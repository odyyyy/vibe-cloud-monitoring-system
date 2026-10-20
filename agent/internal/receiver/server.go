package receiver

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/metrics"
)

const (
	// loopbackHost is deliberately fixed: the receiver must never be reachable
	// from outside the machine or container running the agent.
	loopbackHost = "127.0.0.1"

	maxHTTPBodyBytes = 1 << 20
	maxHTTPMetrics   = 1000
	maxStatsDPacket  = 65535

	httpShutdownTimeout = 5 * time.Second
)

// Server is a loopback-only HTTP or StatsD listener for custom metrics.
type Server struct {
	protocol string
	buffer   *Buffer
	logger   *log.Logger

	listener   net.Listener
	httpServer *http.Server
	udpConn    *net.UDPConn
}

// New binds the receiver socket to 127.0.0.1 on the configured port. Binding
// happens eagerly so a port conflict is a clear startup error.
func New(cfg config.ReceiverConfig, buffer *Buffer, logger *log.Logger) (*Server, error) {
	if buffer == nil {
		return nil, errors.New("receiver buffer is required")
	}

	protocol := strings.ToLower(strings.TrimSpace(cfg.Protocol))
	address := net.JoinHostPort(loopbackHost, strconv.Itoa(cfg.Port))

	server := &Server{
		protocol: protocol,
		buffer:   buffer,
		logger:   logger,
	}

	switch protocol {
	case config.ReceiverProtocolHTTP:
		listener, err := net.Listen("tcp4", address)
		if err != nil {
			return nil, fmt.Errorf("listen http %s: %w", address, err)
		}
		mux := http.NewServeMux()
		mux.HandleFunc("/metrics", server.handleMetrics)
		server.listener = listener
		server.httpServer = &http.Server{
			Handler:           mux,
			ReadHeaderTimeout: 5 * time.Second,
			ReadTimeout:       10 * time.Second,
			WriteTimeout:      10 * time.Second,
			IdleTimeout:       60 * time.Second,
			MaxHeaderBytes:    16 << 10,
		}
	case config.ReceiverProtocolStatsD:
		conn, err := net.ListenUDP("udp4", &net.UDPAddr{
			IP:   net.ParseIP(loopbackHost),
			Port: cfg.Port,
		})
		if err != nil {
			return nil, fmt.Errorf("listen statsd %s: %w", address, err)
		}
		server.udpConn = conn
	default:
		return nil, fmt.Errorf("unsupported receiver protocol %q", cfg.Protocol)
	}

	return server, nil
}

// Addr returns the bound loopback address, for startup logging.
func (s *Server) Addr() string {
	switch {
	case s.listener != nil:
		return s.listener.Addr().String()
	case s.udpConn != nil:
		return s.udpConn.LocalAddr().String()
	default:
		return ""
	}
}

// Run serves the receiver until the context is cancelled.
func (s *Server) Run(ctx context.Context) error {
	switch {
	case s.httpServer != nil:
		return s.runHTTP(ctx)
	case s.udpConn != nil:
		return s.runStatsD(ctx)
	default:
		return errors.New("receiver has no listener")
	}
}

func (s *Server) runHTTP(ctx context.Context) error {
	done := make(chan struct{})
	stop := make(chan struct{})
	go func() {
		defer close(done)
		select {
		case <-ctx.Done():
			shutdownCtx, cancel := context.WithTimeout(context.Background(), httpShutdownTimeout)
			defer cancel()
			_ = s.httpServer.Shutdown(shutdownCtx)
		case <-stop:
		}
	}()

	err := s.httpServer.Serve(s.listener)
	close(stop)
	<-done

	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return err
}

func (s *Server) runStatsD(ctx context.Context) error {
	done := make(chan struct{})
	stop := make(chan struct{})
	go func() {
		defer close(done)
		select {
		case <-ctx.Done():
			_ = s.udpConn.Close()
		case <-stop:
		}
	}()
	defer func() {
		close(stop)
		<-done
	}()

	buf := make([]byte, maxStatsDPacket)
	for {
		n, _, err := s.udpConn.ReadFromUDP(buf)
		if err != nil {
			if ctx.Err() != nil || errors.Is(err, net.ErrClosed) {
				return nil
			}
			return err
		}

		events := ParseStatsDPacket(buf[:n], time.Now().UTC())
		if len(events) > 0 {
			s.buffer.AddStatsD(events)
		}
	}
}

type metricIngestRequest struct {
	Metrics []metrics.Point `json:"metrics"`
}

// handleMetrics accepts the same JSON shape as the cloud ingestion API:
// {"metrics":[{"name":"...","value":1,"timestamp":"...","tags":{...}}]}.
func (s *Server) handleMetrics(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxHTTPBodyBytes)
	decoder := json.NewDecoder(r.Body)
	var request metricIngestRequest
	if err := decoder.Decode(&request); err != nil {
		var maxBytesError *http.MaxBytesError
		if errors.As(err, &maxBytesError) {
			http.Error(w, "payload too large", http.StatusRequestEntityTooLarge)
			return
		}
		http.Error(w, "invalid JSON payload", http.StatusBadRequest)
		return
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		http.Error(w, "invalid JSON payload", http.StatusBadRequest)
		return
	}
	if len(request.Metrics) == 0 {
		http.Error(w, "metrics must not be empty", http.StatusBadRequest)
		return
	}
	if len(request.Metrics) > maxHTTPMetrics {
		http.Error(w, "at most 1000 metrics are allowed per request", http.StatusBadRequest)
		return
	}

	accepted, dropped, rateLimited := s.buffer.AddHTTP(request.Metrics)
	w.Header().Set("X-Metrics-Accepted", strconv.Itoa(accepted))
	w.Header().Set("X-Metrics-Dropped", strconv.Itoa(dropped))

	switch {
	case accepted == 0 && rateLimited > 0:
		w.Header().Set("Retry-After", "1")
		http.Error(w, "metric rate limit exceeded", http.StatusTooManyRequests)
	case accepted == 0 && dropped > 0:
		http.Error(w, "no valid metrics were accepted", http.StatusBadRequest)
	default:
		w.WriteHeader(http.StatusAccepted)
	}
}
