// Package forward sends telemetry batches with retry classification and a
// bounded disk buffer. Transient failures (network, 5xx, 429) are retried with
// exponential backoff and then persisted; permanent rejections (400, 401, 403,
// 413) are logged and dropped so they can never fill the disk.
package forward

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"github.com/vibemonitoring/agent/internal/buffer"
	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/logs"
	"github.com/vibemonitoring/agent/internal/metrics"
	"github.com/vibemonitoring/agent/internal/transport"
)

const (
	// maxLiveAttempts bounds how long live telemetry waits for the backend
	// before the batch is persisted. Replay then takes over in the background.
	maxLiveAttempts = 3
	// replayInterval is the minimum time between replay sends, bounding replay
	// to five batches per second so it cannot starve live traffic.
	replayInterval = 200 * time.Millisecond
	// ageMaintenanceInterval controls how often expired batches are evicted even
	// while the replay worker is backing off.
	ageMaintenanceInterval = 30 * time.Second
	// authReportInterval suppresses repeated 401/403 log lines so a revoked key
	// does not spam the log once per batch.
	authReportInterval = time.Minute
)

// Kind identifies the ingestion endpoint a buffered batch belongs to.
type Kind string

const (
	KindLogs    Kind = "logs"
	KindMetrics Kind = "metrics"
)

type logBatch struct {
	Events []logs.Event `json:"events"`
}

type metricBatch struct {
	Metrics []metrics.Point `json:"metrics"`
}

// Sender forwards log and metric batches through one shared retry/buffer path.
// It is safe for concurrent use.
type Sender struct {
	http    *transport.Client
	queue   *buffer.Queue
	logger  *log.Logger
	initial time.Duration
	max     time.Duration
	jitter  bool

	retries  atomic.Int64
	failures atomic.Int64

	authMu         sync.Mutex
	lastAuthReport time.Time
}

// New creates a sender. queue may be nil when disk buffering is disabled.
func New(client *transport.Client, queue *buffer.Queue, retry config.RetryConfig, logger *log.Logger) *Sender {
	if logger == nil {
		logger = log.New(os.Stderr, "", log.LstdFlags|log.LUTC)
	}
	return &Sender{
		http:    client,
		queue:   queue,
		logger:  logger,
		initial: retry.InitialBackoff(),
		max:     retry.MaxBackoff(),
		jitter:  retry.Jitter,
	}
}

// Send implements the log pipeline batch sender interface. The stored payload
// is exactly the ingestion request body, so the API key is never persisted.
func (s *Sender) Send(ctx context.Context, events []logs.Event) error {
	return s.send(ctx, KindLogs, len(events), logBatch{Events: events})
}

// SendMetrics sends a host/custom metric batch through the same retry and
// buffer path.
func (s *Sender) SendMetrics(ctx context.Context, points []metrics.Point) error {
	return s.send(ctx, KindMetrics, len(points), metricBatch{Metrics: points})
}

func (s *Sender) send(ctx context.Context, kind Kind, count int, payload any) error {
	path, ok := pathFor(kind)
	if !ok {
		s.failures.Add(1)
		return fmt.Errorf("unknown telemetry kind %q", kind)
	}

	body, err := json.Marshal(payload)
	if err != nil {
		s.failures.Add(1)
		return fmt.Errorf("encode %s batch: %w", kind, err)
	}

	// If a backlog already exists, enqueue instead of sending directly so live
	// data does not overtake older buffered batches. The replay worker drains
	// both in order.
	if s.queue != nil && s.queue.Enabled() && s.queue.Len() > 0 {
		return s.persist(kind, count, body)
	}

	for attempt := 0; attempt < maxLiveAttempts; attempt++ {
		if err := ctx.Err(); err != nil {
			return s.persist(kind, count, body)
		}

		err := s.http.PostRaw(ctx, path, body)
		if err == nil {
			return nil
		}

		if !transport.IsRetryable(err) {
			s.reportPermanent(kind, count, err)
			return err
		}
		if ctx.Err() != nil {
			break
		}
		if attempt == maxLiveAttempts-1 {
			break
		}

		wait := transport.WaitDelay(s.initial, s.max, attempt, s.jitter, transport.RetryAfter(err))
		s.retries.Add(1)
		s.logger.Printf("sending %d %s failed (attempt %d/%d): %v; retrying in %s",
			count, kind, attempt+1, maxLiveAttempts, err, wait)
		if !sleep(ctx, wait) {
			break
		}
	}

	return s.persist(kind, count, body)
}

