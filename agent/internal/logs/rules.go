package logs

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

const (
	ruleRefreshInterval = 5 * time.Minute
	maxPatternLength    = 512
	maxContextKeyLength = 64
	maxAttributeValue   = 256
	maxRuleContextBytes = 8192
	maxCapturedRunes    = 256
)

var (
	contextKeyPattern = regexp.MustCompile(`^[A-Za-z0-9_.-]{1,64}$`)
	reservedRuleKeys  = map[string]bool{
		"service": true, "source": true, "host": true,
		"k8s.pod.name": true, "k8s.namespace": true, "k8s.container.name": true,
	}
)

// PipelineRule mirrors the backend LogPipelineRuleResponse DTO. ActionConfig is
// kept raw so one malformed rule cannot prevent the rest of the set from loading.
type PipelineRule struct {
	ID           int64           `json:"id"`
	Name         string          `json:"name"`
	Filter       string          `json:"filter"`
	Action       string          `json:"action"`
	ActionConfig json.RawMessage `json:"actionConfig"`
	OrderIndex   int             `json:"orderIndex"`
}

type pipelineRulesResponse struct {
	Rules []PipelineRule `json:"rules"`
}

// RuleFetcher downloads the tenant's rules for the application behind the API key.
type RuleFetcher interface {
	FetchRules(ctx context.Context) ([]PipelineRule, error)
}

// FetchRules downloads log pipeline rules for the authenticated application.
func (c *Client) FetchRules(ctx context.Context) ([]PipelineRule, error) {
	var response pipelineRulesResponse
	if err := c.http.GetJSON(ctx, "/api/v1/ingest/log-pipelines", &response); err != nil {
		return nil, err
	}
	if len(response.Rules) > 50 {
		return nil, fmt.Errorf("backend returned too many pipeline rules")
	}
	return response.Rules, nil
}

type ruleFilter struct {
	clauses []ruleClause
}

type ruleClause struct {
	kind  string
	key   string
	value string
}

type actionConfig struct {
	Pattern string `json:"pattern"`
	Target  string `json:"target"`
	Field   string `json:"field"`
	Key     string `json:"key"`
	Value   string `json:"value"`
}

type compiledRule struct {
	id         int64
	name       string
	action     string
	filter     ruleFilter
	re         *regexp.Regexp
	groupNames []string
	key        string
	value      string
	maskTarget string
	maskField  string
}

type ruleDocument struct {
	original string
	values   map[string]interface{}
	valid    bool
	modified bool
}

// compileRules validates and compiles the downloaded rule set. Invalid rules are
// skipped individually so one bad configuration does not disable processing.
func compileRules(rules []PipelineRule) ([]compiledRule, []error) {
	ordered := append([]PipelineRule(nil), rules...)
	sort.SliceStable(ordered, func(i, j int) bool {
		if ordered[i].OrderIndex == ordered[j].OrderIndex {
			return ordered[i].ID < ordered[j].ID
		}
		return ordered[i].OrderIndex < ordered[j].OrderIndex
	})

	compiled := make([]compiledRule, 0, len(ordered))
	var errs []error
	for _, rule := range ordered {
		result, err := compileRule(rule)
		if err != nil {
			errs = append(errs, fmt.Errorf("rule id=%d: %w", rule.ID, err))
			continue
		}
		compiled = append(compiled, result)
	}
	return compiled, errs
}

