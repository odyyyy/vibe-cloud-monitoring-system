package receiver

import (
	"testing"
	"time"
)

func TestParseStatsDLine(t *testing.T) {
	now := time.Date(2026, 10, 20, 12, 0, 0, 0, time.UTC)

	cases := []struct {
		name     string
		line     string
		kind     statsdKind
		value    float64
		wantTags map[string]string
	}{
		{
			name:  "counter",
			line:  "orders_processed:1|c",
			kind:  statsdCounter,
			value: 1,
		},
		{
			name:  "counter with sample rate",
			line:  "orders_processed:5|c|@0.5",
			kind:  statsdCounter,
			value: 10,
		},
		{
			name:  "gauge",
			line:  "queue.depth:42|g",
			kind:  statsdGauge,
			value: 42,
		},
		{
			name:  "timer",
			line:  "request.latency:12.5|ms",
			kind:  statsdTimer,
			value: 12.5,
		},
		{
			name:  "histogram",
			line:  "payload.size:9|h",
			kind:  statsdTimer,
			value: 9,
		},
		{
			name:     "tags",
			line:     "login_failures:1|c|#env:prod,region:eu",
			kind:     statsdCounter,
			value:    1,
			wantTags: map[string]string{"env": "prod", "region": "eu"},
		},
		{
			name:     "bare tags",
			line:     "login_failures:1|c|#env,region",
			kind:     statsdCounter,
			value:    1,
			wantTags: map[string]string{"env": "", "region": ""},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			event, ok := parseStatsDLine(tc.line, now)
			if !ok {
				t.Fatalf("parseStatsDLine(%q) failed", tc.line)
			}
			if event.Name == "" {
				t.Error("expected a metric name")
			}
			if event.Kind != tc.kind {
				t.Errorf("kind = %v, want %v", event.Kind, tc.kind)
			}
			if event.Value != tc.value {
				t.Errorf("value = %v, want %v", event.Value, tc.value)
			}
			if !event.Timestamp.Equal(now) {
				t.Errorf("timestamp = %v, want %v", event.Timestamp, now)
			}
			for key, want := range tc.wantTags {
				if event.Tags[key] != want {
					t.Errorf("tag %q = %q, want %q", key, event.Tags[key], want)
				}
			}
		})
	}
}

func TestParseStatsDLineRejectsMalformedInput(t *testing.T) {
	now := time.Now().UTC()
	lines := []string{
		"",
		"no_colon",
		":1|c",
		"metric:",
		"metric:not-a-number|c",
		"metric:1|unknown",
		"metric:1|c|@0",
		"metric:1|c|@2",
		"metric:NaN|g",
		"metric:+Inf|ms",
	}

	for _, line := range lines {
		if event, ok := parseStatsDLine(line, now); ok {
			t.Errorf("parseStatsDLine(%q) = %+v, want rejection", line, event)
		}
	}
}

func TestParseStatsDPacketHandlesMultipleLines(t *testing.T) {
	now := time.Now().UTC()
	packet := []byte("first:1|c\n\nsecond:2|g\nbroken\nthird:3|ms\n")

	events := ParseStatsDPacket(packet, now)

	if len(events) != 3 {
		t.Fatalf("expected 3 events, got %d: %+v", len(events), events)
	}
	if events[0].Name != "first" || events[1].Name != "second" || events[2].Name != "third" {
		t.Errorf("unexpected event order: %+v", events)
	}
}
