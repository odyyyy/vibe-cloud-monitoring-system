package config

import (
	"errors"
	"fmt"
	"net/url"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
	"k8s.io/apimachinery/pkg/labels"
)

// namespacePattern is the DNS-1123 label format Kubernetes requires for
// namespaces. Validating early turns a typo into a clear startup error instead
// of an empty watch.
var namespacePattern = regexp.MustCompile(`^[a-z0-9]([-a-z0-9]*[a-z0-9])?$`)

// Supported log source formats. Auto parses a line as JSON when it is a JSON
// object and falls back to plain text otherwise; text disables JSON parsing;
// json also falls back to plain text for lines that are not valid JSON objects.
const (
	LogFormatAuto = "auto"
	LogFormatJSON = "json"
	LogFormatText = "text"
)

const (
	defaultIntervalSeconds = 15
	defaultLogBatchSize    = 500
	defaultLogFlushSeconds = 5
	defaultStateFile       = ".agent_state.json"

	// maxLogBatchSize matches the backend LogIngestRequest.MAX_BATCH_SIZE. A larger
	// batch would be rejected with HTTP 400, so it is a configuration error here.
	maxLogBatchSize = 1000
)

// KubernetesMetadata identifies the pod container a log line came from. It is
// populated by pod discovery, never read from the configuration file.
type KubernetesMetadata struct {
	Pod       string
	Namespace string
	Container string
}

// LogSource configures one tailed log file.
type LogSource struct {
	// Path is the exact file (not a directory) the agent is allowed to read.
	Path string `yaml:"path"`
	// ServiceName is attached to every event as context; defaults to the file name.
	ServiceName string `yaml:"service_name"`
	// LevelPattern is an optional regex used to extract the severity. When it has a
	// capturing group, group 1 is used; otherwise the whole match is used.
	LevelPattern string `yaml:"level_pattern"`
	// Format controls line parsing. "auto" (the default) parses JSON objects and
	// falls back to plain text; "json" always attempts JSON with a plain-text
	// fallback; "text" disables JSON parsing.
	Format string `yaml:"format"`
	// Kubernetes is set for sources created by pod discovery.
	Kubernetes *KubernetesMetadata `yaml:"-"`
	// FromBeginning makes a newly discovered source read existing content instead
	// of skipping to the end. Pod discovery sets it so startup logs written
	// before the watch event are not lost.
	FromBeginning bool `yaml:"-"`
}

// EffectiveFormat returns the normalized format used for a source that was not
// necessarily validated (runtime-discovered pod sources). An empty value means
// auto detection, which preserves the plain-text behavior for non-JSON lines.
func (s LogSource) EffectiveFormat() string {
	f := strings.ToLower(strings.TrimSpace(s.Format))
	if f == "" {
		return LogFormatAuto
	}
	return f
}

// NetworkConfig controls which network interfaces the agent reports metrics
// for. The zero value selects the default auto mode.
type NetworkConfig struct {
	// Interfaces is an explicit allow-list of interface names reported by the
	// agent. When empty (default) the agent auto-selects physical interfaces
	// (eth*, ens*, enp*, wlan*) and skips loopback and virtual/bridge
	// interfaces (lo, docker0, veth*, br-*). Entries are matched exactly, so
	// "lo" must be listed explicitly to report the loopback interface.
	Interfaces []string `yaml:"interfaces"`
}

// MetricsConfig controls host metric collection.
type MetricsConfig struct {
	Network NetworkConfig `yaml:"network"`
}

// KubernetesConfig controls automatic discovery of pod logs from the Kubernetes
// node the agent runs on.
type KubernetesConfig struct {
	// Enabled turns pod log discovery on.
	Enabled bool
	// Namespace restricts discovery to one namespace; empty means all namespaces.
	Namespace string
	// LabelSelector restricts discovery to pods matching a selector, e.g. app=api.
	LabelSelector string
}

// UnmarshalYAML accepts both the project's snake_case convention and the
// camelCase labelSelector spelling used in Kubernetes tooling.
func (k *KubernetesConfig) UnmarshalYAML(value *yaml.Node) error {
	var raw struct {
		Enabled            bool   `yaml:"enabled"`
		Namespace          string `yaml:"namespace"`
		LabelSelector      string `yaml:"label_selector"`
		LabelSelectorCamel string `yaml:"labelSelector"`
	}
	if err := value.Decode(&raw); err != nil {
		return err
	}
	k.Enabled = raw.Enabled
	k.Namespace = raw.Namespace
	k.LabelSelector = raw.LabelSelector
	if k.LabelSelector == "" {
		k.LabelSelector = raw.LabelSelectorCamel
	}
	return nil
}

