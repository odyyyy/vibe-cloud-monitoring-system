// Package logs tails configured log files, parses plain-text lines into events,
// redacts secrets, batches them and forwards them to the cloud ingestion API.
package logs

import (
	"encoding/json"
	"sort"
	"strings"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

const (
	// maxMessageRunes matches the backend LogEventPayload.MAX_MESSAGE_LENGTH so a
	// single line can never be rejected for being too long.
	maxMessageRunes = 8192
	// maxContextBytes bounds the serialized context JSON. The backend accepts up to
	// 8192 characters; keeping the agent below 4 KiB leaves headroom for escaping
	// and prevents a single structured line from bloating every event.
	maxContextBytes = 4096
	// maxReservedContextRunes bounds agent-provided metadata so the reserved keys
	// can always be included even when the surrounding context is large.
	maxReservedContextRunes = 256
)

// Event mirrors the backend LogEventPayload DTO. Timestamp is an event time in UTC
// serialized as RFC 3339, which Spring maps onto java.time.Instant.
type Event struct {
	Level     string    `json:"level"`
	Message   string    `json:"message"`
	Timestamp time.Time `json:"timestamp"`
	Context   string    `json:"context,omitempty"`

	// severity is the canonical severity token used for throttle decisions. It is
	// unexported so it is never serialized to the backend.
	severity string
}

// isReservedContextKey reports whether a context key is owned by the agent. Log
// content can never set these keys, which prevents a malicious line from spoofing
// the host, service, source or Kubernetes metadata.
func isReservedContextKey(key string) bool {
	switch key {
	case "service", "source", "host", "k8s.pod.name", "k8s.namespace", "k8s.container.name":
		return true
	default:
		return false
	}
}

func buildEventContext(src config.LogSource, host string, fields map[string]interface{}) string {
	reserved := map[string]interface{}{
		"service": truncateRunes(redact(src.ServiceName), maxReservedContextRunes),
		"source":  truncateRunes(redact(src.Path), maxReservedContextRunes),
		"host":    truncateRunes(redact(host), maxReservedContextRunes),
	}
	if src.Kubernetes != nil {
		reserved["k8s.pod.name"] = truncateRunes(redact(src.Kubernetes.Pod), maxReservedContextRunes)
		reserved["k8s.namespace"] = truncateRunes(redact(src.Kubernetes.Namespace), maxReservedContextRunes)
		reserved["k8s.container.name"] = truncateRunes(redact(src.Kubernetes.Container), maxReservedContextRunes)
	}

	user := make(map[string]interface{}, len(fields))
	for key, value := range fields {
		redactedKey := redact(key)
		if isReservedContextKey(redactedKey) {
			continue
		}
		user[redactedKey] = redactContextEntry(redactedKey, value)
	}

	return marshalContext(reserved, user)
}

// isSensitiveContextKey mirrors the well-known secret names handled by the
// line-based redaction rules. Structured JSON separates the key from its value,
// so the key/value form cannot be matched by those regexes alone.
func isSensitiveContextKey(key string) bool {
	normalized := strings.ToLower(strings.TrimSpace(key))
	normalized = strings.ReplaceAll(normalized, "-", "_")
	switch normalized {
	case "password", "passwd", "pwd", "pass",
		"secret", "client_secret", "secret_key",
		"token", "access_token", "refresh_token", "auth_token", "id_token",
		"api_key", "apikey",
		"access_key", "accesskey", "aws_access_key_id",
		"authorization":
		return true
	default:
		return false
	}
}

func redactContextEntry(key string, value interface{}) interface{} {
	if isSensitiveContextKey(key) {
		return "[REDACTED]"
	}
	return redactContextValue(value)
}

// redactContextValue applies the Task 7 redaction rules to strings inside parsed
// JSON, including nested objects and arrays, before they are serialized.
func redactContextValue(value interface{}) interface{} {
	switch v := value.(type) {
	case string:
		return redact(v)
	case map[string]interface{}:
		out := make(map[string]interface{}, len(v))
		for key, nested := range v {
			redactedKey := redact(key)
			out[redactedKey] = redactContextEntry(redactedKey, nested)
		}
		return out
	case []interface{}:
		out := make([]interface{}, len(v))
		for i, nested := range v {
			out[i] = redactContextValue(nested)
		}
		return out
	default:
		return value
	}
}

// marshalContext serializes reserved metadata and as many user fields as fit into
// maxContextBytes. Reserved keys always win over log content and are included
// first; additional keys are added in deterministic (sorted) order until the
// limit is reached.
func marshalContext(reserved, user map[string]interface{}) string {
	context := make(map[string]interface{}, len(reserved)+len(user))
	size := 2 // opening and closing braces

	add := func(key string, value interface{}, force bool) bool {
		encodedKey, err := json.Marshal(key)
		if err != nil {
			return false
		}
		encodedValue, err := json.Marshal(value)
		if err != nil {
			return false
		}
		entrySize := len(encodedKey) + 1 + len(encodedValue)
		if len(context) > 0 {
			entrySize++
		}
		if !force && size+entrySize > maxContextBytes {
			return false
		}
		context[key] = value
		size += entrySize
		return true
	}

	reservedKeys := make([]string, 0, len(reserved))
	for key := range reserved {
		reservedKeys = append(reservedKeys, key)
	}
	sort.Strings(reservedKeys)
	addReservedContext(reserved, reservedKeys, add)

	userKeys := make([]string, 0, len(user))
	for key := range user {
		userKeys = append(userKeys, key)
	}
	sort.Strings(userKeys)
	for _, key := range userKeys {
		add(key, user[key], false)
	}

	encoded, err := json.Marshal(context)
	if err != nil {
		return ""
	}
	return redact(string(encoded))
}

// addReservedContext adds agent-owned keys, dropping the least important metadata
// only if the reserved values alone would exceed the context limit. service and
// host are always retained; their values are already bounded above.
func addReservedContext(reserved map[string]interface{}, keys []string, add func(string, interface{}, bool) bool) {
	if contextSize(reserved) <= maxContextBytes {
		for _, key := range keys {
			add(key, reserved[key], true)
		}
		return
	}

	optional := []string{"source", "k8s.container.name", "k8s.namespace", "k8s.pod.name"}
	trimmed := make(map[string]interface{}, len(reserved))
	for key, value := range reserved {
		trimmed[key] = value
	}
	for _, key := range optional {
		delete(trimmed, key)
		if contextSize(trimmed) <= maxContextBytes {
			break
		}
	}

	trimmedKeys := make([]string, 0, len(trimmed))
	for key := range trimmed {
		trimmedKeys = append(trimmedKeys, key)
	}
	sort.Strings(trimmedKeys)
	for _, key := range trimmedKeys {
		add(key, trimmed[key], true)
	}
}

func contextSize(context map[string]interface{}) int {
	encoded, err := json.Marshal(context)
	if err != nil {
		return maxContextBytes + 1
	}
	return len(encoded)
}

// truncateRunes caps a string by rune count (the backend bounds by characters).
func truncateRunes(s string, max int) string {
	if max <= 0 {
		return ""
	}
	r := []rune(s)
	if len(r) <= max {
		return s
	}
	return string(r[:max])
}
