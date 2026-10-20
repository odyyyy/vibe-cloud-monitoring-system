package receiver

import (
	"math"
	"strconv"
	"strings"
	"time"
)

type statsdKind int

const (
	statsdCounter statsdKind = iota
	statsdGauge
	statsdTimer
)

// StatsDEvent is one parsed StatsD line. Counter values already include the
// sample-rate adjustment.
type StatsDEvent struct {
	Name      string
	Value     float64
	Kind      statsdKind
	Timestamp time.Time
	Tags      map[string]string
}

// ParseStatsDPacket parses a UDP datagram that may contain multiple newline
// separated StatsD lines. Malformed lines are skipped; parsing never fails the
// whole packet.
func ParseStatsDPacket(packet []byte, now time.Time) []StatsDEvent {
	events := make([]StatsDEvent, 0, 1)
	for _, line := range strings.Split(string(packet), "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if event, ok := parseStatsDLine(line, now); ok {
			events = append(events, event)
		}
	}
	return events
}

// parseStatsDLine parses the standard format:
//
//	metric.name:value|type|@sample_rate|#tag1,tag2
func parseStatsDLine(line string, now time.Time) (StatsDEvent, bool) {
	colon := strings.IndexByte(line, ':')
	if colon <= 0 || colon == len(line)-1 {
		return StatsDEvent{}, false
	}

	name := strings.TrimSpace(line[:colon])
	if name == "" {
		return StatsDEvent{}, false
	}

	fields := strings.Split(line[colon+1:], "|")
	if len(fields) < 2 {
		return StatsDEvent{}, false
	}

	value, err := strconv.ParseFloat(strings.TrimSpace(fields[0]), 64)
	if err != nil || math.IsNaN(value) || math.IsInf(value, 0) {
		return StatsDEvent{}, false
	}

	kind, ok := parseStatsDKind(fields[1])
	if !ok {
		return StatsDEvent{}, false
	}

	sampleRate := 1.0
	tags := make(map[string]string)
	for _, field := range fields[2:] {
		field = strings.TrimSpace(field)
		switch {
		case strings.HasPrefix(field, "@"):
			rate, err := strconv.ParseFloat(strings.TrimPrefix(field, "@"), 64)
			if err != nil || rate <= 0 || rate > 1 {
				return StatsDEvent{}, false
			}
			sampleRate = rate
		case strings.HasPrefix(field, "#"):
			parseStatsDTags(strings.TrimPrefix(field, "#"), tags)
		}
	}

	if kind == statsdCounter {
		value /= sampleRate
	}

	return StatsDEvent{
		Name:      name,
		Value:     value,
		Kind:      kind,
		Timestamp: now,
		Tags:      tags,
	}, true
}

func parseStatsDKind(raw string) (statsdKind, bool) {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "c":
		return statsdCounter, true
	case "g":
		return statsdGauge, true
	case "ms", "h", "timer", "histogram":
		return statsdTimer, true
	default:
		return 0, false
	}
}

func parseStatsDTags(raw string, tags map[string]string) {
	for _, rawTag := range strings.Split(raw, ",") {
		tag := strings.TrimSpace(rawTag)
		if tag == "" {
			continue
		}

		key, value := tag, ""
		if separator := strings.IndexByte(tag, ':'); separator >= 0 {
			key = strings.TrimSpace(tag[:separator])
			value = strings.TrimSpace(tag[separator+1:])
		}
		if key == "" {
			continue
		}
		tags[key] = value
	}
}