func compileRule(rule PipelineRule) (compiledRule, error) {
	filter, err := parseRuleFilter(rule.Filter)
	if err != nil {
		return compiledRule{}, err
	}
	action := strings.ToUpper(strings.TrimSpace(rule.Action))
	compiled := compiledRule{id: rule.ID, name: rule.Name, action: action, filter: filter}

	var config actionConfig
	if len(bytes.TrimSpace(rule.ActionConfig)) > 0 {
		decoder := json.NewDecoder(bytes.NewReader(rule.ActionConfig))
		decoder.DisallowUnknownFields()
		if err := decoder.Decode(&config); err != nil {
			return compiledRule{}, fmt.Errorf("actionConfig: %w", err)
		}
	}

	switch action {
	case "DROP":
	case "PARSE_REGEX":
		compiled.re, err = compileRuleRegex(config.Pattern)
		if err != nil {
			return compiledRule{}, err
		}
		for index, name := range compiled.re.SubexpNames() {
			if index == 0 || name == "" {
				continue
			}
			if err := validateRuleKey(name); err != nil {
				return compiledRule{}, fmt.Errorf("capture group %q: %w", name, err)
			}
			compiled.groupNames = append(compiled.groupNames, name)
		}
		if len(compiled.groupNames) == 0 {
			return compiledRule{}, fmt.Errorf("PARSE_REGEX requires a named capture group")
		}
	case "ADD_ATTRIBUTE":
		if err := validateRuleKey(config.Key); err != nil {
			return compiledRule{}, fmt.Errorf("attribute key: %w", err)
		}
		if strings.TrimSpace(config.Value) == "" {
			return compiledRule{}, fmt.Errorf("ADD_ATTRIBUTE value is required")
		}
		if utf8.RuneCountInString(config.Value) > maxAttributeValue {
			return compiledRule{}, fmt.Errorf("ADD_ATTRIBUTE value is too long")
		}
		compiled.key = config.Key
		compiled.value = config.Value
	case "MASK":
		compiled.re, err = compileRuleRegex(config.Pattern)
		if err != nil {
			return compiledRule{}, err
		}
		compiled.maskTarget = strings.ToUpper(strings.TrimSpace(config.Target))
		if compiled.maskTarget == "" {
			compiled.maskTarget = "MESSAGE"
		}
		if compiled.maskTarget != "MESSAGE" && compiled.maskTarget != "CONTEXT" {
			return compiledRule{}, fmt.Errorf("MASK target must be message or context")
		}
		if compiled.maskTarget == "CONTEXT" {
			if err := validateRuleKey(config.Field); err != nil {
				return compiledRule{}, fmt.Errorf("MASK field: %w", err)
			}
			compiled.maskField = config.Field
		} else if config.Field != "" {
			return compiledRule{}, fmt.Errorf("MASK field is only valid for context")
		}
	default:
		return compiledRule{}, fmt.Errorf("unsupported action %q", rule.Action)
	}
	return compiled, nil
}

func compileRuleRegex(pattern string) (*regexp.Regexp, error) {
	if strings.TrimSpace(pattern) == "" {
		return nil, fmt.Errorf("pattern is required")
	}
	if len(pattern) > maxPatternLength {
		return nil, fmt.Errorf("pattern is too long")
	}
	compiled, err := regexp.Compile(pattern)
	if err != nil {
		return nil, fmt.Errorf("pattern is invalid: %w", err)
	}
	return compiled, nil
}

func validateRuleKey(key string) error {
	if !contextKeyPattern.MatchString(key) || len(key) > maxContextKeyLength {
		return fmt.Errorf("invalid key %q", key)
	}
	if reservedRuleKeys[key] {
		return fmt.Errorf("key %q is reserved", key)
	}
	return nil
}

func parseRuleFilter(expression string) (ruleFilter, error) {
	trimmed := strings.TrimSpace(expression)
	if trimmed == "" || trimmed == "*" {
		return ruleFilter{}, nil
	}

	var clauses []ruleClause
	index := 0
	for index < len(trimmed) {
		index = skipFilterSeparators(trimmed, index)
		if index >= len(trimmed) {
			break
		}
		colon := strings.IndexByte(trimmed[index:], ':')
		if colon < 0 {
			return ruleFilter{}, fmt.Errorf("filter clause must use key:value syntax")
		}
		colon += index
		key := strings.TrimSpace(trimmed[index:colon])
		if key == "" {
			return ruleFilter{}, fmt.Errorf("filter clause is missing a key")
		}
		value, next, err := readFilterValue(trimmed, skipFilterWhitespace(trimmed, colon+1))
		if err != nil {
			return ruleFilter{}, err
		}
		clause, err := newRuleClause(key, value)
		if err != nil {
			return ruleFilter{}, err
		}
		clauses = append(clauses, clause)
		index = next
	}
	return ruleFilter{clauses: clauses}, nil
}

