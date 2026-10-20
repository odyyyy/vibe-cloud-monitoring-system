// Package metrics collects host/container OS metrics and forwards them to the
// cloud ingestion API.
package metrics

import (
	"context"
	"fmt"
	"os"
	"path"
	"runtime"
	"strings"
	"time"

	"github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/disk"
	"github.com/shirou/gopsutil/v4/host"
	"github.com/shirou/gopsutil/v4/load"
	"github.com/shirou/gopsutil/v4/mem"
	"github.com/shirou/gopsutil/v4/net"

	"github.com/vibemonitoring/agent/internal/config"
)

const (
	metricCPUUsage    = "system.cpu.usage"
	metricMemoryUsage = "system.memory.usage"
	metricDiskUsage   = "system.disk.usage"

	metricNetworkBytesSent  = "system.network.bytes_sent"
	metricNetworkBytesRecv  = "system.network.bytes_recv"
	metricNetworkPacketsSent = "system.network.packets_sent"
	metricNetworkPacketsRecv = "system.network.packets_recv"
	metricNetworkDropIn     = "system.network.drop_in"
	metricNetworkDropOut    = "system.network.drop_out"

	metricLoad1  = "system.load.1"
	metricLoad5  = "system.load.5"
	metricLoad15 = "system.load.15"

	metricUptime = "system.uptime"
)

// defaultInterfacePatterns matches the physical interfaces reported in auto
// mode. Loopback (lo) and virtual/bridge interfaces (docker0, veth*, br-*)
// never match, so they are skipped unless explicitly configured.
var defaultInterfacePatterns = []string{"eth*", "ens*", "enp*", "wlan*"}

// Version is the agent version reported to the cloud as the "version" metric tag so the
// fleet view can show which build each host runs. Override at build time with
// -ldflags "-X github.com/vibemonitoring/agent/internal/metrics.Version=1.2.3".
var Version = "0.1.0"

// Point mirrors the backend MetricPoint DTO. Timestamp is an event time in UTC
// serialized as RFC 3339, which Spring maps onto java.time.Instant.
type Point struct {
	Name      string            `json:"name"`
	Value     float64           `json:"value"`
	Timestamp time.Time         `json:"timestamp"`
	Tags      map[string]string `json:"tags,omitempty"`
}

// networkFilter decides which network interfaces are reported. A nil explicit
// map means auto mode: only interfaces matching defaultInterfacePatterns are
// included.
type networkFilter struct {
	explicit map[string]bool
}

func newNetworkFilter(cfg config.NetworkConfig) networkFilter {
	if len(cfg.Interfaces) == 0 {
		return networkFilter{}
	}
	explicit := make(map[string]bool, len(cfg.Interfaces))
	for _, name := range cfg.Interfaces {
		if clean := sanitizeInterfaceName(name); clean != "" {
			explicit[clean] = true
		}
	}
	return networkFilter{explicit: explicit}
}

func (f networkFilter) include(name string) bool {
	if f.explicit != nil {
		return f.explicit[name]
	}
	for _, pattern := range defaultInterfacePatterns {
		if matched, err := path.Match(pattern, name); err == nil && matched {
			return true
		}
	}
	return false
}

// sanitizeInterfaceName reduces an interface name to the safe tag alphabet
// (ASCII alphanumerics and hyphens). Every other character becomes a hyphen so
// the value cannot break the tag format or explode cardinality.
func sanitizeInterfaceName(name string) string {
	var b strings.Builder
	b.Grow(len(name))
	for _, r := range name {
		switch {
		case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z', r >= '0' && r <= '9', r == '-':
			b.WriteRune(r)
		default:
			b.WriteRune('-')
		}
	}
	return b.String()
}

// Collector gathers basic host/container OS metrics using gopsutil.
type Collector struct {
	hostname string
	network  networkFilter
}

func NewCollector(network config.NetworkConfig) *Collector {
	hostname, err := os.Hostname()
	if err != nil || hostname == "" {
		hostname = "unknown"
	}
	return &Collector{hostname: hostname, network: newNetworkFilter(network)}
}

