package receiver

import (
	"context"
	"io"
	"log"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/metrics"
)

func TestHTTPHandlerAcceptsBatch(t *testing.T) {
	buffer := testBuffer()
	server := &Server{buffer: buffer}

	body := `{"metrics":[{"name":"orders_processed","value":3,"timestamp":"2026-10-20T10:00:00Z","tags":{"env":"prod"}}]}`
	request := httptest.NewRequest(http.MethodPost, "/metrics", strings.NewReader(body))
	recorder := httptest.NewRecorder()

	server.handleMetrics(recorder, request)

	if recorder.Code != http.StatusAccepted {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusAccepted)
	}
	points := buffer.Drain()
	if len(points) != 1 {
		t.Fatalf("expected 1 point, got %d", len(points))
	}
	if points[0].Name != "orders_processed" || points[0].Value != 3 {
		t.Errorf("unexpected point: %+v", points[0])
	}
	if points[0].Tags["source"] != sourceCustom {
		t.Errorf("source = %q, want %q", points[0].Tags["source"], sourceCustom)
	}
}

func TestHTTPHandlerRejectsMalformedRequests(t *testing.T) {
	cases := []struct {
		name   string
		method string
		body   string
		status int
	}{
		{"invalid json", http.MethodPost, "{", http.StatusBadRequest},
		{"empty batch", http.MethodPost, `{"metrics":[]}`, http.StatusBadRequest},
		{"missing batch", http.MethodPost, `{}`, http.StatusBadRequest},
		{"wrong method", http.MethodGet, ``, http.StatusMethodNotAllowed},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			server := &Server{buffer: testBuffer()}
			request := httptest.NewRequest(tc.method, "/metrics", strings.NewReader(tc.body))
			recorder := httptest.NewRecorder()

			server.handleMetrics(recorder, request)

			if recorder.Code != tc.status {
				t.Errorf("status = %d, want %d", recorder.Code, tc.status)
			}
		})
	}
}

func TestHTTPHandlerRejectsOversizedBatch(t *testing.T) {
	server := &Server{buffer: testBuffer()}

	var body strings.Builder
	body.WriteString(`{"metrics":[`)
	for i := 0; i < maxHTTPMetrics+1; i++ {
		if i > 0 {
			body.WriteByte(',')
		}
		body.WriteString(`{"name":"custom.metric","value":1}`)
	}
	body.WriteString(`]}`)

	request := httptest.NewRequest(http.MethodPost, "/metrics", strings.NewReader(body.String()))
	recorder := httptest.NewRecorder()
	server.handleMetrics(recorder, request)

	if recorder.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
}

func TestHTTPHandlerReturnsTooManyRequestsWhenRateLimited(t *testing.T) {
	buffer := testBuffer()
	buffer.limiter = newRateLimiter(1)
	server := &Server{buffer: buffer}

	first := `{"metrics":[{"name":"one","value":1},{"name":"two","value":2}]}`
	recorder := httptest.NewRecorder()
	server.handleMetrics(recorder, httptest.NewRequest(http.MethodPost, "/metrics", strings.NewReader(first)))
	if recorder.Code != http.StatusAccepted {
		t.Fatalf("first status = %d, want %d", recorder.Code, http.StatusAccepted)
	}

	second := `{"metrics":[{"name":"three","value":3}]}`
	recorder = httptest.NewRecorder()
	server.handleMetrics(recorder, httptest.NewRequest(http.MethodPost, "/metrics", strings.NewReader(second)))
	if recorder.Code != http.StatusTooManyRequests {
		t.Errorf("second status = %d, want %d", recorder.Code, http.StatusTooManyRequests)
	}
}

func TestHTTPHandlerRejectsOnlyInvalidMetrics(t *testing.T) {
	server := &Server{buffer: testBuffer()}
	body := `{"metrics":[{"name":"","value":1}]}`

	recorder := httptest.NewRecorder()
	server.handleMetrics(recorder, httptest.NewRequest(http.MethodPost, "/metrics", strings.NewReader(body)))

	if recorder.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
}

func TestServerBindsLoopbackOnly(t *testing.T) {
	protocols := []string{config.ReceiverProtocolHTTP, config.ReceiverProtocolStatsD}

	for _, protocol := range protocols {
		t.Run(protocol, func(t *testing.T) {
			server, err := New(config.ReceiverConfig{Protocol: protocol}, testBuffer(), log.New(io.Discard, "", 0))
			if err != nil {
				t.Fatalf("New: %v", err)
			}
			defer func() {
				if server.listener != nil {
					_ = server.listener.Close()
				}
				if server.udpConn != nil {
					_ = server.udpConn.Close()
				}
			}()

			address := server.Addr()
			if !strings.HasPrefix(address, loopbackHost+":") {
				t.Errorf("bound address = %q, want loopback", address)
			}
		})
	}
}

func TestStatsDServerReceivesUDP(t *testing.T) {
	buffer := testBuffer()
	server, err := New(config.ReceiverConfig{Protocol: config.ReceiverProtocolStatsD}, buffer, log.New(io.Discard, "", 0))
	if err != nil {
		t.Fatalf("New: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() {
		done <- server.Run(ctx)
	}()

	address, ok := server.udpConn.LocalAddr().(*net.UDPAddr)
	if !ok {
		cancel()
		t.Fatalf("unexpected listener address %T", server.udpConn.LocalAddr())
	}
	conn, err := net.DialUDP("udp4", nil, address)
	if err != nil {
		cancel()
		t.Fatalf("DialUDP: %v", err)
	}
	defer conn.Close()

	if _, err := conn.Write([]byte("orders_processed:2|c|#env:prod")); err != nil {
		cancel()
		t.Fatalf("write datagram: %v", err)
	}

	deadline := time.Now().Add(2 * time.Second)
	var points []metrics.Point
	for len(points) == 0 {
		if time.Now().After(deadline) {
			cancel()
			t.Fatal("timed out waiting for a StatsD metric")
		}
		points = buffer.Drain()
		if len(points) == 0 {
			time.Sleep(10 * time.Millisecond)
		}
	}
	if points[0].Name != "orders_processed" || points[0].Value != 2 {
		t.Errorf("unexpected point: %+v", points[0])
	}

	cancel()
	select {
	case err := <-done:
		if err != nil {
			t.Errorf("Run: %v", err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("server did not stop after cancellation")
	}
}