func (s *Sender) persist(kind Kind, count int, body []byte) error {
	if s.queue == nil || !s.queue.Enabled() {
		s.failures.Add(1)
		s.logger.Printf("error: cannot deliver %d %s and disk buffering is unavailable; batch dropped",
			count, kind)
		return fmt.Errorf("%s batch dropped: disk buffering unavailable", kind)
	}
	if err := s.queue.Enqueue(string(kind), count, body); err != nil {
		s.failures.Add(1)
		if err != buffer.ErrBatchTooLarge {
			s.logger.Printf("error: cannot persist %d %s to the disk buffer: %v; batch dropped",
				count, kind, err)
		}
		return err
	}
	s.logger.Printf("buffered %d %s for retry (%d queued)", count, kind, s.queue.Len())
	return nil
}

func (s *Sender) reportPermanent(kind Kind, count int, err error) {
	s.failures.Add(1)
	if transport.IsAuthFailure(err) {
		s.authMu.Lock()
		now := time.Now()
		report := s.lastAuthReport.IsZero() || now.Sub(s.lastAuthReport) >= authReportInterval
		if report {
			s.lastAuthReport = now
		}
		s.authMu.Unlock()
		if report {
			s.logger.Printf("error: backend rejected the API key while sending %s (%v). "+
				"Check AGENT_API_KEY: it is probably wrong or revoked. The batch was dropped; "+
				"auth errors will be suppressed for %s", kind, err, authReportInterval)
		}
		return
	}
	s.logger.Printf("error: dropping %d %s: backend permanently rejected the batch: %v", count, kind, err)
}

// RunReplay sends buffered batches oldest-first until ctx is cancelled. It is
// rate limited and uses exponential backoff on transient failures, leaving the
// batch on disk until a 2xx response is received.
func (s *Sender) RunReplay(ctx context.Context) {
	if s.queue == nil || !s.queue.Enabled() {
		return
	}
	s.logger.Printf("buffer replay worker started: %d queued batch(es), dir=%s",
		s.queue.Len(), s.queue.Directory())

	timer := time.NewTimer(time.Millisecond)
	ageTicker := time.NewTicker(ageMaintenanceInterval)
	defer timer.Stop()
	defer ageTicker.Stop()

	attempt := 0
	for {
		select {
		case <-ctx.Done():
			return
		case <-ageTicker.C:
			s.queue.EvictExpired()
			continue
		case <-timer.C:
		}

		entry, name, ok := s.queue.Next()
		if !ok {
			attempt = 0
			timer.Reset(replayInterval)
			continue
		}

		path, known := pathFor(Kind(entry.Kind))
		if !known {
			s.reportPermanent(Kind(entry.Kind), entry.Count,
				fmt.Errorf("unknown buffered batch kind %q", entry.Kind))
			s.queue.Remove(name)
			attempt = 0
			timer.Reset(replayInterval)
			continue
		}

		err := s.http.PostRaw(ctx, path, entry.Body)
		if err == nil {
			s.queue.Remove(name)
			attempt = 0
			timer.Reset(replayInterval)
			continue
		}
		if ctx.Err() != nil {
			return
		}
		if !transport.IsRetryable(err) {
			s.reportPermanent(Kind(entry.Kind), entry.Count, err)
			s.queue.Remove(name)
			attempt = 0
			timer.Reset(replayInterval)
			continue
		}

		wait := transport.WaitDelay(s.initial, s.max, attempt, s.jitter, transport.RetryAfter(err))
		s.retries.Add(1)
		if attempt < 32 {
			attempt++
		}
		s.logger.Printf("buffer replay failed for %s: %v; retrying in %s", name, err, wait)
		timer.Reset(wait)
	}
}

// RetriesTotal is the number of transient failures that triggered a retry.
func (s *Sender) RetriesTotal() int64 {
	return s.retries.Load()
}

// FailuresTotal is the number of batches dropped without being delivered or
// buffered successfully.
func (s *Sender) FailuresTotal() int64 {
	return s.failures.Load()
}

// BufferStats returns current disk queue counters.
func (s *Sender) BufferStats() buffer.Stats {
	if s.queue == nil {
		return buffer.Stats{}
	}
	return s.queue.Stats()
}

func pathFor(kind Kind) (string, bool) {
	switch kind {
	case KindLogs:
		return "/api/v1/ingest/logs", true
	case KindMetrics:
		return "/api/v1/ingest/metrics", true
	default:
		return "", false
	}
}

func sleep(ctx context.Context, d time.Duration) bool {
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}
