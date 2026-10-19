// Package logs tails configured log files, parses plain-text lines into events,
// redacts secrets, batches them and forwards them to the cloud ingestion API.
package logs

import (
	"encoding/json"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

const (
	// maxMessageRunes matches the backend LogEventPayload.MAX_MESSAGE_LENGTH so a
	// single line can never be rejected for being too long.
	maxMessageRunes = 8192
	// maxContextRunes matches the backend LogEventPayload.MAX_CONTEXT_LENGTH.
	maxContextRunes = 8192
)

// Event mirrors the backend LogEventPayload DTO. Timestamp is an event time in UTC
// serialized as RFC 3339, which Spring maps onto java.time.Instant.
type Event struct {
	Level     string    `json:"level"`
	Message   string    `json:"message"`
	Timestamp time.Time `json:"timestamp"`
	Context   string    `json:"context,omitempty"`
}

// eventContext is serialized into Event.Context. The backend stores context
// verbatim and does not interpret it, so service/source/host and Kubernetes
// metadata travel here.
type eventContext struct {
	Service   string `json:"service"`
	Source    string `json:"source"`
	Host      string `json:"host"`
	PodName   string `json:"k8s.pod.name,omitempty"`
	Namespace string `json:"k8s.namespace,omitempty"`
	Container string `json:"k8s.container.name,omitempty"`
}

func buildEventContext(src config.LogSource, host string) string {
	ec := eventContext{Service: src.ServiceName, Source: src.Path, Host: host}
	if src.Kubernetes != nil {
		ec.PodName = src.Kubernetes.Pod
		ec.Namespace = src.Kubernetes.Namespace
		ec.Container = src.Kubernetes.Container
	}
	b, err := json.Marshal(ec)
	if err != nil {
		return ""
	}
	return truncateRunes(string(b), maxContextRunes)
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
