package logs

import (
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

func TestParseJSONLogExtractsStandardFields(t *testing.T) {
	const raw = `{"time":"2023-10-01T12:00:00Z","level":"error","msg":"failed","user_id":123}`

	parsed, ok := parseJSONLog(raw, nil)
	if !ok {
		t.Fatal("expected the line to parse as JSON")
	}
	if parsed.Level != "ERROR" {
		t.Errorf("level = %q, want ERROR", parsed.Level)
	}
	if parsed.Message != "failed" {
		t.Errorf("message = %q, want failed", parsed.Message)
	}
	if !parsed.HasTimestamp || !parsed.Timestamp.Equal(time.Date(2023, 10, 1, 12, 0, 0, 0, time.UTC)) {
		t.Errorf("timestamp = %s, want 2023-10-01T12:00:00Z", parsed.Timestamp)
	}
	if _, exists := parsed.Fields["user_id"]; !exists {
		t.Error("user_id must be kept in context")
	}
	for _, key := range []string{"time", "level", "msg"} {
		if _, exists := parsed.Fields[key]; exists {
			t.Errorf("standard field %q must not be duplicated in context", key)
		}
	}
}

func TestParseJSONLogUsesMessageAliases(t *testing.T) {
	parsed, ok := parseJSONLog(`{"timestamp":"2023-10-01T12:00:00Z","severity":"warning","message":"disk almost full"}`, nil)
	if !ok {
		t.Fatal("expected the line to parse as JSON")
	}
	if parsed.Level != "WARN" || parsed.Message != "disk almost full" || !parsed.HasTimestamp {
		t.Fatalf("unexpected parse result: %+v", parsed)
	}
}

func TestParseJSONLogRejectsNonObjectsAndDeepNesting(t *testing.T) {
	cases := []string{
		"plain text ERROR",
		`[{"level":"info"}]`,
		`"just a string"`,
		`{"a":{"b":{"c":{"d":1}}}}`,
		`{"a":1} trailing`,
		`null`,
	}
	for _, raw := range cases {
		if _, ok := parseJSONLog(raw, nil); ok {
			t.Errorf("parseJSONLog(%q) unexpectedly succeeded", raw)
		}
	}
}

func TestParseJSONLogAllowsThreeLevels(t *testing.T) {
	parsed, ok := parseJSONLog(`{"a":{"b":{"c":1}}}`, nil)
	if !ok {
		t.Fatal("expected three levels of nesting to be accepted")
	}
	if parsed.Fields["a"] == nil {
		t.Error("nested field was dropped")
	}
}

func TestBuildEventContextKeepsAgentMetadata(t *testing.T) {
	src := config.LogSource{
		Path:        "/var/log/app.log",
		ServiceName: "checkout",
		Kubernetes: &config.KubernetesMetadata{
			Pod:       "api-1",
			Namespace: "payments",
			Container: "api",
		},
	}
	fields := map[string]interface{}{
		"user_id":       json.Number("123"),
		"host":          "evil-host",
		"service":       "evil-service",
		"source":        "evil-source",
		"k8s.pod.name":  "evil-pod",
		"k8s.namespace": "evil-namespace",
		"note":          "kept",
	}

	context := buildEventContext(src, "agent-host", fields)
	var decoded map[string]interface{}
	if err := json.Unmarshal([]byte(context), &decoded); err != nil {
		t.Fatalf("context is not valid JSON: %v (%q)", err, context)
	}

	want := map[string]string{
		"host":               "agent-host",
		"service":            "checkout",
		"source":             "/var/log/app.log",
		"k8s.pod.name":       "api-1",
		"k8s.namespace":      "payments",
		"k8s.container.name": "api",
		"note":               "kept",
	}
	for key, value := range want {
		if got, _ := decoded[key].(string); got != value {
			t.Errorf("context[%q] = %q, want %q", key, got, value)
		}
	}
	if decoded["user_id"] == nil {
		t.Error("user_id missing from context")
	}
}

func TestBuildEventContextRedactsAndLimitsSize(t *testing.T) {
	src := config.LogSource{Path: "/var/log/app.log", ServiceName: "checkout"}
	fields := map[string]interface{}{
		"password": "secret123",
		"nested": map[string]interface{}{
			"token": "hidden-value",
		},
		"big": strings.Repeat("x", 8192),
		"ok":  "visible",
	}

	context := buildEventContext(src, "agent-host", fields)
	if len(context) > maxContextBytes {
		t.Fatalf("context is %d bytes, want at most %d", len(context), maxContextBytes)
	}
	if strings.Contains(context, "secret123") || strings.Contains(context, "hidden-value") {
		t.Fatalf("secret leaked into context: %q", context)
	}
	if !json.Valid([]byte(context)) {
		t.Fatalf("context is not valid JSON: %q", context)
	}
}
