package logs

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/vibemonitoring/agent/internal/transport"
)

func TestFetchRulesUsesTheApiKeyAndDecodesTheAgentContract(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Errorf("method = %s, want GET", r.Method)
		}
		if r.URL.Path != "/api/v1/ingest/log-pipelines" {
			t.Errorf("path = %s, want /api/v1/ingest/log-pipelines", r.URL.Path)
		}
		if key := r.Header.Get("X-API-Key"); key != "app_test_key" {
			t.Errorf("X-API-Key = %q, want app_test_key", key)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"rules":[
			{"id":11,"name":"Drop debug","filter":"level:DEBUG","action":"DROP",
			 "actionConfig":{},"orderIndex":0},
			{"id":12,"name":"Extract order","filter":"level:ERROR","action":"PARSE_REGEX",
			 "actionConfig":{"pattern":"order (?P<order_id>\\d+)"},"orderIndex":1}
		]}`)
	}))
	defer server.Close()

	client := NewClient(transport.NewClient(server.URL, "app_test_key"))
	rules, err := client.FetchRules(context.Background())
	if err != nil {
		t.Fatalf("FetchRules returned an error: %v", err)
	}
	if len(rules) != 2 {
		t.Fatalf("got %d rules, want 2", len(rules))
	}
	if rules[0].ID != 11 || rules[0].Action != "DROP" || rules[0].OrderIndex != 0 {
		t.Errorf("first rule = %+v", rules[0])
	}
	if rules[1].ID != 12 || rules[1].Action != "PARSE_REGEX" {
		t.Errorf("second rule = %+v", rules[1])
	}
	if string(rules[1].ActionConfig) != `{"pattern":"order (?P<order_id>\\d+)"}` {
		t.Errorf("actionConfig = %s", rules[1].ActionConfig)
	}
}

func TestFetchRulesReturnsAnErrorForANonSuccessStatus(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "denied", http.StatusUnauthorized)
	}))
	defer server.Close()

	client := NewClient(transport.NewClient(server.URL, "app_test_key"))
	if _, err := client.FetchRules(context.Background()); err == nil {
		t.Fatal("FetchRules must return an error for a 401 response")
	}
}

func TestFetchRulesRejectsMoreRulesThanTheBackendLimit(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"rules":[`)
		for i := 0; i < 51; i++ {
			if i > 0 {
				_, _ = io.WriteString(w, ",")
			}
			_, _ = io.WriteString(w, `{"id":1,"name":"r","filter":"*","action":"DROP","orderIndex":0}`)
		}
		_, _ = io.WriteString(w, `]}`)
	}))
	defer server.Close()

	client := NewClient(transport.NewClient(server.URL, "app_test_key"))
	if _, err := client.FetchRules(context.Background()); err == nil {
		t.Fatal("FetchRules must reject a response above the rule limit")
	}
}
