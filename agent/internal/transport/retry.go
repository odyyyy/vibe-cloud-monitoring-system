package transport

import (
	"math/rand"
	"time"
)

// jitterFraction is the maximum additive spread applied to a backoff delay.
const jitterFraction = 0.2

// BackoffDelay returns the exponential delay for the given zero-based attempt:
// initial, 2*initial, 4*initial, ... capped at max. When jitter is enabled a
// random value in [0, 20%] of the delay is added, and the final value is still
// capped at max so a retry never waits longer than configured.
func BackoffDelay(initial, max time.Duration, attempt int, jitter bool) time.Duration {
	if initial <= 0 {
		initial = time.Second
	}
	if max <= 0 {
		max = initial
	}
	if initial > max {
		initial = max
	}
	if attempt < 0 {
		attempt = 0
	}

	delay := initial
	for i := 0; i < attempt && delay < max; i++ {
		if delay > max/2 {
			delay = max
			break
		}
		delay *= 2
	}
	if delay > max {
		delay = max
	}

	if jitter {
		spread := time.Duration(rand.Float64() * jitterFraction * float64(delay))
		delay += spread
		if delay > max {
			delay = max
		}
	}
	return delay
}

// WaitDelay returns the backoff for attempt, but honors a Retry-After value
// from the server when it is longer than the computed delay.
func WaitDelay(initial, max time.Duration, attempt int, jitter bool, retryAfter time.Duration) time.Duration {
	delay := BackoffDelay(initial, max, attempt, jitter)
	if retryAfter > delay {
		return retryAfter
	}
	return delay
}
