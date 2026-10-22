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
	defaultIntervalSeconds  = 15
	defaultLogBatchSize     = 500
	defaultLogFlushSeconds  = 5
	defaultStateFile        = ".agent_state.json"
	defaultReceiverPort     = 8125
	defaultReceiverHTTPPort = 8080

	defaultBufferDirectory = "/var/lib/monitoring-agent/buffer"
	defaultBufferMaxSizeMB = 256
	defaultBufferMaxAge    = 24
	defaultBackoffInitial  = 1000
	defaultBackoffMax      = 60000

	// maxLogBatchSize matches the backend LogIngestRequest.MAX_BATCH_SIZE. A larger
	// batch would be rejected with HTTP 400, so it is a configuration error here.
	maxLogBatchSize = 1000

	// defaultMaxLogsPerSecond and defaultLogSampleRate protect the backend from a
	// noisy application. A per-source ceiling of 500 logs/s keeps a single host
	// well below the ingestion limits, and 10% of the excess is kept so the signal
	// stays visible instead of disappearing entirely.
	defaultMaxLogsPerSecond = 500
	defaultLogSampleRate    = 0.1
)

// defaultAlwaysForwardLevels are the severities that bypass log throttling. FATAL
// and CRITICAL are kept distinct from ERROR so a flood of ordinary errors is still
// rate limited while genuine crashes are never dropped.
var defaultAlwaysForwardLevels = []string{"FATAL", "CRITICAL"}

