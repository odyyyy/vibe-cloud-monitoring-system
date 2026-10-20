// Package receiver implements the loopback-only endpoint that accepts custom
// metrics from applications running next to the agent. Received points are
// validated, rate limited, buffered and later forwarded with the host metrics.
package receiver

import (
	"log"
	"math"
	"os"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/vibemonitoring/agent/internal/metrics"
)

const (
	// maxMetricsPerSecond protects the agent from a misbehaving local
	// application. Excess points are dropped instead of growing memory.
	maxMetricsPerSecond = 10000
	// maxBufferedPoints bounds memory while the backend is unavailable or slow.
	maxBufferedPoints = 50000

	maxMetricNameLength = 200
	maxMetricTags       = 50
	maxTagKeyLength     = 100
	maxTagValueLength   = 500

	sourceTag       = "source"
	sourceCustom    = "custom"
	hostTag         = "host"
	versionTag      = "version"
	unknownHostname = "unknown"
)

// rateLimiter is a token bucket. It is intentionally simple: one bucket covers
// all incoming points regardless of protocol.
type rateLimiter struct {
	mu     sync.Mutex
	rate   float64
	burst  float64
	tokens float64
	last   time.Time
	now    func() time.Time
}

func newRateLimiter(perSecond int) *rateLimiter {
	now := time.Now
	burst := float64(perSecond)
	return &rateLimiter{
		rate:   float64(perSecond),
		burst:  burst,
		tokens: burst,
		last:   now(),
		now:    now,
	}
}

// allow returns how many of the requested points fit into the current budget.
func (l *rateLimiter) allow(n int) int {
	if n <= 0 {
		return 0
	}

	l.mu.Lock()
	defer l.mu.Unlock()

	now := l.now()
	if now.After(l.last) {
		l.tokens = math.Min(l.burst, l.tokens+now.Sub(l.last).Seconds()*l.rate)
		l.last = now
	}

	if l.tokens >= float64(n) {
		l.tokens -= float64(n)
		return n
	}

	allowed := int(l.tokens)
	l.tokens -= float64(allowed)
	return allowed
}

type aggregateKey struct {
	kind statsdKind
	name string
	tags string
}

type aggregateEntry struct {
	point metrics.Point
}

// Buffer stores custom metric points until the next host collection cycle.
// All access is guarded by mu because HTTP handlers, the UDP reader and the
// forwarding goroutine run concurrently.
type Buffer struct {
	logger   *log.Logger
	hostname string
	version  string
	limiter  *rateLimiter
	max      int

	mu         sync.Mutex
	raw        []metrics.Point
	aggregates map[aggregateKey]*aggregateEntry
	order      []aggregateKey
	dropped    int
}

// NewBuffer builds an empty custom metric buffer tagged with the local hostname
// and agent version.
func NewBuffer(logger *log.Logger) *Buffer {
	hostname, err := os.Hostname()
	if err != nil || hostname == "" {
		hostname = unknownHostname
	}
	return &Buffer{
		logger:     logger,
		hostname:   hostname,
		version:    metrics.Version,
		limiter:    newRateLimiter(maxMetricsPerSecond),
		max:        maxBufferedPoints,
		aggregates: make(map[aggregateKey]*aggregateEntry),
	}
}

// AddHTTP validates and buffers points received over the HTTP receiver. It
// returns the accepted, dropped and rate-limited counts so the handler can
// answer with an appropriate status code.
func (b *Buffer) AddHTTP(points []metrics.Point) (accepted, dropped, rateLimited int) {
	b.mu.Lock()
	defer b.mu.Unlock()

	allowed := b.limiter.allow(len(points))
	rateLimited = len(points) - allowed
	dropped = rateLimited

	defaults := b.defaultTags()
	for _, point := range points[:allowed] {
		normalized, ok := normalizePoint(point, defaults)
		if !ok {
			dropped++
			continue
		}
		if b.appendRawLocked(normalized) {
			accepted++
		} else {
			dropped++
		}
	}

	b.dropped += dropped
	return accepted, dropped, rateLimited
}

// AddStatsD validates and buffers parsed StatsD events. Counters are summed and
// gauges keep their last value within a collection cycle; timers and
// histograms are forwarded as raw values.
func (b *Buffer) AddStatsD(events []StatsDEvent) (accepted, dropped, rateLimited int) {
	b.mu.Lock()
	defer b.mu.Unlock()

	allowed := b.limiter.allow(len(events))
	rateLimited = len(events) - allowed
	dropped = rateLimited

	defaults := b.defaultTags()
	for _, event := range events[:allowed] {
		point, ok := normalizePoint(metrics.Point{
			Name:      event.Name,
			Value:     event.Value,
			Timestamp: event.Timestamp,
			Tags:      event.Tags,
		}, defaults)
		if !ok {
			dropped++
			continue
		}

		switch event.Kind {
		case statsdCounter, statsdGauge:
			if b.addAggregateLocked(event.Kind, point) {
				accepted++
			} else {
				dropped++
			}
		default:
			if b.appendRawLocked(point) {
				accepted++
			} else {
				dropped++
			}
		}
	}

	b.dropped += dropped
	return accepted, dropped, rateLimited
}

