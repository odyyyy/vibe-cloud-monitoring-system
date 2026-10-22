package logs

import (
	"context"
	"fmt"
	"log"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

// finalFlushTimeout bounds the shutdown flush so SIGTERM does not hang.
const finalFlushTimeout = 10 * time.Second

// maxPendingSourceUpdates bounds the queue between pod discovery and the poll
// goroutine. Exceeding it means discovery is producing events faster than the
// pipeline can apply them, which only happens in pathological clusters.
const maxPendingSourceUpdates = 1024

// sourceUpdate is a dynamic source change produced by pod discovery. Updates are
// applied on the Run goroutine so polling state is never accessed concurrently.
type sourceUpdate struct {
	add    bool
	source config.LogSource
	path   string
}

// BatchSender is the forwarding path used by the pipeline. The production
// implementation adds retries and a disk buffer; tests can supply a fake.
type BatchSender interface {
	Send(ctx context.Context, events []Event) error
}

// Pipeline tails configured log files, batches parsed events in memory and
// forwards them to the cloud. Buffering is bounded: when the backend is
// unreachable the oldest events are dropped rather than growing without limit.
type Pipeline struct {
	logger      *log.Logger
	client      BatchSender
	state       *stateStore
	hostname    string
	batchSize   int
	flushEvery  time.Duration
	pollEvery   time.Duration
	maxBuffer   int
	throttleCfg config.LogThrottlingConfig

	sources     []*source
	sourceIndex map[string]*source
	updates     chan sourceUpdate

	mu        sync.Mutex
	buffer    []Event
	dropped   int
	throttled int
	flushCh   chan struct{}

	// droppedTotal counts events dropped by throttling since startup. It is read
	// by the metric collection goroutine, so it is atomic.
	droppedTotal atomic.Int64

	// warned is only touched by the single poll goroutine inside Run.
	warned map[string]bool
}

// NewPipeline builds a pipeline for cfg.Logs. It returns an error only for invalid
// configuration (an uncompilable level_pattern); a missing or corrupt state file is
// reported as a warning and treated as empty state.
func NewPipeline(cfg *config.Config, client BatchSender, logger *log.Logger) (*Pipeline, error) {
	state, err := loadState(cfg.StateFile)
	if err != nil {
		logger.Printf("warning: %v (starting with empty offsets)", err)
	}

	hostname, err := os.Hostname()
	if err != nil || hostname == "" {
		hostname = "unknown"
	}

	p := &Pipeline{
		logger:      logger,
		client:      client,
		state:       state,
		hostname:    hostname,
		batchSize:   cfg.LogBatchSize,
		flushEvery:  time.Duration(cfg.LogFlushSeconds) * time.Second,
		pollEvery:   defaultPollInterval,
		maxBuffer:   cfg.LogBatchSize * 10,
		throttleCfg: cfg.LogThrottling,
		flushCh:     make(chan struct{}, 1),
		updates:     make(chan sourceUpdate, maxPendingSourceUpdates),
		sourceIndex: map[string]*source{},
		warned:      map[string]bool{},
	}

	for i, srcCfg := range cfg.Logs {
		s, err := newSource(srcCfg)
		if err != nil {
			return nil, fmt.Errorf("logs[%d].level_pattern: %w", i, err)
		}
		s.throttle = newThrottler(cfg.LogThrottling)
		if offset, ok := state.offset(srcCfg.Path); ok {
			s.offset = offset
			s.hasState = true
		}
		p.sources = append(p.sources, s)
		p.sourceIndex[srcCfg.Path] = s
	}

	return p, nil
}

// AddSource registers a log source discovered at runtime (for example a pod
// container). It is safe to call from another goroutine; the update is applied
// by Run. Adding a path twice is a no-op.
func (p *Pipeline) AddSource(src config.LogSource) {
	p.enqueueUpdate(sourceUpdate{add: true, source: src})
}

// RemoveSource stops tailing the file at path and drops its persisted offset.
// The update is applied by Run.
func (p *Pipeline) RemoveSource(path string) {
	p.enqueueUpdate(sourceUpdate{add: false, path: path})
}

func (p *Pipeline) enqueueUpdate(u sourceUpdate) {
	select {
	case p.updates <- u:
	default:
		label := u.path
		if u.add {
			label = u.source.Path
		}
		p.logger.Printf("warning: log source update queue is full; dropping update for %s", label)
	}
}

func (p *Pipeline) applySourceUpdate(u sourceUpdate) {
	if u.add {
		p.addSource(u.source)
		return
	}
	p.removeSource(u.path)
}

func (p *Pipeline) addSource(cfg config.LogSource) {
	if _, exists := p.sourceIndex[cfg.Path]; exists {
		return
	}
	s, err := newSource(cfg)
	if err != nil {
		p.logger.Printf("warning: cannot tail %s: %v", cfg.Path, err)
		return
	}
	s.throttle = newThrottler(p.throttleCfg)
	if offset, ok := p.state.offset(cfg.Path); ok {
		s.offset = offset
		s.hasState = true
	}
	p.sources = append(p.sources, s)
	p.sourceIndex[cfg.Path] = s
	p.logger.Printf("log source added: %s", cfg.Path)
}

func (p *Pipeline) removeSource(path string) {
	s, exists := p.sourceIndex[path]
	if !exists {
		return
	}
	delete(p.sourceIndex, path)
	for i, candidate := range p.sources {
		if candidate == s {
			p.sources = append(p.sources[:i], p.sources[i+1:]...)
			break
		}
	}
	// No file descriptor is kept between polls, so dropping the source releases
	// the file immediately on the next iteration.
	if err := p.state.remove(path); err != nil {
		p.logger.Printf("warning: remove state for %s: %v", path, err)
	}
	p.logger.Printf("log source removed: %s", path)
}

// Run polls the configured files and flushes batches until ctx is cancelled, then
// performs one bounded final flush. It blocks until finished.
func (p *Pipeline) Run(ctx context.Context) {
	p.logger.Printf("log collection enabled: %d source(s), batch=%d, flush=%s, state=%s",
		len(p.sources), p.batchSize, p.flushEvery, p.state.path)

	pollTicker := time.NewTicker(p.pollEvery)
	flushTicker := time.NewTicker(p.flushEvery)
	defer pollTicker.Stop()
	defer flushTicker.Stop()

	for {
		select {
		case <-ctx.Done():
			p.finalFlush()
			return
		case <-pollTicker.C:
			for _, s := range p.sources {
				p.pollSource(s)
			}
		case <-flushTicker.C:
			p.flush(ctx)
		case <-p.flushCh:
			p.flush(ctx)
		case update := <-p.updates:
			p.applySourceUpdate(update)
		}
	}
}

func (p *Pipeline) emit(event Event) {
	p.mu.Lock()
	if p.maxBuffer > 0 && len(p.buffer) >= p.maxBuffer {
		p.buffer = p.buffer[1:]
		p.dropped++
	}
	p.buffer = append(p.buffer, event)
	reached := len(p.buffer) >= p.batchSize
	p.mu.Unlock()

	if reached {
		p.signalFlush()
	}
}

func (p *Pipeline) signalFlush() {
	select {
	case p.flushCh <- struct{}{}:
	default:
	}
}

// flush drains the current buffer and sends it, retrying with backoff until it is
// accepted or ctx is cancelled.
func (p *Pipeline) flush(ctx context.Context) {
	p.mu.Lock()
	throttled := p.throttled
	p.throttled = 0
	if len(p.buffer) == 0 {
		p.mu.Unlock()
		if throttled > 0 {
			p.logger.Printf("throttled %d log events: rate limit exceeded", throttled)
		}
		return
	}
	batch := p.buffer
	p.buffer = make([]Event, 0, p.batchSize)
	dropped := p.dropped
	p.dropped = 0
	p.mu.Unlock()

	if throttled > 0 {
		p.logger.Printf("throttled %d log events: rate limit exceeded", throttled)
	}
	if dropped > 0 {
		p.logger.Printf("dropped %d log events: local buffer limit reached", dropped)
	}

	if err := p.client.Send(ctx, batch); err != nil {
		p.logger.Printf("log batch send failed: %v", err)
	}
}

// DroppedTotal returns the cumulative number of log events dropped by agent-side
// throttling since startup. It is safe to call from another goroutine.
func (p *Pipeline) DroppedTotal() int64 {
	return p.droppedTotal.Load()
}

func (p *Pipeline) finalFlush() {
	// Apply updates that were queued right before shutdown, so a pod discovered
	// in the last second is still polled once.
drain:
	for {
		select {
		case update := <-p.updates:
			p.applySourceUpdate(update)
		default:
			break drain
		}
	}

	// One last read so lines written right before shutdown are not lost.
	for _, s := range p.sources {
		p.pollSource(s)
	}
	ctx, cancel := context.WithTimeout(context.Background(), finalFlushTimeout)
	defer cancel()
	p.flush(ctx)
}

func (p *Pipeline) warnOnce(key, msg string) {
	if p.warned[key] {
		return
	}
	p.warned[key] = true
	p.logger.Printf("warning: %s", msg)
}