// Config holds the agent startup configuration loaded from a YAML file and
// optionally overridden by environment variables.
type Config struct {
	BackendURL      string           `yaml:"backend_url"`
	APIKey          string           `yaml:"api_key"`
	IntervalSeconds int              `yaml:"interval_seconds"`
	Logs            []LogSource      `yaml:"logs"`
	LogBatchSize    int              `yaml:"log_batch_size"`
	LogFlushSeconds int              `yaml:"log_flush_seconds"`
	StateFile       string           `yaml:"state_file"`
	Metrics         MetricsConfig    `yaml:"metrics"`
	Kubernetes      KubernetesConfig `yaml:"kubernetes"`
}

// LoadConfig reads, parses, applies environment overrides and validates the
// configuration file at path.
func LoadConfig(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read config %q: %w", path, err)
	}

	cfg := &Config{
		IntervalSeconds: defaultIntervalSeconds,
		LogBatchSize:    defaultLogBatchSize,
		LogFlushSeconds: defaultLogFlushSeconds,
		StateFile:       defaultStateFile,
	}
	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("parse config %q: %w", path, err)
	}

	applyEnvOverrides(cfg)

	if err := cfg.validate(); err != nil {
		return nil, err
	}
	return cfg, nil
}

// applyEnvOverrides lets secrets and deployment-specific values stay out of the
// committed configuration file.
func applyEnvOverrides(cfg *Config) {
	if v := strings.TrimSpace(os.Getenv("AGENT_BACKEND_URL")); v != "" {
		cfg.BackendURL = v
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_API_KEY")); v != "" {
		cfg.APIKey = v
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_INTERVAL_SECONDS")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.IntervalSeconds = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_LOG_BATCH_SIZE")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.LogBatchSize = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_LOG_FLUSH_SECONDS")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.LogFlushSeconds = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_STATE_FILE")); v != "" {
		cfg.StateFile = v
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_KUBERNETES_ENABLED")); v != "" {
		if enabled, err := strconv.ParseBool(v); err == nil {
			cfg.Kubernetes.Enabled = enabled
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_KUBERNETES_NAMESPACE")); v != "" {
		cfg.Kubernetes.Namespace = v
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_KUBERNETES_LABEL_SELECTOR")); v != "" {
		cfg.Kubernetes.LabelSelector = v
	}
}

func (c *Config) validate() error {
	if c.BackendURL == "" {
		return errors.New("backend_url is required")
	}
	u, err := url.Parse(c.BackendURL)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" {
		return fmt.Errorf("backend_url must be a valid http(s) URL, got %q", c.BackendURL)
	}
	if c.APIKey == "" {
		return errors.New("api_key is required")
	}
	if c.IntervalSeconds <= 0 {
		return fmt.Errorf("interval_seconds must be positive, got %d", c.IntervalSeconds)
	}
	if c.LogBatchSize <= 0 || c.LogBatchSize > maxLogBatchSize {
		return fmt.Errorf("log_batch_size must be between 1 and %d, got %d", maxLogBatchSize, c.LogBatchSize)
	}
	if c.LogFlushSeconds <= 0 {
		return fmt.Errorf("log_flush_seconds must be positive, got %d", c.LogFlushSeconds)
	}
	if c.StateFile == "" {
		return errors.New("state_file must not be empty")
	}

	for i, src := range c.Logs {
		if strings.TrimSpace(src.Path) == "" {
			return fmt.Errorf("logs[%d].path is required", i)
		}
		if src.LevelPattern != "" {
			if _, err := regexp.Compile(src.LevelPattern); err != nil {
				return fmt.Errorf("logs[%d].level_pattern is not a valid regex: %w", i, err)
			}
		}
		format := src.EffectiveFormat()
		if format != LogFormatAuto && format != LogFormatJSON && format != LogFormatText {
			return fmt.Errorf("logs[%d].format must be one of %q, %q or %q, got %q",
				i, LogFormatAuto, LogFormatJSON, LogFormatText, src.Format)
		}
		c.Logs[i].Format = format
	}

	namespace := strings.TrimSpace(c.Kubernetes.Namespace)
	if namespace == "" || strings.EqualFold(namespace, "all") {
		c.Kubernetes.Namespace = ""
	} else {
		c.Kubernetes.Namespace = namespace
		if !namespacePattern.MatchString(namespace) || len(namespace) > 63 {
			return fmt.Errorf("kubernetes.namespace must be a valid DNS-1123 label or \"all\", got %q", namespace)
		}
	}
	c.Kubernetes.LabelSelector = strings.TrimSpace(c.Kubernetes.LabelSelector)
	if c.Kubernetes.LabelSelector != "" {
		if _, err := labels.Parse(c.Kubernetes.LabelSelector); err != nil {
			return fmt.Errorf("kubernetes.label_selector is invalid: %w", err)
		}
	}
	return nil
}

// Interval is the metric collection period.
func (c *Config) Interval() time.Duration {
	return time.Duration(c.IntervalSeconds) * time.Second
}

// MaskKey returns a redacted representation of an API key so that logs never
// contain the full credential.
func MaskKey(key string) string {
	if key == "" {
		return ""
	}
	if len(key) <= 8 {
		return "****"
	}
	return key[:4] + "****" + key[len(key)-4:]
}
