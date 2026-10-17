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
	"github.com/vibemonitoring/agent/internal/logs"
	"github.com/vibemonitoring/agent/internal/metrics"
	"github.com/vibemonitoring/agent/internal/transport"
)

const defaultConfigPath = "config.yaml"

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
	collector := metrics.NewCollector()
	metricsClient := metrics.NewClient(httpClient)

	logger.Printf("starting: backend=%s interval=%s api_key=%s",
		cfg.BackendURL, cfg.Interval(), config.MaskKey(cfg.APIKey))

	var wg sync.WaitGroup
	if len(cfg.Logs) > 0 {
		logsClient := logs.NewClient(httpClient)
		pipeline, err := logs.NewPipeline(cfg, logsClient, logger)
		if err != nil {
			logger.Fatalf("log pipeline error: %v", err)
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			pipeline.Run(ctx)
		}()
	}

	run(ctx, logger, cfg, collector, metricsClient)
	wg.Wait()
	logger.Print("stopped")
}

// run collects and sends metrics immediately and then on every configured
// interval until the context is cancelled.
func run(ctx context.Context, logger *log.Logger, cfg *config.Config, collector *metrics.Collector, client *metrics.Client) {
	collectAndSend(ctx, logger, collector, client)

	ticker := time.NewTicker(cfg.Interval())
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			collectAndSend(ctx, logger, collector, client)
		}
	}
}

func collectAndSend(ctx context.Context, logger *log.Logger, collector *metrics.Collector, client *metrics.Client) {
	points, err := collector.Collect(ctx)
	if err != nil {
		logger.Printf("metric collection failed: %v", err)
		return
	}
	sendWithBackoff(ctx, logger, client, points)
}

// sendWithBackoff retries the metric batch with exponential backoff until it is
// accepted or the context is cancelled. Log batches use the same helper through
// the log pipeline.
func sendWithBackoff(ctx context.Context, logger *log.Logger, client *metrics.Client, points []metrics.Point) {
	transport.Retry(ctx, logger, fmt.Sprintf("%d metrics", len(points)), func(sendCtx context.Context) error {
		return client.Send(sendCtx, points)
	})
}
