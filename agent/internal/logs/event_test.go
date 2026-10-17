package logs

import (
	"regexp"
	"strings"
	"testing"
	"time"
)

func TestRedactMasksSecrets(t *testing.T) {
	cases := []struct {
		name           string
		in             string
		mustNotContain string
	}{
		{"password", "user login password=secret123 ok", "secret123"},
		{"api_key", "auth with api_key=app_ABCDEF123456", "app_ABCDEF123456"},
		{"bearer", "Authorization: Bearer eyJhbGciOiJIUzI1", "eyJhbGciOiJIUzI1"},
		{"aws", "key AKIAIOSFODNN7EXAMPLE found", "AKIAIOSFODNN7EXAMPLE"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := redact(tc.in)
			if strings.Contains(got, tc.mustNotContain) {
				t.Fatalf("redact(%q) = %q still contains secret", tc.in, got)
			}
			if !strings.Contains(got, "[REDACTED]") {
				t.Fatalf("redact(%q) = %q has no redaction marker", tc.in, got)
			}
		})
	}
}

func TestRedactKeepsHarmlessText(t *testing.T) {
	const line = "2024-05-06 07:08:09 INFO user logged in"
	if got := redact(line); got != line {
		t.Fatalf("redact changed harmless line: %q", got)
	}
}

func TestInferLevelDefault(t *testing.T) {
	cases := []struct {
		line string
		want string
	}{
		{"2024-05-06 07:08:09 INFO started", "INFO"},
		{"2024-05-06 07:08:09 ERROR boom", "ERROR"},
		{"2024-05-06 07:08:09 WARN slow", "WARN"},
		{"2024-05-06 07:08:09 WARNING slow", "WARN"},
		{"2024-05-06 07:08:09 DEBUG trace", "DEBUG"},
		{"plain message without a level token", "INFO"},
	}
	for _, tc := range cases {
		if got := inferLevel(nil, tc.line); got != tc.want {
			t.Errorf("inferLevel(%q) = %q, want %q", tc.line, got, tc.want)
		}
	}
}

func TestInferLevelCustomPattern(t *testing.T) {
	re := regexp.MustCompile(`severity=(\w+)`)
	if got := inferLevel(re, "severity=warn disk almost full"); got != "WARN" {
		t.Errorf("custom pattern = %q, want WARN", got)
	}
	// A custom pattern that does not match falls back to the default detection.
	if got := inferLevel(re, "2024-05-06 07:08:09 ERROR boom"); got != "ERROR" {
		t.Errorf("fallback = %q, want ERROR", got)
	}
	// Neither custom nor default match -> INFO.
	if got := inferLevel(re, "nothing to see"); got != "INFO" {
		t.Errorf("no match = %q, want INFO", got)
	}
}

func TestExtractTimestamp(t *testing.T) {
	ts, ok := extractTimestamp("2024-05-06T07:08:09Z hello")
	if !ok {
		t.Fatal("expected RFC3339 timestamp to parse")
	}
	if !ts.Equal(time.Date(2024, 5, 6, 7, 8, 9, 0, time.UTC)) {
		t.Fatalf("unexpected timestamp: %s", ts)
	}

	withFraction, ok := extractTimestamp("2024-05-06T07:08:09.500Z hello")
	if !ok || withFraction.Nanosecond() != 500*int(time.Millisecond) {
		t.Fatalf("fractional timestamp not parsed: %s (ok=%v)", withFraction, ok)
	}

	space, ok := extractTimestamp("2024-05-06 07:08:09 INFO hello")
	if !ok || !space.Equal(time.Date(2024, 5, 6, 7, 8, 9, 0, time.UTC)) {
		t.Fatalf("space timestamp not parsed: %s (ok=%v)", space, ok)
	}
}

func TestExtractTimestampMissing(t *testing.T) {
	if _, ok := extractTimestamp("just a message with 12:00 in it"); ok {
		t.Fatal("expected no timestamp")
	}
}

func TestTruncateRunes(t *testing.T) {
	if got := truncateRunes("héllo", 3); got != "hél" {
		t.Fatalf("truncateRunes = %q, want %q", got, "hél")
	}
	if got := truncateRunes("abc", 10); got != "abc" {
		t.Fatalf("truncateRunes = %q, want abc", got)
	}
}
