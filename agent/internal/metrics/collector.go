// Package metrics collects host/container OS metrics and forwards them to the
// cloud ingestion API.
package metrics

import (
	"context"
	"fmt"
	"os"
	"runtime"
	"time"

	"github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/disk"
	"github.com/shirou/gopsutil/v4/mem"
)

const (
	metricCPUUsage    = "system.cpu.usage"
	metricMemoryUsage = "system.memory.usage"
	metricDiskUsage   = "system.disk.usage"
)

// Point mirrors the backend MetricPoint DTO. Timestamp is an event time in UTC
// serialized as RFC 3339, which Spring maps onto java.time.Instant.
type Point struct {
	Name      string            `json:"name"`
	Value     float64           `json:"value"`
	Timestamp time.Time         `json:"timestamp"`
	Tags      map[string]string `json:"tags,omitempty"`
}

// Collector gathers basic host/container OS metrics using gopsutil.
type Collector struct {
	hostname string
}

func NewCollector() *Collector {
	hostname, err := os.Hostname()
	if err != nil || hostname == "" {
		hostname = "unknown"
	}
	return &Collector{hostname: hostname}
}

// Collect returns the current CPU, memory and disk usage as metric points.
// Individual metric failures are tolerated; an error is returned only when no
// metric could be collected at all.
func (c *Collector) Collect(ctx context.Context) ([]Point, error) {
	now := time.Now().UTC()
	tags := map[string]string{"host": c.hostname}
	points := make([]Point, 0, 3)

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

	if len(points) == 0 {
		return nil, fmt.Errorf("no OS metrics could be collected")
	}
	return points, nil
}

func diskPath() string {
	if runtime.GOOS == "windows" {
		return "C:"
	}
	return "/"
}
