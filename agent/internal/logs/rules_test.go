package logs

import (
	"encoding/json"
	"strings"
	"testing"
)

func mustCompileRules(t *testing.T, rules ...PipelineRule) []compiledRule {
	t.Helper()
	compiled, errs := compileRules(rules)
	if len(errs) > 0 {
		t.Fatalf("compileRules returned errors: %v", errs)
	}
	return compiled
}

func TestRuleFilterSupportsAndAndQuotedValues(t *testing.T) {
	compiled := mustCompileRules(t, PipelineRule{
		ID:         1,
		Filter:     `level:error&&message:"disk full" host:server-1`,
		Action:     "DROP",
		OrderIndex: 0,
	})

	document := parseRuleDocument(`{"host":"server-1"}`)
	if !compiled[0].filter.matches("ERROR", "warning: disk full on /var", document) {
		t.Fatal("expected the filter to match")
	}
	if compiled[0].filter.matches("INFO", "disk full", document) {
		t.Fatal("level filter must not match a different level")
	}
	if compiled[0].filter.matches("ERROR", "disk full", parseRuleDocument(`{}`)) {
		t.Fatal("host filter must not match an absent value")
	}
}

func TestApplyPipelineRulesRunsActionsInOrder(t *testing.T) {
	rules := mustCompileRules(t,
		PipelineRule{
			ID:           1,
			Filter:       "level:ERROR",
			Action:       "PARSE_REGEX",
			ActionConfig: json.RawMessage(`{"pattern":"order (?P<order_id>\\d+)"}`),
			OrderIndex:   1,
		},
		PipelineRule{
			ID:           2,
			Filter:       "context.order_id:12345",
			Action:       "ADD_ATTRIBUTE",
			ActionConfig: json.RawMessage(`{"key":"environment","value":"prod"}`),
			OrderIndex:   2,
		},
		PipelineRule{
			ID:           3,
			Filter:       "service:checkout",
			Action:       "MASK",
			ActionConfig: json.RawMessage(`{"pattern":"[0-9]{4}-[0-9]{4}","target":"context","field":"card"}`),
			OrderIndex:   3,
		},
	)

	event, dropped := applyPipelineRules(Event{
		Level:   "ERROR",
		Message: "order 12345 failed",
		Context: `{"service":"checkout","card":"1234-5678"}`,
	}, rules)
	if dropped {
		t.Fatal("event must not be dropped")
	}

	var context map[string]interface{}
	if err := json.Unmarshal([]byte(event.Context), &context); err != nil {
		t.Fatalf("context is invalid JSON: %v", err)
	}
	if context["order_id"] != "12345" {
		t.Errorf("order_id = %v, want 12345", context["order_id"])
	}
	if context["environment"] != "prod" {
		t.Errorf("environment = %v, want prod", context["environment"])
	}
	if context["card"] != "[REDACTED]" {
		t.Errorf("card = %v, want masked value", context["card"])
	}
}

func TestApplyPipelineRulesMasksMessageAndDrops(t *testing.T) {
	maskRules := mustCompileRules(t, PipelineRule{
		ID:           1,
		Filter:       "message:card",
		Action:       "MASK",
		ActionConfig: json.RawMessage(`{"pattern":"[0-9]{4}-[0-9]{3,4}-[0-9]{4}"}`),
	})
	masked, dropped := applyPipelineRules(Event{
		Level:   "INFO",
		Message: "charged card 4111-2222-3333-4444",
	}, maskRules)
	if dropped {
		t.Fatal("mask must not drop the event")
	}
	if strings.Contains(masked.Message, "4111") || !strings.Contains(masked.Message, "[REDACTED]") {
		t.Fatalf("message was not masked: %q", masked.Message)
	}

	dropRules := mustCompileRules(t, PipelineRule{
		ID:         2,
		Filter:     "message:healthcheck",
		Action:     "DROP",
		OrderIndex: 0,
	})
	if _, dropped := applyPipelineRules(Event{Level: "DEBUG", Message: "healthcheck ok"}, dropRules); !dropped {
		t.Fatal("expected the event to be dropped")
	}
}

func TestApplyPipelineRulesKeepsMalformedContext(t *testing.T) {
	rules := mustCompileRules(t, PipelineRule{
		ID:           1,
		Filter:       "level:INFO",
		Action:       "ADD_ATTRIBUTE",
		ActionConfig: json.RawMessage(`{"key":"environment","value":"prod"}`),
	})

	const malformed = `not-json`
	event, _ := applyPipelineRules(Event{
		Level:   "INFO",
		Message: "hello",
		Context: malformed,
	}, rules)
	if event.Context != malformed {
		t.Fatalf("malformed context was overwritten: %q", event.Context)
	}
}

