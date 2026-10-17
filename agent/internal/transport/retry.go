package transport

import (
	"context"
	"log"
	"math/rand"
	"time"
)

const (
	initialBackoff = 1 * time.Second
	maxBackoff     = 60 * time.Second
)

// Retry calls send with exponential backoff until it succeeds or ctx is
// cancelled. It never panics and never exits the process, so a temporarily
// unavailable backend does not crash the agent.
func Retry(ctx context.Context, logger *log.Logger, label string, send func(context.Context) error) {
	backoff := initialBackoff

	for attempt := 1; ; attempt++ {
		err := send(ctx)
		if err == nil {
			logger.Printf("sent %s", label)
			return
		}
		if ctx.Err() != nil {
			logger.Printf("ingest of %s cancelled after %d attempt(s): %v", label, attempt, err)
			return
		}

		wait := jitter(backoff)
		logger.Printf("ingest of %s attempt %d failed: %v; retrying in %s", label, attempt, err, wait)

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
