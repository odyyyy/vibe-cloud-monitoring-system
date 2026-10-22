package forward

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/buffer"
	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/metrics"
	"github.com/vibemonitoring/agent/internal/transport"
)

func testRetryConfig() config.RetryConfig {
	return config.RetryConfig{InitialBackoffMS: 1, MaxBackoffMS: 5, Jitter: false}
}

func openTestQueue(t *testing.T) *buffer.Queue {
	t.Helper()
	q := buffer.Open(config.BufferConfig{
		Enabled:     true,
		Directory:   t.TempDir(),
		MaxSizeMB:   8,
		MaxAgeHours: 24,
	}, log.New(io.Discard, "", 0))
	if !q.Enabled() {
		t.Fatal("queue should be enabled")
	}
	return q
}

func point(name string) metrics.Point {
	return metrics.Point{Name: name, Value: 1, Timestamp: time.Now().UTC()}
}

func TestSenderRetriesTransientFailure(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if calls.Add(1) == 1 {
			http.Error(w, "temporary", http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusAccepted)
	}))
	defer server.Close()

	q := openTestQueue(t)
	sender := New(transport.NewClient(server.URL, "app_test"), q, testRetryConfig(), log.New(io.Discard, "", 0))

	if err := sender.SendMetrics(context.Background(), []metrics.Point{point("a")}); err != nil {
		t.Fatalf("SendMetrics: %v", err)
	}
	if got := sender.RetriesTotal(); got != 1 {
		t.Errorf("retries = %d, want 1", got)
	}
	if got := sender.FailuresTotal(); got != 0 {
		t.Errorf("failures = %d, want 0", got)
	}
	if q.Len() != 0 {
		t.Errorf("queue should be empty, got %d", q.Len())
	}
}

func TestSenderDropsPermanentFailureWithoutBuffering(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "bad request", http.StatusBadRequest)
	}))
	defer server.Close()

	q := openTestQueue(t)
	sender := New(transport.NewClient(server.URL, "app_test"), q, testRetryConfig(), log.New(io.Discard, "", 0))

	if err := sender.SendMetrics(context.Background(), []metrics.Point{point("a")}); err == nil {
		t.Fatal("expected an error for HTTP 400")
	}
	if got := sender.FailuresTotal(); got != 1 {
		t.Errorf("failures = %d, want 1", got)
	}
	if q.Len() != 0 {
		t.Errorf("permanent failures must not be buffered, queue = %d", q.Len())
	}
	if got := sender.RetriesTotal(); got != 0 {
		t.Errorf("retries = %d, want 0", got)
	}
}

func TestSenderReportsAuthFailureOncePerInterval(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
	}))
	defer server.Close()

	q := openTestQueue(t)
	var logs bytes.Buffer
	sender := New(transport.NewClient(server.URL, "app_secret"), q, testRetryConfig(), log.New(&logs, "", 0))

	for i := 0; i < 3; i++ {
		_ = sender.SendMetrics(context.Background(), []metrics.Point{point("a")})
	}

	if got := sender.FailuresTotal(); got != 3 {
		t.Errorf("failures = %d, want 3", got)
	}
	if q.Len() != 0 {
		t.Errorf("auth failures must not be buffered, queue = %d", q.Len())
	}
	if count := strings.Count(logs.String(), "rejected the API key"); count != 1 {
		t.Errorf("auth log lines = %d, want 1; logs:\n%s", count, logs.String())
	}
	if strings.Contains(logs.String(), "app_secret") {
		t.Error("auth error log leaked the API key")
	}
}

func TestSenderBuffersDuringOutageAndReplaysInOrder(t *testing.T) {
	var down atomic.Bool
	down.Store(true)
	var mu sync.Mutex
	var delivered []string

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if down.Load() {
			http.Error(w, "down", http.StatusInternalServerError)
			return
		}
		var body struct {
			Metrics []metrics.Point `json:"metrics"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Errorf("decode: %v", err)
		}
		mu.Lock()
		for _, metric := range body.Metrics {
			delivered = append(delivered, metric.Name)
		}
		mu.Unlock()
		w.WriteHeader(http.StatusAccepted)
	}))
	defer server.Close()

	q := openTestQueue(t)
	sender := New(transport.NewClient(server.URL, "app_test"), q, testRetryConfig(), log.New(io.Discard, "", 0))

	// First batch fails and is persisted.
	if err := sender.SendMetrics(context.Background(), []metrics.Point{point("first")}); err != nil {
		t.Fatalf("SendMetrics(first): %v", err)
	}
	if q.Len() != 1 {
		t.Fatalf("queue = %d, want 1 after outage", q.Len())
	}

	// Backend recovers, but the backlog forces the next live batch behind it.
	down.Store(false)
	if err := sender.SendMetrics(context.Background(), []metrics.Point{point("second")}); err != nil {
		t.Fatalf("SendMetrics(second): %v", err)
	}
	if q.Len() != 2 {
		t.Fatalf("queue = %d, want 2 while backlog exists", q.Len())
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go sender.RunReplay(ctx)

	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		if q.Len() == 0 {
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	if q.Len() != 0 {
		t.Fatalf("replay did not drain the queue: %d batches remain", q.Len())
	}

	mu.Lock()
	got := append([]string(nil), delivered...)
	mu.Unlock()
	want := []string{"first", "second"}
	if len(got) != len(want) {
		t.Fatalf("delivered = %v, want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("delivery %d = %q, want %q (order must be oldest-first)", i, got[i], want[i])
		}
	}
}

func TestSenderReplayDropsPermanentFailure(t *testing.T) {
	var permanent atomic.Bool
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if permanent.Load() {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		http.Error(w, "down", http.StatusServiceUnavailable)
	}))
	defer server.Close()

	q := openTestQueue(t)
	sender := New(transport.NewClient(server.URL, "app_test"), q, testRetryConfig(), log.New(io.Discard, "", 0))

	if err := sender.SendMetrics(context.Background(), []metrics.Point{point("first")}); err != nil {
		t.Fatalf("SendMetrics: %v", err)
	}
	if q.Len() != 1 {
		t.Fatalf("queue = %d, want 1", q.Len())
	}
	permanent.Store(true)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go sender.RunReplay(ctx)

	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		if q.Len() == 0 {
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	if q.Len() != 0 {
		t.Fatalf("permanent replay failure must remove the batch, %d left", q.Len())
	}
}