func TestCompileRulesSkipsInvalidRulesIndividually(t *testing.T) {
	compiled, errs := compileRules([]PipelineRule{
		{ID: 1, Filter: "", Action: "DROP", OrderIndex: 0},
		{ID: 2, Filter: "", Action: "PARSE_REGEX", ActionConfig: json.RawMessage(`{"pattern":"(\\d+)"}`), OrderIndex: 1},
	})
	if len(compiled) != 1 {
		t.Fatalf("compiled %d rules, want 1", len(compiled))
	}
	if len(errs) != 1 {
		t.Fatalf("got %d errors, want 1", len(errs))
	}
}

func TestCompileRulesRejectsBlankAttributeValue(t *testing.T) {
	compiled, errs := compileRules([]PipelineRule{{
		ID:           1,
		Action:       "ADD_ATTRIBUTE",
		ActionConfig: json.RawMessage(`{"key":"environment","value":"   "}`),
	}})
	if len(compiled) != 0 || len(errs) != 1 {
		t.Fatalf("compiled %d rules with %d errors, want 0 rules and 1 error", len(compiled), len(errs))
	}
}

func TestApplyPipelineRulesSkipsUnmatchedOptionalCaptures(t *testing.T) {
	rules := mustCompileRules(t, PipelineRule{
		ID:           1,
		Action:       "PARSE_REGEX",
		ActionConfig: json.RawMessage(`{"pattern":"order (?P<order_id>\\d+)(?: user (?P<user_id>\\d+))?"}`),
	})

	event, dropped := applyPipelineRules(Event{Level: "INFO", Message: "order 42"}, rules)
	if dropped {
		t.Fatal("parse must not drop the event")
	}
	var context map[string]interface{}
	if err := json.Unmarshal([]byte(event.Context), &context); err != nil {
		t.Fatalf("decode context: %v", err)
	}
	if context["order_id"] != "42" {
		t.Fatalf("order_id = %v, want 42", context["order_id"])
	}
	if _, exists := context["user_id"]; exists {
		t.Fatal("an unmatched optional capture must not create a context field")
	}
}

// BenchmarkApplyPipelineRules measures the per-event cost of a realistic rule set. Rules are
// compiled once, exactly as the agent does after each refresh; only evaluation is timed.
func BenchmarkApplyPipelineRules(b *testing.B) {
	rules, errs := compileRules([]PipelineRule{
		{ID: 1, Filter: "level:DEBUG", Action: "DROP", OrderIndex: 0},
		{ID: 2, Filter: "level:ERROR", Action: "PARSE_REGEX",
			ActionConfig: json.RawMessage(`{"pattern":"order (?P<order_id>\\d+)"}`), OrderIndex: 1},
		{ID: 3, Filter: "*", Action: "ADD_ATTRIBUTE",
			ActionConfig: json.RawMessage(`{"key":"environment","value":"prod"}`), OrderIndex: 2},
		{ID: 4, Filter: "context.service:checkout", Action: "MASK",
			ActionConfig: json.RawMessage(`{"pattern":"[0-9]{4}-[0-9]{4}","target":"message"}`),
			OrderIndex:   3},
	})
	if len(errs) > 0 {
		b.Fatalf("compileRules returned errors: %v", errs)
	}

	event := Event{
		Level:   "ERROR",
		Message: "order 12345 failed for card 4111-2222",
		Context: `{"service":"checkout","host":"server-1"}`,
	}
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		applyPipelineRules(event, rules)
	}
}

func TestApplyPipelineRulesAppliesParseCapturesAtomically(t *testing.T) {
	rules := mustCompileRules(t, PipelineRule{
		ID:           1,
		Action:       "PARSE_REGEX",
		ActionConfig: json.RawMessage(`{"pattern":"(?P<first>[a-z]+) (?P<second>[a-z]+)"}`),
	})

	original := `{"pad":"` + strings.Repeat("x", 7900) + `"}`
	message := strings.Repeat("a", 150) + " " + strings.Repeat("b", 150)
	event, dropped := applyPipelineRules(Event{
		Level:   "INFO",
		Message: message,
		Context: original,
	}, rules)
	if dropped {
		t.Fatal("parse must not drop the event")
	}
	if event.Context != original {
		t.Fatal("captures must be applied only when the whole update fits")
	}
}
