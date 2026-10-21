package config

import (
	"os"
	"path/filepath"
	"testing"
)

func writeConfigFile(t *testing.T, body string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(path, []byte(body), 0o600); err != nil {
		t.Fatalf("write config: %v", err)
	}
	return path
}

func TestLoadConfigParsesLogSources(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
logs:
  - path: /var/log/app.log
    service_name: checkout
    level_pattern: 'level=(\w+)'
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if len(cfg.Logs) != 1 {
		t.Fatalf("expected 1 log source, got %d", len(cfg.Logs))
	}
	if cfg.Logs[0].Path != "/var/log/app.log" || cfg.Logs[0].ServiceName != "checkout" {
		t.Fatalf("unexpected log source: %+v", cfg.Logs[0])
	}
	if cfg.Logs[0].Format != LogFormatAuto {
		t.Errorf("default format = %q, want %q", cfg.Logs[0].Format, LogFormatAuto)
	}
	if cfg.LogBatchSize != defaultLogBatchSize {
		t.Errorf("default log_batch_size = %d, want %d", cfg.LogBatchSize, defaultLogBatchSize)
	}
	if cfg.LogFlushSeconds != defaultLogFlushSeconds {
		t.Errorf("default log_flush_seconds = %d, want %d", cfg.LogFlushSeconds, defaultLogFlushSeconds)
	}
	if cfg.StateFile != defaultStateFile {
		t.Errorf("default state_file = %q, want %q", cfg.StateFile, defaultStateFile)
	}
}

func TestLoadConfigAllowsEmptyLogs(t *testing.T) {
	path := writeConfigFile(t, "backend_url: http://localhost:8080\napi_key: app_test\nlogs: []\n")
	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if len(cfg.Logs) != 0 {
		t.Fatalf("expected no log sources, got %d", len(cfg.Logs))
	}
}

func TestLoadConfigDefaultsLogThrottling(t *testing.T) {
	path := writeConfigFile(t, "backend_url: http://localhost:8080\napi_key: app_test\n")
	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if cfg.LogThrottling.MaxLogsPerSecond != defaultMaxLogsPerSecond {
		t.Errorf("default max_logs_per_second = %d, want %d",
			cfg.LogThrottling.MaxLogsPerSecond, defaultMaxLogsPerSecond)
	}
	if cfg.LogThrottling.SampleRate != defaultLogSampleRate {
		t.Errorf("default sample_rate = %v, want %v", cfg.LogThrottling.SampleRate, defaultLogSampleRate)
	}
	if len(cfg.LogThrottling.AlwaysForwardLevels) != 2 ||
		cfg.LogThrottling.AlwaysForwardLevels[0] != "FATAL" ||
		cfg.LogThrottling.AlwaysForwardLevels[1] != "CRITICAL" {
		t.Errorf("default always_forward_levels = %v, want [FATAL CRITICAL]",
			cfg.LogThrottling.AlwaysForwardLevels)
	}
}

func TestLoadConfigParsesLogThrottling(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
log_throttling:
  max_logs_per_second: 100
  sample_rate: 0.25
  always_forward_levels: ["fatal", "error"]
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if cfg.LogThrottling.MaxLogsPerSecond != 100 {
		t.Errorf("max_logs_per_second = %d, want 100", cfg.LogThrottling.MaxLogsPerSecond)
	}
	if cfg.LogThrottling.SampleRate != 0.25 {
		t.Errorf("sample_rate = %v, want 0.25", cfg.LogThrottling.SampleRate)
	}
	if len(cfg.LogThrottling.AlwaysForwardLevels) != 2 ||
		cfg.LogThrottling.AlwaysForwardLevels[0] != "FATAL" ||
		cfg.LogThrottling.AlwaysForwardLevels[1] != "ERROR" {
		t.Errorf("always_forward_levels = %v, want normalized [FATAL ERROR]",
			cfg.LogThrottling.AlwaysForwardLevels)
	}
}

func TestLoadConfigRejectsInvalidLogThrottling(t *testing.T) {
	cases := map[string]string{
		"negative rate": `
log_throttling:
  max_logs_per_second: -1
`,
		"sample rate above one": `
log_throttling:
  sample_rate: 1.5
`,
		"negative sample rate": `
log_throttling:
  sample_rate: -0.1
`,
		"unknown level": `
log_throttling:
  always_forward_levels: ["NOT_A_LEVEL"]
`,
	}
	for name, body := range cases {
		t.Run(name, func(t *testing.T) {
			path := writeConfigFile(t, "backend_url: http://localhost:8080\napi_key: app_test\n"+body)
			if _, err := LoadConfig(path); err == nil {
				t.Fatal("expected a configuration error")
			}
		})
	}
}

