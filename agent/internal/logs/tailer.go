package logs

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

const (
	defaultPollInterval = 1 * time.Second
	// maxReadPerPoll bounds how many bytes are read from one file per poll, so a
	// sudden large file cannot exhaust memory.
	maxReadPerPoll = 1 << 20 // 1 MiB
	// maxPendingLineBytes bounds a single line that never receives a newline.
	maxPendingLineBytes = 64 << 10 // 64 KiB
)

// source is the mutable tailing state for one configured file.
type source struct {
	cfg     config.LogSource
	levelRe *regexp.Regexp

	offset      int64
	pending     []byte
	lastInfo    os.FileInfo
	hasState    bool
	initialized bool
}

// newSource builds the tailing state for a configured log source, resolving the
// optional level regex and the service-name default.
func newSource(cfg config.LogSource) (*source, error) {
	var levelRe *regexp.Regexp
	if strings.TrimSpace(cfg.LevelPattern) != "" {
		compiled, err := regexp.Compile(cfg.LevelPattern)
		if err != nil {
			return nil, err
		}
		levelRe = compiled
	}
	if strings.TrimSpace(cfg.ServiceName) == "" {
		cfg.ServiceName = filepath.Base(cfg.Path)
	}
	return &source{cfg: cfg, levelRe: levelRe}, nil
}

// pollSource reads any new bytes from a single file, handling rotation and
// truncation, and persists the committed offset.
func (p *Pipeline) pollSource(s *source) {
	fi, err := os.Stat(s.cfg.Path)
	if err != nil {
		// Missing file (yet). Keep the stored offset and retry on the next poll.
		return
	}
	if fi.IsDir() {
		p.warnOnce(s.cfg.Path, fmt.Sprintf("configured log path %s is a directory; skipping", s.cfg.Path))
		return
	}

	switch {
	case !s.initialized:
		// First successful stat: tail from the end unless we have a saved offset, so
		// an existing file is not re-ingested from the beginning. Runtime-discovered
		// pod files opt into reading existing content because the container may have
		// logged startup lines before the watch event arrived.
		if !s.hasState && !s.cfg.FromBeginning {
			s.offset = fi.Size()
		}
		s.lastInfo = fi
		s.initialized = true
	case s.lastInfo != nil && !os.SameFile(s.lastInfo, fi):
		// The path now points at a different file: rotated (app.log -> app.log.1 and
		// a new app.log). Start reading the new file from the beginning.
		p.logger.Printf("log rotation detected: %s", s.cfg.Path)
		s.offset = 0
		s.pending = nil
		s.lastInfo = fi
	case fi.Size() < s.offset:
		// Same file truncated in place (copy-truncate rotation).
		s.offset = 0
		s.pending = nil
	}

	if fi.Size() <= s.offset {
		return
	}

	if err := p.readFrom(s, fi.Size()); err != nil {
		p.warnOnce(s.cfg.Path, fmt.Sprintf("read %s: %v", s.cfg.Path, err))
	}
}

func (p *Pipeline) readFrom(s *source, size int64) error {
	f, err := os.Open(s.cfg.Path)
	if err != nil {
		return err
	}
	defer f.Close()

	if _, err := f.Seek(s.offset, io.SeekStart); err != nil {
		return err
	}

	remaining := size - s.offset
	if remaining > maxReadPerPoll {
		remaining = maxReadPerPoll
	}
	buf := make([]byte, remaining)
	n, err := io.ReadFull(f, buf)
	if err != nil && err != io.ErrUnexpectedEOF && err != io.EOF {
		return err
	}
	buf = buf[:n]

	s.offset += int64(n)
	s.pending = append(s.pending, buf...)

	p.drainLines(s)

	// Guard against a line that never ends: emit it truncated and move on.
	if len(s.pending) > maxPendingLineBytes {
		line := string(s.pending)
		s.pending = nil
		p.emitLine(s, line)
	}

	// Persist only fully emitted lines; an incomplete trailing line is re-read after
	// a restart instead of being lost.
	return p.state.set(s.cfg.Path, s.offset-int64(len(s.pending)))
}

func (p *Pipeline) drainLines(s *source) {
	for {
		idx := bytes.IndexByte(s.pending, '\n')
		if idx < 0 {
			return
		}
		line := string(s.pending[:idx])
		s.pending = s.pending[idx+1:]
		p.emitLine(s, line)
	}
}

func (p *Pipeline) emitLine(s *source, raw string) {
	raw = strings.TrimRight(raw, "\r")
	if strings.TrimSpace(raw) == "" {
		return
	}
	if event, ok := p.buildEvent(s, raw); ok {
		p.emit(event)
	}
}

// buildEvent parses a single line into an Event. Redaction is applied before the
// message is buffered so secrets never leave the host.
func (p *Pipeline) buildEvent(s *source, raw string) (Event, bool) {
	if s.cfg.EffectiveFormat() != config.LogFormatText {
		if parsed, ok := parseJSONLog(raw, s.levelRe); ok {
			return p.buildJSONEvent(s, raw, parsed)
		}
	}
	return p.buildTextEvent(s, raw)
}

func (p *Pipeline) buildJSONEvent(s *source, raw string, parsed jsonLog) (Event, bool) {
	message := parsed.Message
	if !parsed.HasMessage || strings.TrimSpace(message) == "" {
		message = raw
	}
	message = truncateRunes(redact(message), maxMessageRunes)
	if strings.TrimSpace(message) == "" {
		return Event{}, false
	}

	timestamp := parsed.Timestamp
	if !parsed.HasTimestamp {
		if extracted, ok := extractTimestamp(raw); ok {
			timestamp = extracted
		} else {
			timestamp = time.Now().UTC()
		}
	}

	return Event{
		Level:     parsed.Level,
		Message:   message,
		Timestamp: timestamp.UTC(),
		Context:   buildEventContext(s.cfg, p.hostname, parsed.Fields),
	}, true
}

func (p *Pipeline) buildTextEvent(s *source, raw string) (Event, bool) {
	message := truncateRunes(redact(raw), maxMessageRunes)
	if strings.TrimSpace(message) == "" {
		return Event{}, false
	}

	timestamp, ok := extractTimestamp(raw)
	if !ok {
		timestamp = time.Now().UTC()
	}

	return Event{
		Level:     inferLevel(s.levelRe, raw),
		Message:   message,
		Timestamp: timestamp.UTC(),
		Context:   buildEventContext(s.cfg, p.hostname, nil),
	}, true
}
