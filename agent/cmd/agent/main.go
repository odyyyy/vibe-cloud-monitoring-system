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

	"github.com/vibemonitoring/agent/internal/buffer"
	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/forward"
	"github.com/vibemonitoring/agent/internal/k8s"
	"github.com/vibemonitoring/agent/internal/logs"
	"github.com/vibemonitoring/agent/internal/metrics"
	"github.com/vibemonitoring/agent/internal/receiver"
	"github.com/vibemonitoring/agent/internal/transport"
)

const defaultConfigPath = "config.yaml"

// finalFlushTimeout bounds the shutdown flush so SIGTERM does not hang.
const finalFlushTimeout = 5 * time.Second

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
	queue := buffer.Open(cfg.Buffer, logger)
	sender := forward.New(httpClient, queue, cfg.Retry, logger)
	if cfg.Buffer.Enabled && !queue.Enabled() {
		logger.Print("warning: disk buffer is enabled but unavailable; continuing in memory-only mode")
	}

	logger.Printf("starting: backend=%s interval=%s api_key=%s buffer=%s",
		cfg.BackendURL, cfg.Interval(), config.MaskKey(cfg.APIKey), bufferState(queue))

	var wg sync.WaitGroup

	wg.Add(1)
	go func() {
		defer wg.Done()
		sender.RunReplay(ctx)
	}()

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
	var logPipeline *logs.Pipeline
	if len(cfg.Logs) > 0 || cfg.Kubernetes.Enabled {
		var err error
		logPipeline, err = logs.NewPipeline(cfg, sender, logger)
		if err != nil {
			logger.Fatalf("log pipeline error: %v", err)
		}

		var discoverer *k8s.Discoverer
		if cfg.Kubernetes.Enabled {
			discoverer, err = k8s.New(cfg.Kubernetes, logPipeline, logger)
			if err != nil {
				logger.Fatalf("kubernetes discovery error: %v", err)
			}
		}

		wg.Add(1)
		go func() {
			defer wg.Done()
			logPipeline.Run(ctx)
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

	run(ctx, logger, cfg, collector, sender, customMetrics, logPipeline)

	// SIGTERM/SIGINT: one bounded final attempt to flush anything still in
	// memory. The sender persists failed batches to disk on its own.
	finalCtx, cancel := context.WithTimeout(context.Background(), finalFlushTimeout)
	collectAndSend(finalCtx, logger, collector, sender, customMetrics, logPipeline)
	cancel()

	wg.Wait()
	logger.Print("stopped")
}

// run collects and sends metrics immediately and then on every configured
// interval until the context is cancelled.
func run(ctx context.Context, logger *log.Logger, cfg *config.Config, collector *metrics.Collector, sender *forward.Sender, custom *receiver.Buffer, logPipeline *logs.Pipeline) {
	collectAndSend(ctx, logger, collector, sender, custom, logPipeline)

	ticker := time.NewTicker(cfg.Interval())
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			collectAndSend(ctx, logger, collector, sender, custom, logPipeline)
		}
	}
}

func collectAndSend(ctx context.Context, logger *log.Logger, collector *metrics.Collector, sender *forward.Sender, custom *receiver.Buffer, logPipeline *logs.Pipeline) {
	var points []metrics.Point
	now := time.Now().UTC()

	hostPoints, err := collector.Collect(ctx)
	if err != nil {
		logger.Printf("metric collection failed: %v", err)
	} else {
		points = append(points, hostPoints...)
	}

	if custom != nil {
		points = append(points, custom.Drain()...)
	}

	// Report the throttling counter next to the host metrics. It is cumulative,
	// like the network counters, so the backend can derive rates from it.
	if logPipeline != nil {
		points = append(points, collector.LogsDroppedPoint(logPipeline.DroppedTotal(), now))
	}

	bufferStats := sender.BufferStats()
	points = append(points,
		collector.Point(metrics.MetricBufferSizeBytes, float64(bufferStats.SizeBytes), now),
		collector.Point(metrics.MetricBufferQueuedBatches, float64(bufferStats.QueuedBatches), now),
		collector.Point(metrics.MetricBufferDroppedTotal, float64(bufferStats.DroppedTotal), now),
		collector.Point(metrics.MetricSendRetriesTotal, float64(sender.RetriesTotal()), now),
		collector.Point(metrics.MetricSendFailuresTotal, float64(sender.FailuresTotal()), now),
	)

	sendMetricBatches(ctx, logger, sender, points)
}

type metricBatchSender interface {
	SendMetrics(ctx context.Context, points []metrics.Point) error
}

// sendMetricBatches splits points into backend-sized batches and hands each to
// the shared sender, which handles retries and disk buffering.
func sendMetricBatches(ctx context.Context, logger *log.Logger, sender metricBatchSender, points []metrics.Point) {
	for start := 0; start < len(points); start += maxMetricBatchSize {
		end := start + maxMetricBatchSize
		if end > len(points) {
			end = len(points)
		}
		batch := points[start:end]
		if err := sender.SendMetrics(ctx, batch); err != nil {
			logger.Printf("metric batch send failed: %v", err)
		}
		if ctx.Err() != nil {
			return
		}
	}
}

func bufferState(queue *buffer.Queue) string {
	if !queue.Enabled() {
		return "disabled"
	}
	return fmt.Sprintf("%s (%d queued)", queue.Directory(), queue.Len())
}