// Drain returns all buffered points and resets the buffer. The caller owns the
// returned slice. Dropped-point diagnostics are logged once per drain instead
// of once per rejected packet.
func (b *Buffer) Drain() []metrics.Point {
	b.mu.Lock()
	points := make([]metrics.Point, 0, len(b.raw)+len(b.order))
	for _, key := range b.order {
		if entry, ok := b.aggregates[key]; ok {
			points = append(points, entry.point)
		}
	}
	points = append(points, b.raw...)
	b.raw = nil
	b.aggregates = make(map[aggregateKey]*aggregateEntry)
	b.order = nil
	dropped := b.dropped
	b.dropped = 0
	b.mu.Unlock()

	if dropped > 0 && b.logger != nil {
		b.logger.Printf("dropped %d custom metrics: receiver rate or buffer limit reached", dropped)
	}
	return points
}

func (b *Buffer) appendRawLocked(point metrics.Point) bool {
	if b.max > 0 && len(b.raw) >= b.max {
		if len(b.raw) == 0 {
			return false
		}
		// Keep the most recent data: evict the oldest point.
		b.raw = b.raw[1:]
		b.dropped++
	}
	b.raw = append(b.raw, point)
	return true
}

func (b *Buffer) addAggregateLocked(kind statsdKind, point metrics.Point) bool {
	key := aggregateKey{kind: kind, name: point.Name, tags: tagSignature(point.Tags)}
	if entry, ok := b.aggregates[key]; ok {
		if kind == statsdCounter {
			sum := entry.point.Value + point.Value
			if math.IsNaN(sum) || math.IsInf(sum, 0) {
				return false
			}
			entry.point.Value = sum
		} else {
			entry.point.Value = point.Value
		}
		entry.point.Timestamp = point.Timestamp
		return true
	}
	if b.max > 0 && len(b.aggregates) >= b.max {
		return false
	}
	b.aggregates[key] = &aggregateEntry{point: point}
	b.order = append(b.order, key)
	return true
}

func (b *Buffer) defaultTags() map[string]string {
	return map[string]string{
		hostTag:    b.hostname,
		versionTag: b.version,
		sourceTag:  sourceCustom,
	}
}

// normalizePoint validates an untrusted point and returns a copy with the
// agent-owned tags applied. Invalid points are rejected rather than forwarded,
// because one bad point would make the backend reject the whole batch.
func normalizePoint(point metrics.Point, defaults map[string]string) (metrics.Point, bool) {
	name := strings.TrimSpace(strings.ToValidUTF8(point.Name, "\uFFFD"))
	if name == "" || utf16Length(name) > maxMetricNameLength {
		return metrics.Point{}, false
	}
	if math.IsNaN(point.Value) || math.IsInf(point.Value, 0) {
		return metrics.Point{}, false
	}

	timestamp := point.Timestamp
	if timestamp.IsZero() {
		timestamp = time.Now().UTC()
	} else {
		timestamp = timestamp.UTC()
	}

	keys := make([]string, 0, len(point.Tags))
	for key := range point.Tags {
		keys = append(keys, key)
	}
	sort.Strings(keys)

	tags := make(map[string]string, min(len(keys), maxMetricTags))
	userTagLimit := maxMetricTags - len(defaults)
	for _, rawKey := range keys {
		key := strings.TrimSpace(strings.ToValidUTF8(rawKey, "\uFFFD"))
		value := strings.ToValidUTF8(point.Tags[rawKey], "\uFFFD")
		if key == "" || utf16Length(key) > maxTagKeyLength {
			continue
		}
		if _, reserved := defaults[key]; reserved {
			continue
		}
		if len(tags) >= userTagLimit {
			break
		}
		tags[key] = truncateUTF16(value, maxTagValueLength)
	}
	for key, value := range defaults {
		tags[key] = value
	}

	return metrics.Point{
		Name:      name,
		Value:     point.Value,
		Timestamp: timestamp,
		Tags:      tags,
	}, true
}

func tagSignature(tags map[string]string) string {
	keys := make([]string, 0, len(tags))
	for key := range tags {
		keys = append(keys, key)
	}
	sort.Strings(keys)

	var builder strings.Builder
	for _, key := range keys {
		builder.WriteString(key)
		builder.WriteByte(0)
		builder.WriteString(tags[key])
		builder.WriteByte(0)
	}
	return builder.String()
}

// utf16Length matches the backend's Java String length limit: characters
// outside the basic multilingual plane occupy two UTF-16 code units.
func utf16Length(value string) int {
	length := 0
	for _, r := range value {
		if r > 0xFFFF {
			length += 2
		} else {
			length++
		}
	}
	return length
}

// truncateUTF16 limits a value to the backend's UTF-16 column width without
// splitting a surrogate pair.
func truncateUTF16(value string, limit int) string {
	if len(value) <= limit {
		return value
	}
	units := 0
	for index, r := range value {
		width := 1
		if r > 0xFFFF {
			width = 2
		}
		if units+width > limit {
			return value[:index]
		}
		units += width
	}
	return value
}
