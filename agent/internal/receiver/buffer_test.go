package receiver

import (
	"io"
	"log"
	"math"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/metrics"
)

func testBuffer() *Buffer {
	buffer := NewBuffer(log.New(io.Discard, "", 0))
	buffer.hostname = "server-1"
	return buffer
}

func TestBufferAddsAgentTags(t *testing.T) {
	buffer := testBuffer()
	now := time.Date(2026, 10, 20, 12, 0, 0, 0, time.UTC)

	accepted, dropped, _ := buffer.AddHTTP([]metrics.Point{{
		Name:      "orders_processed",
		Value:     3,
		Timestamp: now,
		Tags: map[string]string{
			"env":     "prod",
			"host":    "spoofed-host",
			"version": "spoofed-version",
			"source":  "spoofed-source",
		},
	}})
	if accepted != 1 || dropped != 0 {
		t.Fatalf("accepted=%d dropped=%d, want 1/0", accepted, dropped)
	}

	points := buffer.Drain()
	if len(points) != 1 {
		t.Fatalf("expected 1 point, got %d", len(points))
	}

	tags := points[0].Tags
	if tags["host"] != "server-1" {
		t.Errorf("host = %q, want server-1", tags["host"])
	}
	if tags["version"] != metrics.Version {
		t.Errorf("version = %q, want %q", tags["version"], metrics.Version)
	}
	if tags["source"] != sourceCustom {
		t.Errorf("source = %q, want %q", tags["source"], sourceCustom)
	}
	if tags["env"] != "prod" {
		t.Errorf("env = %q, want prod", tags["env"])
	}
}

func TestBufferAggregatesStatsDCounters(t *testing.T) {
	buffer := testBuffer()
	now := time.Now().UTC()

	accepted, dropped, _ := buffer.AddStatsD([]StatsDEvent{
		{Name: "orders_processed", Value: 1, Kind: statsdCounter, Timestamp: now},
		{Name: "orders_processed", Value: 2, Kind: statsdCounter, Timestamp: now},
	})
	if accepted != 2 || dropped != 0 {
		t.Fatalf("accepted=%d dropped=%d, want 2/0", accepted, dropped)
	}

	points := buffer.Drain()
	if len(points) != 1 {
		t.Fatalf("expected aggregated counter point, got %d points", len(points))
	}
	if points[0].Value != 3 {
		t.Errorf("counter value = %v, want 3", points[0].Value)
	}
}

func TestBufferKeepsLastStatsDGaugeValue(t *testing.T) {
	buffer := testBuffer()
	now := time.Now().UTC()

	buffer.AddStatsD([]StatsDEvent{
		{Name: "queue.depth", Value: 10, Kind: statsdGauge, Timestamp: now},
		{Name: "queue.depth", Value: 4, Kind: statsdGauge, Timestamp: now},
	})

	points := buffer.Drain()
	if len(points) != 1 {
		t.Fatalf("expected 1 gauge point, got %d", len(points))
	}
	if points[0].Value != 4 {
		t.Errorf("gauge value = %v, want 4", points[0].Value)
	}
}

func TestBufferForwardsStatsDTimersAsRawValues(t *testing.T) {
	buffer := testBuffer()
	now := time.Now().UTC()

	buffer.AddStatsD([]StatsDEvent{
		{Name: "request.latency", Value: 10, Kind: statsdTimer, Timestamp: now},
		{Name: "request.latency", Value: 20, Kind: statsdTimer, Timestamp: now},
	})

	points := buffer.Drain()
	if len(points) != 2 {
		t.Fatalf("expected 2 raw timer points, got %d", len(points))
	}
	if points[0].Value+points[1].Value != 30 {
		t.Errorf("unexpected timer values: %+v", points)
	}
}

func TestBufferDrainResetsState(t *testing.T) {
	buffer := testBuffer()
	buffer.AddHTTP([]metrics.Point{{Name: "custom.metric", Value: 1, Timestamp: time.Now().UTC()}})

	if points := buffer.Drain(); len(points) != 1 {
		t.Fatalf("first drain returned %d points, want 1", len(points))
	}
	if points := buffer.Drain(); len(points) != 0 {
		t.Fatalf("second drain returned %d points, want 0", len(points))
	}
}

