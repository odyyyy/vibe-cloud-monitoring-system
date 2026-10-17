package logs

import (
	"context"
	"fmt"
	"log"
	"os"
	"sync"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/transport"
)

// finalFlushTimeout bounds the shutdown flush so SIGTERM does not hang.
const finalFlushTimeout = 10 * time.Second

// Pipeline tails configured log files, batches parsed events in memory and
// forwards them to the cloud. Buffering is bounded: when the backend is
// unreachable the oldest events are dropped rather than growing without limit.
type Pipeline struct {
	logger     *log.Logger
	client     *Client
	state      *stateStore
	hostname   string
	batchSize  int
	flushEvery time.Duration
	pollEvery  time.Duration
	maxBuffer  int

	sources []*source

	mu      sync.Mutex
	buffer  []Event
	dropped int
	flushCh chan struct{}

	// warned is only touched by the single poll goroutine inside Run.
	warned map[string]bool
}

// NewPipeline builds a pipeline for cfg.Logs. It returns an error only for invalid
// configuration (an uncompilable level_pattern); a missing or corrupt state file is
// reported as a warning and treated as empty state.
func NewPipeline(cfg *config.Config, client *Client, logger *log.Logger) (*Pipeline, error) {
	state, err := loadState(cfg.StateFile)
	if err != nil {
		logger.Printf("warning: %v (starting with empty offsets)", err)
	}

	hostname, err := os.Hostname()
	if err != nil || hostname == "" {
		hostname = "unknown"
	}

	p := &Pipeline{
		logger:     logger,
		client:     client,
		state:      state,
		hostname:   hostname,
		batchSize:  cfg.LogBatchSize,
		flushEvery: time.Duration(cfg.LogFlushSeconds) * time.Second,
		pollEvery:  defaultPollInterval,
		maxBuffer:  cfg.LogBatchSize * 10,
		flushCh:    make(chan struct{}, 1),
		warned:     map[string]bool{},
	}

	for i, srcCfg := range cfg.Logs {
		s, err := newSource(srcCfg)
		if err != nil {
			return nil, fmt.Errorf("logs[%d].level_pattern: %w", i, err)
		}
		if offset, ok := state.offset(srcCfg.Path); ok {
			s.offset = offset
			s.hasState = true
		}
		p.sources = append(p.sources, s)
	}

	return p, nil
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
	if len(p.buffer) == 0 {
		p.mu.Unlock()
		return
	}
	batch := p.buffer
	p.buffer = make([]Event, 0, p.batchSize)
	dropped := p.dropped
	p.dropped = 0
	p.mu.Unlock()

	if dropped > 0 {
		p.logger.Printf("dropped %d log events: local buffer limit reached", dropped)
	}

	transport.Retry(ctx, p.logger, fmt.Sprintf("%d logs", len(batch)), func(sendCtx context.Context) error {
		return p.client.Send(sendCtx, batch)
	})
}

func (p *Pipeline) finalFlush() {
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
