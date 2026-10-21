package metrics

import (
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

func TestTagsCarryHostAndVersion(t *testing.T) {
	c := &Collector{hostname: "server-1"}

	tags := c.tags()

	if tags["host"] != "server-1" {
		t.Errorf("host tag = %q, want %q", tags["host"], "server-1")
	}
	if tags["version"] != Version {
		t.Errorf("version tag = %q, want %q", tags["version"], Version)
	}
}

func TestTagsWithAddsInterface(t *testing.T) {
	c := &Collector{hostname: "server-1"}

	tags := c.tagsWith("interface", "eth0")

	if tags["interface"] != "eth0" {
		t.Errorf("interface tag = %q, want %q", tags["interface"], "eth0")
	}
	if tags["host"] != "server-1" {
		t.Errorf("host tag = %q, want %q", tags["host"], "server-1")
	}
}

func TestNetworkFilterAutoMode(t *testing.T) {
	filter := newNetworkFilter(config.NetworkConfig{})

	included := []string{"eth0", "ens192", "enp0s3", "wlan0", "enp0s3-100"}
	for _, name := range included {
		if !filter.include(name) {
			t.Errorf("auto mode should include physical interface %q", name)
		}
	}

	excluded := []string{"lo", "Loopback", "docker0", "veth1a2b3c", "br-4d5e6f", "tun0", "sit0"}
	for _, name := range excluded {
		if filter.include(name) {
			t.Errorf("auto mode should exclude virtual/loopback interface %q", name)
		}
	}
}

func TestNetworkFilterExplicitListOverridesAutoMode(t *testing.T) {
	filter := newNetworkFilter(config.NetworkConfig{Interfaces: []string{"lo", "docker0"}})

	if !filter.include("lo") {
		t.Error("explicit list should include lo")
	}
	if !filter.include("docker0") {
		t.Error("explicit list should include docker0")
	}
	if filter.include("eth0") {
		t.Error("explicit list should exclude interfaces that are not listed")
	}
}

func TestNetworkFilterSanitizesExplicitEntries(t *testing.T) {
	filter := newNetworkFilter(config.NetworkConfig{Interfaces: []string{"eth 0"}})

	if !filter.include("eth-0") {
		t.Error("explicit entries should be sanitized before matching")
	}
	if filter.include("eth 0") {
		t.Error("raw unsanitized name should not match the filter")
	}
}

func TestSanitizeInterfaceName(t *testing.T) {
	cases := map[string]string{
		"eth0":     "eth0",
		"ens192":   "ens192",
		"eth0.100": "eth0-100",
		"eth 0":    "eth-0",
		"veth@123": "veth-123",
		"":         "",
	}
	for input, want := range cases {
		if got := sanitizeInterfaceName(input); got != want {
			t.Errorf("sanitizeInterfaceName(%q) = %q, want %q", input, got, want)
		}
	}
}

func TestMetricNamesMatchContract(t *testing.T) {
	names := []string{
		metricCPUUsage, metricMemoryUsage, metricDiskUsage,
		metricNetworkBytesSent, metricNetworkBytesRecv,
		metricNetworkPacketsSent, metricNetworkPacketsRecv,
		metricNetworkDropIn, metricNetworkDropOut,
		metricLoad1, metricLoad5, metricLoad15,
		metricUptime,
	}
	want := []string{
		"system.cpu.usage", "system.memory.usage", "system.disk.usage",
		"system.network.bytes_sent", "system.network.bytes_recv",
		"system.network.packets_sent", "system.network.packets_recv",
		"system.network.drop_in", "system.network.drop_out",
		"system.load.1", "system.load.5", "system.load.15",
		"system.uptime",
	}
	for i, name := range names {
		if name != want[i] {
			t.Errorf("metric name = %q, want %q", name, want[i])
		}
	}
}

func TestLogsDroppedPoint(t *testing.T) {
	c := &Collector{hostname: "server-1"}
	now := time.Date(2026, 10, 21, 12, 0, 0, 0, time.UTC)

	point := c.LogsDroppedPoint(42, now)

	if point.Name != "agent.logs.dropped_total" {
		t.Errorf("name = %q, want agent.logs.dropped_total", point.Name)
	}
	if point.Value != 42 {
		t.Errorf("value = %v, want 42", point.Value)
	}
	if !point.Timestamp.Equal(now) {
		t.Errorf("timestamp = %s, want %s", point.Timestamp, now)
	}
	if point.Tags["host"] != "server-1" || point.Tags["version"] != Version {
		t.Errorf("unexpected tags: %v", point.Tags)
	}
}