func TestLogThrottlingEnvOverrides(t *testing.T) {
	t.Setenv("AGENT_LOG_MAX_PER_SECOND", "50")
	t.Setenv("AGENT_LOG_SAMPLE_RATE", "0.5")
	t.Setenv("AGENT_LOG_ALWAYS_FORWARD_LEVELS", "error, fatal")

	path := writeConfigFile(t, "backend_url: http://localhost:8080\napi_key: app_test\n")
	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if cfg.LogThrottling.MaxLogsPerSecond != 50 {
		t.Errorf("max_logs_per_second = %d, want 50", cfg.LogThrottling.MaxLogsPerSecond)
	}
	if cfg.LogThrottling.SampleRate != 0.5 {
		t.Errorf("sample_rate = %v, want 0.5", cfg.LogThrottling.SampleRate)
	}
	if len(cfg.LogThrottling.AlwaysForwardLevels) != 2 ||
		cfg.LogThrottling.AlwaysForwardLevels[0] != "ERROR" ||
		cfg.LogThrottling.AlwaysForwardLevels[1] != "FATAL" {
		t.Errorf("always_forward_levels = %v, want [ERROR FATAL]",
			cfg.LogThrottling.AlwaysForwardLevels)
	}
}

func TestLoadConfigRejectsInvalidLevelPattern(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
logs:
  - path: /var/log/app.log
    level_pattern: 'level=(\w+'
`)
	if _, err := LoadConfig(path); err == nil {
		t.Fatal("expected error for invalid level_pattern")
	}
}

func TestLoadConfigRejectsBatchSizeOverLimit(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
log_batch_size: 5000
`)
	if _, err := LoadConfig(path); err == nil {
		t.Fatal("expected error for log_batch_size over backend limit")
	}
}

func TestLoadConfigParsesKubernetes(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
kubernetes:
  enabled: true
  namespace: payments
  label_selector: "app=api"
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if !cfg.Kubernetes.Enabled {
		t.Error("expected kubernetes.enabled to be true")
	}
	if cfg.Kubernetes.Namespace != "payments" {
		t.Errorf("namespace = %q, want payments", cfg.Kubernetes.Namespace)
	}
	if cfg.Kubernetes.LabelSelector != "app=api" {
		t.Errorf("label selector = %q, want app=api", cfg.Kubernetes.LabelSelector)
	}
}

func TestLoadConfigAcceptsCamelCaseLabelSelector(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
kubernetes:
  enabled: true
  labelSelector: "app=api"
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if cfg.Kubernetes.LabelSelector != "app=api" {
		t.Errorf("label selector = %q, want app=api", cfg.Kubernetes.LabelSelector)
	}
}

func TestLoadConfigTreatsAllAsEveryNamespace(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
kubernetes:
  enabled: true
  namespace: all
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if cfg.Kubernetes.Namespace != "" {
		t.Errorf("namespace = %q, want empty (all namespaces)", cfg.Kubernetes.Namespace)
	}
}

func TestLoadConfigRejectsInvalidKubernetesNamespace(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
kubernetes:
  enabled: true
  namespace: "Payments Team"
`)
	if _, err := LoadConfig(path); err == nil {
		t.Fatal("expected error for invalid kubernetes.namespace")
	}
}

func TestLoadConfigRejectsInvalidLabelSelector(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
kubernetes:
  enabled: true
  label_selector: "app in ("
`)
	if _, err := LoadConfig(path); err == nil {
		t.Fatal("expected error for invalid kubernetes.label_selector")
	}
}

func TestKubernetesEnvOverrides(t *testing.T) {
	t.Setenv("AGENT_KUBERNETES_ENABLED", "true")
	t.Setenv("AGENT_KUBERNETES_NAMESPACE", "all")
	t.Setenv("AGENT_KUBERNETES_LABEL_SELECTOR", "app=api")

	path := writeConfigFile(t, "backend_url: http://localhost:8080\napi_key: app_test\n")
	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if !cfg.Kubernetes.Enabled {
		t.Error("expected env to enable kubernetes discovery")
	}
	if cfg.Kubernetes.Namespace != "" {
		t.Errorf("namespace = %q, want empty", cfg.Kubernetes.Namespace)
	}
	if cfg.Kubernetes.LabelSelector != "app=api" {
		t.Errorf("label selector = %q, want app=api", cfg.Kubernetes.LabelSelector)
	}
}

