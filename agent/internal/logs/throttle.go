package logs

import (
	"math/rand"
	"sync"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

// throttler enforces a per-source ceiling on forwarded log events and samples the
// excess, so one noisy file cannot overwhelm the backend or the local buffer.
//
// It uses a sliding-window counter: the forwarded rate over the trailing second is
// the current bucket count plus the previous bucket weighted by how much of the
// previous second still falls inside the window. This smooths the burst that a
// plain fixed window would allow at a second boundary.
//
// A nil *throttler is valid and allows every event, which is how disabled
// throttling is represented.
type throttler struct {
	maxPerSecond float64
	sampleRate   float64
	always       map[string]bool

	mu          sync.Mutex
	windowStart time.Time
	current     int
	previous    int

	// now and random are injectable so tests are deterministic. random defaults to
	// rand.Float64, the package-level source, which is safe for concurrent use.
	now    func() time.Time
	random func() float64
}

// newThrottler builds a throttler from cfg, or returns nil when throttling is
// disabled (max_logs_per_second <= 0).
func newThrottler(cfg config.LogThrottlingConfig) *throttler {
	if cfg.MaxLogsPerSecond <= 0 {
		return nil
	}
	always := make(map[string]bool, len(cfg.AlwaysForwardLevels))
	for _, level := range cfg.AlwaysForwardLevels {
		always[canonicalSeverity(level)] = true
	}
	return &throttler{
		maxPerSecond: float64(cfg.MaxLogsPerSecond),
		sampleRate:   cfg.SampleRate,
		always:       always,
		now:          time.Now,
		random:       rand.Float64,
	}
}

// allow reports whether an event with the given severity may be forwarded. Event
// severities that are listed in always_forward_levels bypass the limit; up to
// max_logs_per_second events per second are allowed; every further event is kept
// with probability sample_rate.
func (t *throttler) allow(severity string) bool {
	if t == nil {
		return true
	}
	if t.always[severity] {
		return true
	}

	t.mu.Lock()
	now := t.now()
	if t.windowStart.IsZero() {
		t.windowStart = now
	} else {
		elapsed := now.Sub(t.windowStart)
		switch {
		case elapsed >= 2*time.Second:
			// The window has been idle for a full second or more: forget all history
			// so a long pause cannot leave stale counts behind.
			t.windowStart = now
			t.current = 0
			t.previous = 0
		case elapsed >= time.Second:
			t.windowStart = t.windowStart.Add(time.Second)
			t.previous = t.current
			t.current = 0
		}
	}

	weight := 1 - float64(now.Sub(t.windowStart))/float64(time.Second)
	effective := float64(t.current) + weight*float64(t.previous)
	if effective < t.maxPerSecond {
		t.current++
		t.mu.Unlock()
		return true
	}
	t.mu.Unlock()

	if t.sampleRate <= 0 {
		return false
	}
	return t.random() < t.sampleRate
}
