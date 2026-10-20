package main

import (
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/metrics"
	"github.com/vibemonitoring/agent/internal/transport"
)

func TestSendWithBackoffSplitsBackendBatches(t *testing.T) {
	var sizes []int
	var mu sync.Mutex

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Metrics []metrics.Point `json:"metrics"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Errorf("decode body: %v", err)
		}
		mu.Lock()
		sizes = append(sizes, len(body.Metrics))
		mu.Unlock()
		w.WriteHeader(http.StatusAccepted)
	}))
	defer server.Close()

	client := metrics.NewClient(transport.NewClient(server.URL, "app_test"))
	points := make([]metrics.Point, 2500)
	for i := range points {
		points[i] = metrics.Point{
			Name:      "custom.metric",
			Value:     float64(i),
			Timestamp: time.Now().UTC(),
		}
	}

	sendWithBackoff(context.Background(), log.New(io.Discard, "", 0), client, points)

	mu.Lock()
	got := append([]int(nil), sizes...)
	mu.Unlock()

	want := []int{maxMetricBatchSize, maxMetricBatchSize, 500}
	if len(got) != len(want) {
		t.Fatalf("sent %d batches (%v), want %d", len(got), got, len(want))
	}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("batch %d size = %d, want %d", i, got[i], want[i])
		}
	}
}
