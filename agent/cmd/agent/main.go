package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/k8s"
	"github.com/vibemonitoring/agent/internal/logs"
	"github.com/vibemonitoring/agent/internal/metrics"
	"github.com/vibemonitoring/agent/internal/receiver"
	"github.com/vibemonitoring/agent/internal/transport"
)

const defaultConfigPath = "config.yaml"

// maxMetricBatchSize matches the backend MetricIngestRequest.MAX_BATCH_SIZE.
// Larger batches are rejected with HTTP 400, so custom metrics are split.
const maxMetricBatchSize = 1000

func main() {
	configPath := flag.String("config", defaultConfigPath, "path to the YAML configuration file")
	flag.Parse()

	logger := log.New(os.Stdout, "agent: ", log.LstdFlags|log.LUTC)

	cfg, err := config.LoadConfig(*configPath)
	if err != nil {
		logger.Fatalf("configuration error: %v", err)
	}

	// Cancel on SIGINT/SIGTERM so in-flight requests and backoff waits stop.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	httpClient := transport.NewClient(cfg.BackendURL, cfg.APIKey)
	collector := metrics.NewCollector(cfg.Metrics.Network)
	metricsClient := metrics.NewClient(httpClient)

	logger.Printf("starting: backend=%s interval=%s api_key=%s",
		cfg.BackendURL, cfg.Interval(), config.MaskKey(cfg.APIKey))

	var wg sync.WaitGroup

	// The custom metric receiver is optional. It binds to loopback only so a
	// local application can push StatsD or JSON metrics to the agent.
	var customMetrics *receiver.Buffer
	if cfg.Receiver.Enabled {
		customMetrics = receiver.NewBuffer(logger)
		receiverServer, err := receiver.New(cfg.Receiver, customMetrics, logger)
		if err != nil {
			logger.Fatalf("custom metric receiver error: %v", err)
		}
		logger.Printf("custom metric receiver enabled: protocol=%s address=%s",
			cfg.Receiver.Protocol, receiverServer.Addr())

		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := receiverServer.Run(ctx); err != nil {
				logger.Printf("custom metric receiver stopped: %v", err)
			}
		}()
	}

	// Pod discovery needs the pipeline even when no static log sources are
	// configured, because sources are added at runtime.
	if len(cfg.Logs) > 0 || cfg.Kubernetes.Enabled {
		logsClient := logs.NewClient(httpClient)
		pipeline, err := logs.NewPipeline(cfg, logsClient, logger)
		if err != nil {
			logger.Fatalf("log pipeline error: %v", err)
		}

		var discoverer *k8s.Discoverer
		if cfg.Kubernetes.Enabled {
			discoverer, err = k8s.New(cfg.Kubernetes, pipeline, logger)
			if err != nil {
				logger.Fatalf("kubernetes discovery error: %v", err)
			}
		}

		wg.Add(1)
		go func() {
			defer wg.Done()
			pipeline.Run(ctx)
		}()

		if discoverer != nil {
			wg.Add(1)
			go func() {
				defer wg.Done()
				if err := discoverer.Run(ctx); err != nil {
					logger.Printf("kubernetes discovery stopped: %v", err)
				}
			}()
		}
	}

	run(ctx, logger, cfg, collector, metricsClient, customMetrics)
	wg.Wait()
	logger.Print("stopped")
}

// run collects and sends metrics immediately and then on every configured
// interval until the context is cancelled.
func run(ctx context.Context, logger *log.Logger, cfg *config.Config, collector *metrics.Collector, client *metrics.Client, custom *receiver.Buffer) {
	collectAndSend(ctx, logger, collector, client, custom)

	ticker := time.NewTicker(cfg.Interval())
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			collectAndSend(ctx, logger, collector, client, custom)
		}
	}
}

func collectAndSend(ctx context.Context, logger *log.Logger, collector *metrics.Collector, client *metrics.Client, custom *receiver.Buffer) {
	var points []metrics.Point

	hostPoints, err := collector.Collect(ctx)
	if err != nil {
		logger.Printf("metric collection failed: %v", err)
	} else {
		points = append(points, hostPoints...)
	}

	if custom != nil {
		points = append(points, custom.Drain()...)
	}

	if len(points) == 0 {
		return
	}
	sendWithBackoff(ctx, logger, client, points)
}

// sendWithBackoff sends the points in backend-sized batches, retrying each
// batch with exponential backoff until it is accepted or the context is
// cancelled. Log batches use the same helper through the log pipeline.
func sendWithBackoff(ctx context.Context, logger *log.Logger, client *metrics.Client, points []metrics.Point) {
	for start := 0; start < len(points); start += maxMetricBatchSize {
		end := start + maxMetricBatchSize
		if end > len(points) {
			end = len(points)
		}
		batch := points[start:end]

		transport.Retry(ctx, logger, fmt.Sprintf("%d metrics", len(batch)), func(sendCtx context.Context) error {
			return client.Send(sendCtx, batch)
		})
		if ctx.Err() != nil {
			return
		}
	}
}
