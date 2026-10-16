package main

import (
	"context"
	"flag"
	"log"
	"math/rand"
	"os"
	"os/signal"
	"syscall"
	"time"
)

const defaultConfigPath = "config.yaml"

func main() {
	configPath := flag.String("config", defaultConfigPath, "path to the YAML configuration file")
	flag.Parse()

	logger := log.New(os.Stdout, "agent: ", log.LstdFlags|log.LUTC)

	cfg, err := LoadConfig(*configPath)
	if err != nil {
		logger.Fatalf("configuration error: %v", err)
	}

	// Cancel on SIGINT/SIGTERM so in-flight requests and backoff waits stop.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	collector := NewMetricsCollector()
	client := NewHTTPMetricsClient(cfg)

	logger.Printf("starting: backend=%s interval=%s api_key=%s",
		cfg.BackendURL, cfg.interval(), maskKey(cfg.APIKey))

	run(ctx, logger, cfg, collector, client)
	logger.Print("stopped")
}

// run collects and sends metrics immediately and then on every configured
// interval until the context is cancelled.
func run(ctx context.Context, logger *log.Logger, cfg *Config, collector *MetricsCollector, client *HTTPMetricsClient) {
	collectAndSend(ctx, logger, collector, client)

	ticker := time.NewTicker(cfg.interval())
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

func collectAndSend(ctx context.Context, logger *log.Logger, collector *MetricsCollector, client *HTTPMetricsClient) {
	metrics, err := collector.Collect(ctx)
	if err != nil {
		logger.Printf("metric collection failed: %v", err)
		return
	}
	sendWithBackoff(ctx, logger, client, metrics)
}

const (
	initialBackoff = 1 * time.Second
	maxBackoff     = 60 * time.Second
)

// sendWithBackoff retries the same batch with exponential backoff until it is
// accepted or the context is cancelled. It never panics and never exits the
// process, so a temporarily unavailable backend does not crash the agent.
func sendWithBackoff(ctx context.Context, logger *log.Logger, client *HTTPMetricsClient, metrics []MetricPoint) {
	backoff := initialBackoff

	for attempt := 1; ; attempt++ {
		err := client.Send(ctx, metrics)
		if err == nil {
			logger.Printf("sent %d metrics", len(metrics))
			return
		}
		if ctx.Err() != nil {
			logger.Printf("ingest cancelled after %d attempt(s): %v", attempt, err)
			return
		}

		wait := jitter(backoff)
		logger.Printf("ingest attempt %d failed: %v; retrying in %s", attempt, err, wait)

		select {
		case <-ctx.Done():
			return
		case <-time.After(wait):
		}

		if backoff < maxBackoff {
			backoff *= 2
			if backoff > maxBackoff {
				backoff = maxBackoff
			}
		}
	}
}

// jitter adds up to 20% random spread to avoid synchronized retries across
// multiple agents.
func jitter(d time.Duration) time.Duration {
	spread := float64(d) * 0.2
	return d + time.Duration(rand.Float64()*spread)
}
