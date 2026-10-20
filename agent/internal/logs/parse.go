package logs

import (
	"encoding/json"
	"io"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const (
	// maxJSONDepth bounds nested JSON objects and arrays. Logs are untrusted and
	// deeply nested payloads would otherwise allocate unbounded structures.
	maxJSONDepth = 3
)

// Standard JSON log fields, in priority order. Common aliases are accepted so
// agents work with typical application and platform log schemas.
var (
	jsonTimestampKeys = []string{"timestamp", "time", "ts", "@timestamp"}
	jsonLevelKeys     = []string{"level", "severity", "lvl"}
	jsonMessageKeys   = []string{"message", "msg"}
	jsonStandardKeys  = []string{
		"timestamp", "time", "ts", "@timestamp",
		"level", "severity", "lvl",
		"message", "msg",
	}
)

// jsonLog is the result of parsing one JSON log line. Fields holds the
// non-standard keys that are forwarded as context.
type jsonLog struct {
	Fields       map[string]interface{}
	Level        string
	HasLevel     bool
	Message      string
	HasMessage   bool
	Timestamp    time.Time
	HasTimestamp bool
}

// defaultLevelPattern matches common severity tokens as standalone words. It is
// used when a source does not define its own level_pattern.
var defaultLevelPattern = regexp.MustCompile(`(?i)\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL|ERR)\b`)

// redactionRules are applied in order to remove values that look like secrets
// before a log line leaves the customer's infrastructure. They are intentionally
// conservative and only cover well-known shapes.
var redactionRules = []struct {
	re   *regexp.Regexp
	repl string
}{
	// Authorization: Bearer <token> must run before the generic key/value rule so the
	// token is removed, not just the "Bearer" keyword.
	{regexp.MustCompile(`(?i)\bbearer\s+[A-Za-z0-9._\-]+`), `Bearer [REDACTED]`},
	// key=value / key: value forms for common secret names.
	{regexp.MustCompile(`(?i)\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|authorization)\b(\s*[:=]\s*)(\S+)`), `${1}${2}[REDACTED]`},
	// Application API keys issued by this platform (app_...).
	{regexp.MustCompile(`\bapp_[A-Za-z0-9\-]{6,}`), `app_[REDACTED]`},
	// AWS access key id.
	{regexp.MustCompile(`\bAKIA[0-9A-Z]{16}\b`), `[REDACTED]`},
	// PEM private key headers.
	{regexp.MustCompile(`-----BEGIN [A-Z ]*PRIVATE KEY-----`), `[REDACTED PRIVATE KEY]`},
}

// redact replaces values that resemble secrets with a fixed placeholder.
func redact(line string) string {
	for _, rule := range redactionRules {
		line = rule.re.ReplaceAllString(line, rule.repl)
	}
	return line
}

// inferLevel returns the closest backend LogLevel for the given line. A custom
// pattern (when provided) takes precedence; otherwise the default severity
// pattern is used. Unknown or absent severities default to INFO.
func inferLevel(custom *regexp.Regexp, line string) string {
	if custom != nil {
		if level, ok := matchLevel(custom, line); ok {
			return level
		}
	}
	if level, ok := matchLevel(defaultLevelPattern, line); ok {
		return level
	}
	return "INFO"
}

func matchLevel(re *regexp.Regexp, line string) (string, bool) {
	m := re.FindStringSubmatch(line)
	if m == nil {
		return "", false
	}
	token := m[0]
	// Prefer the first non-empty capturing group so patterns can anchor on context,
	// e.g. `level=(\w+)`.
	for i := 1; i < len(m); i++ {
		if m[i] != "" {
			token = m[i]
			break
		}
	}
	return normalizeLevel(token), true
}

func normalizeLevel(token string) string {
	switch strings.ToUpper(strings.TrimSpace(token)) {
	case "TRACE", "DEBUG":
		return "DEBUG"
	case "WARN", "WARNING":
		return "WARN"
	case "ERR", "ERROR", "FATAL", "CRITICAL":
		return "ERROR"
	default:
		return "INFO"
	}
}

// timestampFormats are tried in order; the first captured candidate that parses
// wins. Layouts without a zone are interpreted as UTC by time.Parse, matching the
// backend's UTC storage.
var timestampFormats = []struct {
	re     *regexp.Regexp
	layout string
}{
	{regexp.MustCompile(`^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2}))`), time.RFC3339Nano},
	{regexp.MustCompile(`^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?)`), "2006-01-02T15:04:05"},
	{regexp.MustCompile(`^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d+)?)`), "2006-01-02 15:04:05"},
	{regexp.MustCompile(`^(\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})`), "2006/01/02 15:04:05"},
	{regexp.MustCompile(`^(\d{2}/[A-Za-z]{3}/\d{4}:\d{2}:\d{2}:\d{2} [+-]\d{4})`), "02/Jan/2006:15:04:05 -0700"},
}

// extractTimestamp returns the event time parsed from the start of the line. A
// timestamp is only recognized at the beginning to avoid false positives inside
// arbitrary message text.
func extractTimestamp(line string) (time.Time, bool) {
	head := line
	if len(head) > 64 {
		head = head[:64]
	}
	for _, f := range timestampFormats {
		m := f.re.FindStringSubmatch(head)
		if m == nil {
			continue
		}
		if t, err := time.Parse(f.layout, m[1]); err == nil {
			return t.UTC(), true
		}
	}
	return time.Time{}, false
}

// parseJSONLog parses a single line as a JSON object. It returns false for
// non-object JSON (arrays, scalars, null), malformed JSON and objects nested
// deeper than maxJSONDepth; callers then fall back to plain-text parsing.
func parseJSONLog(raw string, levelRe *regexp.Regexp) (jsonLog, bool) {
	fields, ok := decodeJSONObject(raw)
	if !ok {
		return jsonLog{}, false
	}

	parsed := jsonLog{Fields: fields}

	if v, ok := firstJSONValue(fields, jsonLevelKeys); ok {
		if level := strings.TrimSpace(scalarToString(v)); level != "" {
			parsed.Level = normalizeLevel(level)
			parsed.HasLevel = true
		}
	}
	if v, ok := firstJSONValue(fields, jsonMessageKeys); ok {
		parsed.Message = scalarToString(v)
		parsed.HasMessage = true
	}
	if v, ok := firstJSONValue(fields, jsonTimestampKeys); ok {
		if ts, ok := parseJSONTimestamp(v); ok {
			parsed.Timestamp = ts.UTC()
			parsed.HasTimestamp = true
		}
	}

	// Standard fields are represented by dedicated Event fields, never duplicated
	// in context. Removing every alias also prevents a second alias from smuggling
	// an untrusted value into the context map.
	for _, key := range jsonStandardKeys {
		delete(parsed.Fields, key)
	}

	if parsed.Level == "" {
		parsed.Level = inferLevel(levelRe, raw)
	}
	return parsed, true
}

func decodeJSONObject(raw string) (map[string]interface{}, bool) {
	if !withinJSONDepth(raw, maxJSONDepth) {
		return nil, false
	}

	decoder := json.NewDecoder(strings.NewReader(raw))
	decoder.UseNumber()
	var fields map[string]interface{}
	if err := decoder.Decode(&fields); err != nil || fields == nil {
		return nil, false
	}
	// Reject trailing data after the first value so "{"a":1} garbage" is treated
	// as plain text rather than silently dropping the suffix.
	var extra interface{}
	if err := decoder.Decode(&extra); err != io.EOF {
		return nil, false
	}
	return fields, true
}

// withinJSONDepth tokenizes the payload without building nested maps, which
// keeps a hostile deeply nested line from allocating a large object graph.
func withinJSONDepth(raw string, maxDepth int) bool {
	decoder := json.NewDecoder(strings.NewReader(raw))
	decoder.UseNumber()

	depth := 0
	for {
		token, err := decoder.Token()
		if err == io.EOF {
			return true
		}
		if err != nil {
			return false
		}
		switch token {
		case json.Delim('{'), json.Delim('['):
			depth++
			if depth > maxDepth {
				return false
			}
		case json.Delim('}'), json.Delim(']'):
			depth--
			if depth < 0 {
				return false
			}
		}
	}
}

func firstJSONValue(fields map[string]interface{}, keys []string) (interface{}, bool) {
	for _, key := range keys {
		if value, ok := fields[key]; ok {
			return value, true
		}
	}
	return nil, false
}

// scalarToString renders a JSON value as the text the backend stores. Objects and
// arrays are serialized compactly; they are bounded by maxJSONDepth and the
// line size.
func scalarToString(value interface{}) string {
	switch v := value.(type) {
	case nil:
		return ""
	case string:
		return v
	case json.Number:
		return v.String()
	default:
		encoded, err := json.Marshal(v)
		if err != nil {
			return ""
		}
		return string(encoded)
	}
}

func parseJSONTimestamp(value interface{}) (time.Time, bool) {
	switch v := value.(type) {
	case string:
		s := strings.TrimSpace(v)
		if s == "" {
			return time.Time{}, false
		}
		for _, layout := range []string{time.RFC3339Nano, "2006-01-02T15:04:05", "2006-01-02 15:04:05"} {
			if ts, err := time.Parse(layout, s); err == nil {
				return ts.UTC(), true
			}
		}
		if epoch, err := strconv.ParseInt(s, 10, 64); err == nil {
			return epochTime(epoch), true
		}
	case json.Number:
		if epoch, err := strconv.ParseInt(v.String(), 10, 64); err == nil {
			return epochTime(epoch), true
		}
		if seconds, err := v.Float64(); err == nil {
			return epochFloatTime(seconds), true
		}
	case float64:
		return epochFloatTime(v), true
	}
	return time.Time{}, false
}

// epochTime interprets an integer timestamp by magnitude: seconds, milliseconds,
// microseconds or nanoseconds. This covers the common JSON log encoders without
// requiring per-source configuration.
func epochTime(value int64) time.Time {
	abs := value
	if abs < 0 {
		abs = -abs
	}
	switch {
	case abs >= 1e17:
		return time.Unix(0, value)
	case abs >= 1e14:
		return time.Unix(value/1e6, (value%1e6)*1e3)
	case abs >= 1e11:
		return time.Unix(value/1e3, (value%1e3)*1e6)
	default:
		return time.Unix(value, 0)
	}
}

func epochFloatTime(value float64) time.Time {
	abs := value
	if abs < 0 {
		abs = -abs
	}
	switch {
	case abs >= 1e17:
		return time.Unix(0, int64(value))
	case abs >= 1e14:
		micros := int64(value)
		return time.Unix(micros/1e6, (micros%1e6)*1e3)
	case abs >= 1e11:
		millis := int64(value)
		return time.Unix(millis/1e3, (millis%1e3)*1e6)
	default:
		seconds := int64(value)
		return time.Unix(seconds, int64((value-float64(seconds))*1e9))
	}
}