// Collect returns the current CPU, memory, disk, network, load and uptime
// metrics as metric points. Individual metric failures are tolerated; an error
// is returned only when no metric could be collected at all.
func (c *Collector) Collect(ctx context.Context) ([]Point, error) {
	now := time.Now().UTC()
	tags := c.tags()
	points := make([]Point, 0, 16)

	// interval=0 reports usage since the previous call (or since boot on the
	// first call), avoiding an extra blocking sample window per cycle.
	if usage, err := cpu.PercentWithContext(ctx, 0, false); err != nil {
		fmt.Fprintf(os.Stderr, "agent: cpu metric failed: %v\n", err)
	} else if len(usage) > 0 {
		points = append(points, Point{Name: metricCPUUsage, Value: usage[0], Timestamp: now, Tags: tags})
	}

	if vm, err := mem.VirtualMemoryWithContext(ctx); err != nil {
		fmt.Fprintf(os.Stderr, "agent: memory metric failed: %v\n", err)
	} else {
		points = append(points, Point{Name: metricMemoryUsage, Value: vm.UsedPercent, Timestamp: now, Tags: tags})
	}

	if usage, err := disk.UsageWithContext(ctx, diskPath()); err != nil {
		fmt.Fprintf(os.Stderr, "agent: disk metric failed: %v\n", err)
	} else {
		points = append(points, Point{Name: metricDiskUsage, Value: usage.UsedPercent, Timestamp: now, Tags: tags})
	}

	points = append(points, c.networkPoints(ctx, now)...)
	points = append(points, c.loadPoints(ctx, now)...)
	points = append(points, c.uptimePoint(ctx, now)...)

	if len(points) == 0 {
		return nil, fmt.Errorf("no OS metrics could be collected")
	}
	return points, nil
}

// networkPoints reports per-interface I/O counters as absolute values. The
// backend aggregation engine derives rates (bytes/s, packets/s) at query time.
// pernic=true is required: with false gopsutil sums every interface into a
// single "all" record, which carries no per-interface breakdown.
func (c *Collector) networkPoints(ctx context.Context, now time.Time) []Point {
	counters, err := net.IOCountersWithContext(ctx, true)
	if err != nil {
		fmt.Fprintf(os.Stderr, "agent: network metrics failed: %v\n", err)
		return nil
	}
	var points []Point
	for _, counter := range counters {
		name := sanitizeInterfaceName(counter.Name)
		if name == "" || !c.network.include(name) {
			continue
		}
		tags := c.tagsWith("interface", name)
		points = append(points,
			Point{Name: metricNetworkBytesSent, Value: float64(counter.BytesSent), Timestamp: now, Tags: tags},
			Point{Name: metricNetworkBytesRecv, Value: float64(counter.BytesRecv), Timestamp: now, Tags: tags},
			Point{Name: metricNetworkPacketsSent, Value: float64(counter.PacketsSent), Timestamp: now, Tags: tags},
			Point{Name: metricNetworkPacketsRecv, Value: float64(counter.PacketsRecv), Timestamp: now, Tags: tags},
			Point{Name: metricNetworkDropIn, Value: float64(counter.Dropin), Timestamp: now, Tags: tags},
			Point{Name: metricNetworkDropOut, Value: float64(counter.Dropout), Timestamp: now, Tags: tags},
		)
	}
	return points
}

func (c *Collector) loadPoints(ctx context.Context, now time.Time) []Point {
	avg, err := load.AvgWithContext(ctx)
	if err != nil {
		fmt.Fprintf(os.Stderr, "agent: load average metric failed: %v\n", err)
		return nil
	}
	return []Point{
		{Name: metricLoad1, Value: avg.Load1, Timestamp: now, Tags: c.tags()},
		{Name: metricLoad5, Value: avg.Load5, Timestamp: now, Tags: c.tags()},
		{Name: metricLoad15, Value: avg.Load15, Timestamp: now, Tags: c.tags()},
	}
}

func (c *Collector) uptimePoint(ctx context.Context, now time.Time) []Point {
	uptime, err := host.UptimeWithContext(ctx)
	if err != nil {
		fmt.Fprintf(os.Stderr, "agent: uptime metric failed: %v\n", err)
		return nil
	}
	return []Point{{Name: metricUptime, Value: float64(uptime), Timestamp: now, Tags: c.tags()}}
}

func diskPath() string {
	if runtime.GOOS == "windows" {
		return "C:"
	}
	return "/"
}

// tags returns the labels attached to every collected point. The host identifies the agent
// instance in the cloud fleet view, the version lets the platform show which build it runs.
func (c *Collector) tags() map[string]string {
	return map[string]string{"host": c.hostname, "version": Version}
}

// tagsWith returns the base tags plus one extra key/value pair.
func (c *Collector) tagsWith(key, value string) map[string]string {
	tags := c.tags()
	tags[key] = value
	return tags
}