// Supported local metric receiver protocols.
const (
	ReceiverProtocolHTTP   = "http"
	ReceiverProtocolStatsD = "statsd"
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

// ReceiverConfig controls the loopback-only listener that accepts custom
// metrics from applications running next to the agent. The receiver always
// binds to 127.0.0.1 and is opt-in.
type ReceiverConfig struct {
	Enabled  bool   `yaml:"enabled"`
	Protocol string `yaml:"protocol"`
	Port     int    `yaml:"port"`
}

// BufferConfig controls the bounded on-disk queue used when a batch cannot be
// delivered. The queue stores only telemetry payloads, never the API key.
type BufferConfig struct {
	Enabled     bool   `yaml:"enabled"`
	Directory   string `yaml:"directory"`
	MaxSizeMB   int    `yaml:"max_size_mb"`
	MaxAgeHours int    `yaml:"max_age_hours"`
}

// MaxSizeBytes returns the configured queue limit in bytes.
func (b BufferConfig) MaxSizeBytes() int64 {
	return int64(b.MaxSizeMB) * 1024 * 1024
}

// MaxAge returns the maximum age of a buffered batch. Zero disables age-based
// eviction; negative values are rejected during validation.
func (b BufferConfig) MaxAge() time.Duration {
	return time.Duration(b.MaxAgeHours) * time.Hour
}

// RetryConfig controls exponential backoff for transient send failures. Live
// batches use a small bounded number of attempts before being queued; the disk
// replay worker uses the same backoff between passes.
type RetryConfig struct {
	InitialBackoffMS int  `yaml:"initial_backoff_ms"`
	MaxBackoffMS     int  `yaml:"max_backoff_ms"`
	Jitter           bool `yaml:"jitter"`
}

// InitialBackoff returns the first retry delay.
func (r RetryConfig) InitialBackoff() time.Duration {
	return time.Duration(r.InitialBackoffMS) * time.Millisecond
}

// MaxBackoff returns the retry delay ceiling.
func (r RetryConfig) MaxBackoff() time.Duration {
	return time.Duration(r.MaxBackoffMS) * time.Millisecond
}

// LogThrottlingConfig controls agent-side rate limiting and sampling of log
// events. It protects the backend and the local buffer from an application that
// suddenly logs at an extreme rate.
type LogThrottlingConfig struct {
	// MaxLogsPerSecond is the per-source ceiling on forwarded log events. A value
	// of 0 disables throttling entirely.
	MaxLogsPerSecond int `yaml:"max_logs_per_second"`
	// SampleRate is the fraction (0..1) of excess events kept once the ceiling is
	// exceeded. For example 0.1 keeps 10% of the excess.
	SampleRate float64 `yaml:"sample_rate"`
	// AlwaysForwardLevels lists severities that bypass throttling, so critical
	// logs are never sampled away. Entries are matched case-insensitively.
	AlwaysForwardLevels []string `yaml:"always_forward_levels"`
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
	BackendURL      string              `yaml:"backend_url"`
	APIKey          string              `yaml:"api_key"`
	IntervalSeconds int                 `yaml:"interval_seconds"`
	Logs            []LogSource         `yaml:"logs"`
	LogBatchSize    int                 `yaml:"log_batch_size"`
	LogFlushSeconds int                 `yaml:"log_flush_seconds"`
	LogThrottling   LogThrottlingConfig `yaml:"log_throttling"`
	StateFile       string              `yaml:"state_file"`
	Metrics         MetricsConfig       `yaml:"metrics"`
	Kubernetes      KubernetesConfig    `yaml:"kubernetes"`
	Receiver        ReceiverConfig      `yaml:"receiver"`
	Buffer          BufferConfig        `yaml:"buffer"`
	Retry           RetryConfig         `yaml:"retry"`
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
		LogThrottling: LogThrottlingConfig{
			MaxLogsPerSecond:    defaultMaxLogsPerSecond,
			SampleRate:          defaultLogSampleRate,
			AlwaysForwardLevels: append([]string(nil), defaultAlwaysForwardLevels...),
		},
		Buffer: BufferConfig{
			Enabled:     true,
			Directory:   defaultBufferDirectory,
			MaxSizeMB:   defaultBufferMaxSizeMB,
			MaxAgeHours: defaultBufferMaxAge,
		},
		Retry: RetryConfig{
			InitialBackoffMS: defaultBackoffInitial,
			MaxBackoffMS:     defaultBackoffMax,
			Jitter:           true,
		},
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
	if v := strings.TrimSpace(os.Getenv("AGENT_LOG_MAX_PER_SECOND")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.LogThrottling.MaxLogsPerSecond = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_LOG_SAMPLE_RATE")); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			cfg.LogThrottling.SampleRate = f
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_LOG_ALWAYS_FORWARD_LEVELS")); v != "" {
		levels := make([]string, 0)
		for _, part := range strings.Split(v, ",") {
			if level := strings.TrimSpace(part); level != "" {
				levels = append(levels, level)
			}
		}
		cfg.LogThrottling.AlwaysForwardLevels = levels
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
	if v := strings.TrimSpace(os.Getenv("AGENT_RECEIVER_ENABLED")); v != "" {
		if enabled, err := strconv.ParseBool(v); err == nil {
			cfg.Receiver.Enabled = enabled
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_RECEIVER_PROTOCOL")); v != "" {
		cfg.Receiver.Protocol = v
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_RECEIVER_PORT")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.Receiver.Port = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_BUFFER_ENABLED")); v != "" {
		if enabled, err := strconv.ParseBool(v); err == nil {
			cfg.Buffer.Enabled = enabled
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_BUFFER_DIRECTORY")); v != "" {
		cfg.Buffer.Directory = v
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_BUFFER_MAX_SIZE_MB")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.Buffer.MaxSizeMB = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_BUFFER_MAX_AGE_HOURS")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.Buffer.MaxAgeHours = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_RETRY_INITIAL_BACKOFF_MS")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.Retry.InitialBackoffMS = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_RETRY_MAX_BACKOFF_MS")); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.Retry.MaxBackoffMS = n
		}
	}
	if v := strings.TrimSpace(os.Getenv("AGENT_RETRY_JITTER")); v != "" {
		if enabled, err := strconv.ParseBool(v); err == nil {
			cfg.Retry.Jitter = enabled
		}
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
	if c.LogThrottling.MaxLogsPerSecond < 0 {
		return fmt.Errorf("log_throttling.max_logs_per_second must not be negative, got %d",
			c.LogThrottling.MaxLogsPerSecond)
	}
	if c.LogThrottling.SampleRate < 0 || c.LogThrottling.SampleRate > 1 {
		return fmt.Errorf("log_throttling.sample_rate must be between 0 and 1, got %v",
			c.LogThrottling.SampleRate)
	}
	for i, level := range c.LogThrottling.AlwaysForwardLevels {
		trimmed := strings.TrimSpace(level)
		if !isKnownSeverity(trimmed) {
			return fmt.Errorf("log_throttling.always_forward_levels[%d] must be a known level, got %q", i, level)
		}
		c.LogThrottling.AlwaysForwardLevels[i] = strings.ToUpper(trimmed)
	}
	if c.StateFile == "" {
		return errors.New("state_file must not be empty")
	}

	if c.Buffer.Enabled && strings.TrimSpace(c.Buffer.Directory) == "" {
		return errors.New("buffer.directory must not be empty when buffer.enabled is true")
	}
	if c.Buffer.MaxSizeMB <= 0 {
		return fmt.Errorf("buffer.max_size_mb must be positive, got %d", c.Buffer.MaxSizeMB)
	}
	if c.Buffer.MaxAgeHours < 0 {
		return fmt.Errorf("buffer.max_age_hours must not be negative, got %d", c.Buffer.MaxAgeHours)
	}
	if c.Retry.InitialBackoffMS <= 0 {
		return fmt.Errorf("retry.initial_backoff_ms must be positive, got %d", c.Retry.InitialBackoffMS)
	}
	if c.Retry.MaxBackoffMS <= 0 {
		return fmt.Errorf("retry.max_backoff_ms must be positive, got %d", c.Retry.MaxBackoffMS)
	}
	if c.Retry.InitialBackoffMS > c.Retry.MaxBackoffMS {
		return fmt.Errorf("retry.initial_backoff_ms (%d) must not exceed retry.max_backoff_ms (%d)",
			c.Retry.InitialBackoffMS, c.Retry.MaxBackoffMS)
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

	protocol := strings.ToLower(strings.TrimSpace(c.Receiver.Protocol))
	if protocol == "" {
		protocol = ReceiverProtocolStatsD
	}
	if protocol != ReceiverProtocolHTTP && protocol != ReceiverProtocolStatsD {
		return fmt.Errorf("receiver.protocol must be %q or %q, got %q",
			ReceiverProtocolHTTP, ReceiverProtocolStatsD, c.Receiver.Protocol)
	}
	c.Receiver.Protocol = protocol
	if c.Receiver.Port < 0 || c.Receiver.Port > 65535 {
		return fmt.Errorf("receiver.port must be 0 (use default) or between 1 and 65535, got %d", c.Receiver.Port)
	}
	if c.Receiver.Port == 0 {
		if protocol == ReceiverProtocolHTTP {
			c.Receiver.Port = defaultReceiverHTTPPort
		} else {
			c.Receiver.Port = defaultReceiverPort
		}
	}
	return nil
}

// Interval is the metric collection period.
func (c *Config) Interval() time.Duration {
	return time.Duration(c.IntervalSeconds) * time.Second
}

// isKnownSeverity reports whether token names a severity the agent understands.
// It accepts the common aliases so always_forward_levels can use either spelling.
func isKnownSeverity(token string) bool {
	switch strings.ToUpper(strings.TrimSpace(token)) {
	case "TRACE", "DEBUG", "INFO", "WARN", "WARNING", "ERROR", "ERR", "FATAL", "CRITICAL":
		return true
	default:
		return false
	}
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
