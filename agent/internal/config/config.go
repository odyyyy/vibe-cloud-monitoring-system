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

// LogSource configures one tailed log file.
type LogSource struct {
	// Path is the exact file (not a directory) the agent is allowed to read.
	Path string `yaml:"path"`
	// ServiceName is attached to every event as context; defaults to the file name.
	ServiceName string `yaml:"service_name"`
	// LevelPattern is an optional regex used to extract the severity. When it has a
	// capturing group, group 1 is used; otherwise the whole match is used.
	LevelPattern string `yaml:"level_pattern"`
}

// Config holds the agent startup configuration loaded from a YAML file and
// optionally overridden by environment variables.
type Config struct {
	BackendURL      string      `yaml:"backend_url"`
	APIKey          string      `yaml:"api_key"`
	IntervalSeconds int         `yaml:"interval_seconds"`
	Logs            []LogSource `yaml:"logs"`
	LogBatchSize    int         `yaml:"log_batch_size"`
	LogFlushSeconds int         `yaml:"log_flush_seconds"`
	StateFile       string      `yaml:"state_file"`
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