func TestBufferRateLimitDropsExcessPoints(t *testing.T) {
	buffer := testBuffer()
	buffer.limiter = newRateLimiter(1)

	accepted, dropped, rateLimited := buffer.AddHTTP([]metrics.Point{
		{Name: "custom.one", Value: 1, Timestamp: time.Now().UTC()},
		{Name: "custom.two", Value: 2, Timestamp: time.Now().UTC()},
	})

	if accepted != 1 {
		t.Errorf("accepted = %d, want 1", accepted)
	}
	if dropped != 1 || rateLimited != 1 {
		t.Errorf("dropped=%d rateLimited=%d, want 1/1", dropped, rateLimited)
	}
	if points := buffer.Drain(); len(points) != 1 {
		t.Errorf("drained points = %d, want 1", len(points))
	}
}

func TestBufferRejectsInvalidPoints(t *testing.T) {
	buffer := testBuffer()

	accepted, dropped, rateLimited := buffer.AddHTTP([]metrics.Point{
		{Name: "", Value: 1, Timestamp: time.Now().UTC()},
		{Name: strings.Repeat("a", maxMetricNameLength+1), Value: 1, Timestamp: time.Now().UTC()},
		{Name: "bad.value", Value: math.NaN(), Timestamp: time.Now().UTC()},
	})

	if accepted != 0 || dropped != 3 || rateLimited != 0 {
		t.Fatalf("accepted=%d dropped=%d rateLimited=%d, want 0/3/0", accepted, dropped, rateLimited)
	}
	if points := buffer.Drain(); len(points) != 0 {
		t.Errorf("invalid points must not be buffered, got %d", len(points))
	}
}

func TestBufferBoundsTagsAndTruncatesValues(t *testing.T) {
	buffer := testBuffer()
	tags := make(map[string]string, maxMetricTags+10)
	for i := 0; i < maxMetricTags+10; i++ {
		tags["tag"+strconv.Itoa(i)] = "value"
	}
	tags["long"] = strings.Repeat("v", maxTagValueLength+10)

	buffer.AddHTTP([]metrics.Point{{
		Name:      "tagged.metric",
		Value:     1,
		Timestamp: time.Now().UTC(),
		Tags:      tags,
	}})

	points := buffer.Drain()
	if len(points) != 1 {
		t.Fatalf("expected 1 point, got %d", len(points))
	}
	if got := len(points[0].Tags); got > maxMetricTags {
		t.Errorf("tag count = %d, want at most %d", got, maxMetricTags)
	}
	if got := len([]rune(points[0].Tags["long"])); got != maxTagValueLength {
		t.Errorf("long tag rune count = %d, want %d", got, maxTagValueLength)
	}
}

func TestBufferUsesBackendUTF16LengthLimits(t *testing.T) {
	buffer := testBuffer()
	emoji := "\U0001F600"

	accepted, _, _ := buffer.AddHTTP([]metrics.Point{{
		Name:      strings.Repeat(emoji, maxMetricNameLength/2+1),
		Value:     1,
		Timestamp: time.Now().UTC(),
	}})
	if accepted != 0 {
		t.Errorf("accepted = %d, want 0 for a name over the UTF-16 limit", accepted)
	}

	accepted, _, _ = buffer.AddHTTP([]metrics.Point{{
		Name:      "unicode.metric",
		Value:     1,
		Timestamp: time.Now().UTC(),
		Tags:      map[string]string{"label": strings.Repeat(emoji, maxTagValueLength)},
	}})
	if accepted != 1 {
		t.Fatalf("accepted = %d, want 1", accepted)
	}

	points := buffer.Drain()
	if got := utf16Length(points[0].Tags["label"]); got != maxTagValueLength {
		t.Errorf("tag UTF-16 length = %d, want %d", got, maxTagValueLength)
	}
}
