package logs

import (
	"sync"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

// fakeClock is a controllable clock for deterministic window tests.
type fakeClock struct {
	now time.Time
}

func (c *fakeClock) Now() time.Time { return c.now }

func testThrottler(t *testing.T, max int, sampleRate float64, always ...string) (*throttler, *fakeClock) {
	t.Helper()
	th := newThrottler(config.LogThrottlingConfig{
		MaxLogsPerSecond:    max,
		SampleRate:          sampleRate,
		AlwaysForwardLevels: always,
	})
	if th == nil {
		t.Fatalf("expected a throttler for max=%d", max)
	}
	clock := &fakeClock{now: time.Unix(1_700_000_000, 0)}
	th.now = clock.Now
	return th, clock
}

func TestNewThrottlerDisabledAtZero(t *testing.T) {
	if got := newThrottler(config.LogThrottlingConfig{MaxLogsPerSecond: 0}); got != nil {
		t.Fatalf("expected nil throttler for max=0, got %+v", got)
	}
}

func TestNilThrottlerAllowsEverything(t *testing.T) {
	var th *throttler
	for i := 0; i < 1000; i++ {
		if !th.allow("INFO") {
			t.Fatal("nil throttler must allow every event")
		}
	}
}

func TestThrottlerAllowsEventsUnderLimit(t *testing.T) {
	th, _ := testThrottler(t, 3, 0)
	for i := 0; i < 3; i++ {
		if !th.allow("INFO") {
			t.Fatalf("event %d should be allowed under the limit", i)
		}
	}
}

func TestThrottlerDropsExcessWhenSampleRateIsZero(t *testing.T) {
	th, _ := testThrottler(t, 2, 0)
	for i := 0; i < 2; i++ {
		if !th.allow("INFO") {
			t.Fatalf("event %d should be allowed under the limit", i)
		}
	}
	for i := 0; i < 10; i++ {
		if th.allow("INFO") {
			t.Fatalf("excess event %d must be dropped when sample_rate is 0", i)
		}
	}
}

func TestThrottlerSamplesExcess(t *testing.T) {
	th, _ := testThrottler(t, 1, 0.5)
	if !th.allow("INFO") {
		t.Fatal("first event should be allowed")
	}

	th.random = func() float64 { return 0.49 }
	if !th.allow("INFO") {
		t.Fatal("an excess event below sample_rate must be kept")
	}
	th.random = func() float64 { return 0.5 }
	if th.allow("INFO") {
		t.Fatal("an excess event at sample_rate must be dropped")
	}
}

func TestThrottlerAlwaysForwardsCriticalLevels(t *testing.T) {
	th, _ := testThrottler(t, 1, 0, "FATAL", "CRITICAL")
	if !th.allow("INFO") {
		t.Fatal("first event should be allowed")
	}

	if !th.allow("FATAL") {
		t.Error("FATAL must bypass the limit")
	}
	if !th.allow("CRITICAL") {
		t.Error("CRITICAL must bypass the limit")
	}
	if th.allow("ERROR") {
		t.Error("ERROR is not in always_forward_levels and must be throttled")
	}
}

func TestThrottlerResetsAfterIdleWindow(t *testing.T) {
	th, clock := testThrottler(t, 1, 0)
	if !th.allow("INFO") {
		t.Fatal("first event should be allowed")
	}
	if th.allow("INFO") {
		t.Fatal("second event in the same window should be dropped")
	}

	clock.now = clock.now.Add(2 * time.Second)
	if !th.allow("INFO") {
		t.Fatal("a new window after an idle second should allow events again")
	}
}

func TestThrottlerSlidingWindowCarriesPreviousBucket(t *testing.T) {
	th, clock := testThrottler(t, 2, 0)
	// Fill the first window.
	if !th.allow("INFO") || !th.allow("INFO") {
		t.Fatal("both events in the first window should be allowed")
	}

	// Move just past the boundary: half of the previous bucket still counts, so
	// only one additional event fits in the trailing second.
	clock.now = clock.now.Add(1500 * time.Millisecond)
	if !th.allow("INFO") {
		t.Fatal("one event should fit within the sliding window")
	}
	if th.allow("INFO") {
		t.Fatal("the sliding window must account for the previous bucket")
	}
}

func TestThrottlerConcurrentSamplingIsSafe(t *testing.T) {
	th, _ := testThrottler(t, 50, 0.5)

	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 1000; j++ {
				th.allow("INFO")
			}
		}()
	}
	wg.Wait()
}