func newRuleClause(rawKey, value string) (ruleClause, error) {
	if value == "" {
		return ruleClause{}, fmt.Errorf("filter value must not be empty")
	}
	if len(value) > 512 {
		return ruleClause{}, fmt.Errorf("filter value is too long")
	}
	key := strings.ToLower(rawKey)
	switch key {
	case "level":
		return ruleClause{kind: "level", value: value}, nil
	case "message":
		return ruleClause{kind: "message", value: value}, nil
	case "host", "service":
		return ruleClause{kind: "context", key: key, value: value}, nil
	default:
		if !strings.HasPrefix(key, "context.") {
			return ruleClause{}, fmt.Errorf("unknown filter key %q", rawKey)
		}
		contextKey := rawKey[len("context."):]
		if !contextKeyPattern.MatchString(contextKey) {
			return ruleClause{}, fmt.Errorf("invalid context filter key %q", contextKey)
		}
		return ruleClause{kind: "context", key: contextKey, value: value}, nil
	}
}

func readFilterValue(expression string, start int) (string, int, error) {
	if start >= len(expression) {
		return "", start, fmt.Errorf("filter clause is missing a value")
	}
	if expression[start] == '"' {
		var value strings.Builder
		for index := start + 1; index < len(expression); index++ {
			c := expression[index]
			if c == '"' {
				next := index + 1
				if next < len(expression) && !isFilterSpace(expression[next]) &&
					!strings.HasPrefix(expression[next:], "&&") {
					return "", next, fmt.Errorf("unexpected text after a quoted filter value")
				}
				return value.String(), next, nil
			}
			if c == '\\' && index+1 < len(expression) {
				escaped := expression[index+1]
				if escaped == '"' || escaped == '\\' {
					value.WriteByte(escaped)
				} else {
					value.WriteByte(c)
					value.WriteByte(escaped)
				}
				index++
				continue
			}
			value.WriteByte(c)
		}
		return "", len(expression), fmt.Errorf("quoted filter value is not closed")
	}

	end := start
	for end < len(expression) && !isFilterSpace(expression[end]) &&
		!strings.HasPrefix(expression[end:], "&&") {
		end++
	}
	return expression[start:end], end, nil
}

func skipFilterSeparators(expression string, start int) int {
	index := skipFilterWhitespace(expression, start)
	for strings.HasPrefix(expression[index:], "&&") {
		index = skipFilterWhitespace(expression, index+2)
	}
	return index
}

func skipFilterWhitespace(expression string, start int) int {
	for start < len(expression) && isFilterSpace(expression[start]) {
		start++
	}
	return start
}

func isFilterSpace(c byte) bool {
	return c == ' ' || c == '\t' || c == '\n' || c == '\r'
}

func (f ruleFilter) matches(level, message string, document *ruleDocument) bool {
	for _, clause := range f.clauses {
		switch clause.kind {
		case "level":
			if !strings.EqualFold(level, clause.value) {
				return false
			}
		case "message":
			if !strings.Contains(strings.ToLower(message), strings.ToLower(clause.value)) {
				return false
			}
		case "context":
			if !document.valid || document.stringValue(clause.key) != clause.value {
				return false
			}
		}
	}
	return true
}

