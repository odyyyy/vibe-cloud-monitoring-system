package main

import (
	"context"
	"io"
	"log"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/metrics"
)

type recordingMetricSender struct {
	sizes []int
}

func (r *recordingMetricSender) SendMetrics(_ context.Context, points []metrics.Point) error {
	r.sizes = append(r.sizes, len(points))
	return nil
}

func TestSendMetricBatchesSplitsBackendBatches(t *testing.T) {
	sender := &recordingMetricSender{}
	points := make([]metrics.Point, 2500)
	for i := range points {
		points[i] = metrics.Point{
			Name:      "custom.metric",
			Value:     float64(i),
			Timestamp: time.Now().UTC(),
		}
	}

	sendMetricBatches(context.Background(), log.New(io.Discard, "", 0), sender, points)

	want := []int{maxMetricBatchSize, maxMetricBatchSize, 500}
	if len(sender.sizes) != len(want) {
		t.Fatalf("sent %d batches (%v), want %d", len(sender.sizes), sender.sizes, len(want))
	}
	for i := range want {
		if sender.sizes[i] != want[i] {
			t.Errorf("batch %d size = %d, want %d", i, sender.sizes[i], want[i])
		}
	}
}