func TestLoadConfigRejectsMissingPath(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
logs:
  - service_name: checkout
`)
	if _, err := LoadConfig(path); err == nil {
		t.Fatal("expected error for missing logs[].path")
	}
}

func TestLoadConfigParsesNetworkInterfaceAllowList(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
metrics:
  network:
    interfaces:
      - eth0
      - lo
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if len(cfg.Metrics.Network.Interfaces) != 2 {
		t.Fatalf("expected 2 interfaces, got %d", len(cfg.Metrics.Network.Interfaces))
	}
	if cfg.Metrics.Network.Interfaces[0] != "eth0" || cfg.Metrics.Network.Interfaces[1] != "lo" {
		t.Errorf("unexpected interfaces: %v", cfg.Metrics.Network.Interfaces)
	}
}

func TestLoadConfigDefaultsNetworkToEmptyAllowList(t *testing.T) {
	path := writeConfigFile(t, "backend_url: http://localhost:8080\napi_key: app_test\n")

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if len(cfg.Metrics.Network.Interfaces) != 0 {
		t.Errorf("expected empty interface list, got %v", cfg.Metrics.Network.Interfaces)
	}
}

func TestLoadConfigParsesLogFormat(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
logs:
  - path: /var/log/json.log
    format: "JSON"
  - path: /var/log/text.log
    format: text
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if cfg.Logs[0].Format != LogFormatJSON {
		t.Errorf("first format = %q, want %q", cfg.Logs[0].Format, LogFormatJSON)
	}
	if cfg.Logs[1].Format != LogFormatText {
		t.Errorf("second format = %q, want %q", cfg.Logs[1].Format, LogFormatText)
	}
}

func TestLoadConfigRejectsUnknownLogFormat(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
logs:
  - path: /var/log/app.log
    format: xml
`)

	if _, err := LoadConfig(path); err == nil {
		t.Fatal("expected error for unknown logs[].format")
	}
}

func TestLoadConfigDefaultsReceiver(t *testing.T) {
	path := writeConfigFile(t, "backend_url: http://localhost:8080\napi_key: app_test\n")

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if cfg.Receiver.Enabled {
		t.Error("receiver should be disabled by default")
	}
	if cfg.Receiver.Protocol != ReceiverProtocolStatsD {
		t.Errorf("default protocol = %q, want %q", cfg.Receiver.Protocol, ReceiverProtocolStatsD)
	}
	if cfg.Receiver.Port != defaultReceiverPort {
		t.Errorf("default port = %d, want %d", cfg.Receiver.Port, defaultReceiverPort)
	}
}

func TestLoadConfigParsesReceiver(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
receiver:
  enabled: true
  protocol: statsd
  port: 9125
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if !cfg.Receiver.Enabled {
		t.Error("expected receiver.enabled to be true")
	}
	if cfg.Receiver.Protocol != ReceiverProtocolStatsD {
		t.Errorf("protocol = %q, want %q", cfg.Receiver.Protocol, ReceiverProtocolStatsD)
	}
	if cfg.Receiver.Port != 9125 {
		t.Errorf("port = %d, want 9125", cfg.Receiver.Port)
	}
}

func TestLoadConfigDefaultsHTTPReceiverPort(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
receiver:
  enabled: true
  protocol: HTTP
`)

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if cfg.Receiver.Protocol != ReceiverProtocolHTTP {
		t.Errorf("protocol = %q, want %q", cfg.Receiver.Protocol, ReceiverProtocolHTTP)
	}
	if cfg.Receiver.Port != defaultReceiverHTTPPort {
		t.Errorf("port = %d, want %d", cfg.Receiver.Port, defaultReceiverHTTPPort)
	}
}

func TestLoadConfigRejectsInvalidReceiverProtocol(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
receiver:
  enabled: true
  protocol: tcp
`)

	if _, err := LoadConfig(path); err == nil {
		t.Fatal("expected error for unsupported receiver.protocol")
	}
}

func TestLoadConfigRejectsInvalidReceiverPort(t *testing.T) {
	path := writeConfigFile(t, `
backend_url: http://localhost:8080
api_key: app_test
receiver:
  enabled: true
  protocol: statsd
  port: 70000
`)

	if _, err := LoadConfig(path); err == nil {
		t.Fatal("expected error for receiver.port over 65535")
	}
}

func TestReceiverEnvOverrides(t *testing.T) {
	t.Setenv("AGENT_RECEIVER_ENABLED", "true")
	t.Setenv("AGENT_RECEIVER_PROTOCOL", "http")
	t.Setenv("AGENT_RECEIVER_PORT", "9090")

	path := writeConfigFile(t, "backend_url: http://localhost:8080\napi_key: app_test\n")
	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("LoadConfig: %v", err)
	}
	if !cfg.Receiver.Enabled {
		t.Error("expected env to enable the receiver")
	}
	if cfg.Receiver.Protocol != ReceiverProtocolHTTP {
		t.Errorf("protocol = %q, want %q", cfg.Receiver.Protocol, ReceiverProtocolHTTP)
	}
	if cfg.Receiver.Port != 9090 {
		t.Errorf("port = %d, want 9090", cfg.Receiver.Port)
	}
}
