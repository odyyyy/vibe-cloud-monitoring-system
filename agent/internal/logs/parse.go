package logs

import (
	"regexp"
	"strings"
	"time"
)

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