func parseRuleDocument(raw string) *ruleDocument {
	document := &ruleDocument{original: raw, values: map[string]interface{}{}, valid: true}
	if strings.TrimSpace(raw) == "" {
		return document
	}
	decoder := json.NewDecoder(strings.NewReader(raw))
	decoder.UseNumber()
	if err := decoder.Decode(&document.values); err != nil {
		document.valid = false
		document.values = map[string]interface{}{}
		return document
	}
	if document.values == nil {
		document.values = map[string]interface{}{}
	}
	return document
}

func (d *ruleDocument) stringValue(key string) string {
	if !d.valid || key == "" {
		return ""
	}
	return contextString(d.values[key])
}

func contextString(value interface{}) string {
	switch typed := value.(type) {
	case string:
		return typed
	case json.Number:
		return typed.String()
	case float64:
		return strconv.FormatFloat(typed, 'f', -1, 64)
	case bool:
		return strconv.FormatBool(typed)
	default:
		return ""
	}
}

// set applies a context change only if the resulting JSON stays within the
// backend's context limit. Failed changes leave the document untouched.
func (d *ruleDocument) set(key, value string) bool {
	if !d.valid || key == "" {
		return false
	}
	next := cloneContextValues(d.values)
	next[key] = value
	if !fitsRuleContext(next) {
		return false
	}
	d.values = next
	d.modified = true
	return true
}

// setAll applies a group of context changes atomically, matching the backend:
// either every captured field fits or the context is left untouched.
func (d *ruleDocument) setAll(values map[string]string) bool {
	if !d.valid || len(values) == 0 {
		return false
	}
	next := cloneContextValues(d.values)
	for key, value := range values {
		next[key] = value
	}
	if !fitsRuleContext(next) {
		return false
	}
	d.values = next
	d.modified = true
	return true
}

func (d *ruleDocument) mask(key string, re *regexp.Regexp) bool {
	if !d.valid || key == "" {
		return false
	}
	current := contextString(d.values[key])
	if current == "" {
		return false
	}
	masked := re.ReplaceAllString(current, "[REDACTED]")
	if masked == current {
		return false
	}
	return d.set(key, masked)
}

func cloneContextValues(values map[string]interface{}) map[string]interface{} {
	clone := make(map[string]interface{}, len(values)+1)
	for key, value := range values {
		clone[key] = value
	}
	return clone
}

func fitsRuleContext(values map[string]interface{}) bool {
	encoded, err := json.Marshal(values)
	return err == nil && len(encoded) <= maxRuleContextBytes
}

func (d *ruleDocument) serialize() string {
	if !d.modified || !d.valid {
		return d.original
	}
	encoded, err := json.Marshal(d.values)
	if err != nil {
		return d.original
	}
	return string(encoded)
}

func applyPipelineRules(event Event, rules []compiledRule) (Event, bool) {
	if len(rules) == 0 {
		return event, false
	}
	message := event.Message
	document := parseRuleDocument(event.Context)

	for _, rule := range rules {
		if !rule.filter.matches(event.Level, message, document) {
			continue
		}
		switch rule.action {
		case "DROP":
			return event, true
		case "PARSE_REGEX":
			if !document.valid {
				continue
			}
			indexes := rule.re.FindStringSubmatchIndex(message)
			if indexes == nil {
				continue
			}
			additions := make(map[string]string, len(rule.groupNames))
			for _, name := range rule.groupNames {
				index := rule.re.SubexpIndex(name)
				start, end := indexes[2*index], indexes[2*index+1]
				if start < 0 {
					continue
				}
				additions[name] = truncateRunes(message[start:end], maxCapturedRunes)
			}
			document.setAll(additions)
		case "ADD_ATTRIBUTE":
			document.set(rule.key, rule.value)
		case "MASK":
			if rule.maskTarget == "MESSAGE" {
				masked := rule.re.ReplaceAllString(message, "[REDACTED]")
				if utf8.RuneCountInString(masked) <= maxMessageRunes {
					message = masked
				}
			} else {
				document.mask(rule.maskField, rule.re)
			}
		}
	}

	event.Message = message
	event.Context = document.serialize()
	return event, false
}
