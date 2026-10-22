// Package buffer implements a bounded, durable on-disk queue for telemetry
// batches that could not be delivered. Each batch is stored as one JSON file
// written atomically (temp file + rename) and replayed oldest-first.
package buffer

import (
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

const (
	filePrefix = "batch-"
	fileSuffix = ".json"
	// tempPrefix marks files that are being written. A crash can leave them
	// behind; they are removed during the startup scan.
	tempPrefix = ".tmp-"
)

// ErrBatchTooLarge is returned when a single batch is larger than the whole
// buffer limit and therefore can never be persisted.
var ErrBatchTooLarge = errors.New("batch is larger than the buffer limit")

// Entry is the on-disk representation of one buffered batch. It contains only
// telemetry data: the API key is attached at send time and is never persisted.
type Entry struct {
	Kind  string          `json:"kind"`
	Count int             `json:"count"`
	Body  json.RawMessage `json:"body"`
}

// Stats is a snapshot of buffer counters.
type Stats struct {
	SizeBytes     int64
	QueuedBatches int64
	DroppedTotal  int64
}

type fileEntry struct {
	name    string
	size    int64
	modTime time.Time
}

// Queue is a bounded FIFO of batch files. All methods are safe for concurrent
// use.
type Queue struct {
	dir     string
	maxSize int64
	maxAge  time.Duration
	logger  *log.Logger
	now     func() time.Time

	enabled bool
	dropped atomic.Int64

	mu         sync.Mutex
	files      []fileEntry
	totalBytes int64
	lastSeq    int64
}

// Open prepares the configured buffer directory and loads any batches left by
// a previous run. When buffering is enabled but the directory is unusable
// (cannot be created, read or written), Open logs a clear error and returns a
// disabled queue so the agent keeps running in memory-only mode.
func Open(cfg config.BufferConfig, logger *log.Logger) *Queue {
	if logger == nil {
		logger = log.New(os.Stderr, "", log.LstdFlags|log.LUTC)
	}
	q := &Queue{
		dir:     cfg.Directory,
		maxSize: cfg.MaxSizeBytes(),
		maxAge:  cfg.MaxAge(),
		logger:  logger,
		now:     time.Now,
	}
	if !cfg.Enabled {
		return q
	}
	if strings.TrimSpace(cfg.Directory) == "" {
		logger.Print("error: buffer.directory is empty; disk buffering disabled, running in memory-only mode")
		return q
	}
	if err := os.MkdirAll(cfg.Directory, 0o700); err != nil {
		logger.Printf("error: cannot create buffer directory %s: %v; disk buffering disabled, running in memory-only mode",
			cfg.Directory, err)
		return q
	}
	if err := os.Chmod(cfg.Directory, 0o700); err != nil {
		logger.Printf("warning: cannot restrict permissions on buffer directory %s: %v", cfg.Directory, err)
	}
	if err := probeWritable(cfg.Directory); err != nil {
		logger.Printf("error: buffer directory %s is not writable: %v; disk buffering disabled, running in memory-only mode",
			cfg.Directory, err)
		return q
	}

	q.enabled = true
	if err := q.scan(); err != nil {
		logger.Printf("error: cannot scan buffer directory %s: %v; disk buffering disabled, running in memory-only mode",
			cfg.Directory, err)
		q.enabled = false
		return q
	}

	q.mu.Lock()
	expired := q.evictExpiredLocked(q.now())
	trimmed := q.evictSizeLocked()
	queued := len(q.files)
	q.mu.Unlock()
	if expired > 0 {
		logger.Printf("buffer: dropped %d expired batch(es) during startup", expired)
	}
	if trimmed > 0 {
		logger.Printf("buffer: dropped %d old batch(es) during startup to enforce max_size_mb", trimmed)
	}
	if queued > 0 {
		logger.Printf("buffer: resuming replay of %d queued batch(es) from %s", queued, cfg.Directory)
	}
	return q
}

// Enabled reports whether disk buffering is available.
func (q *Queue) Enabled() bool {
	return q != nil && q.enabled
}

// Directory returns the configured buffer directory.
func (q *Queue) Directory() string {
	if q == nil {
		return ""
	}
	return q.dir
}

func probeWritable(dir string) error {
	f, err := os.CreateTemp(dir, tempPrefix+"probe-*")
	if err != nil {
		return err
	}
	name := f.Name()
	_, writeErr := f.Write([]byte("ok"))
	closeErr := f.Close()
	removeErr := os.Remove(name)
	if writeErr != nil {
		return writeErr
	}
	if closeErr != nil {
		return closeErr
	}
	return removeErr
}

// scan loads valid batch files, removes temp/partial files and deletes corrupt
// files. Symlinks and non-regular entries are ignored and never followed.
func (q *Queue) scan() error {
	entries, err := os.ReadDir(q.dir)
	if err != nil {
		return err
	}

	var files []fileEntry
	var total int64
	var lastSeq int64

	for _, dirEntry := range entries {
		name := dirEntry.Name()
		path := filepath.Join(q.dir, name)

		if dirEntry.Type()&os.ModeSymlink != 0 {
			q.logger.Printf("warning: ignoring symlink in buffer directory: %s", path)
			continue
		}
		info, err := os.Lstat(path)
		if err != nil {
			q.logger.Printf("warning: cannot stat buffer entry %s: %v", path, err)
			continue
		}
		if info.Mode()&os.ModeSymlink != 0 {
			q.logger.Printf("warning: ignoring symlink in buffer directory: %s", path)
			continue
		}
		if !info.Mode().IsRegular() {
			continue
		}

		if strings.HasPrefix(name, tempPrefix) || strings.HasSuffix(name, ".tmp") {
			if err := os.Remove(path); err != nil {
				q.logger.Printf("warning: cannot remove partial buffer file %s: %v", path, err)
			}
			continue
		}
		if !strings.HasPrefix(name, filePrefix) || !strings.HasSuffix(name, fileSuffix) {
			continue
		}

		data, err := os.ReadFile(path)
		if err != nil {
			q.logger.Printf("warning: cannot read buffered batch %s: %v; removing it", path, err)
			_ = os.Remove(path)
			continue
		}
		var entry Entry
		if err := json.Unmarshal(data, &entry); err != nil || !validEntry(entry) {
			q.logger.Printf("warning: corrupt buffered batch %s: removing it", path)
			_ = os.Remove(path)
			continue
		}

		files = append(files, fileEntry{name: name, size: info.Size(), modTime: info.ModTime()})
		total += info.Size()
		if seq, ok := sequenceOf(name); ok && seq > lastSeq {
			lastSeq = seq
		}
	}

	sort.Slice(files, func(i, j int) bool { return files[i].name < files[j].name })

	q.mu.Lock()
	q.files = files
	q.totalBytes = total
	q.lastSeq = lastSeq
	q.mu.Unlock()
	return nil
}

func validEntry(entry Entry) bool {
	if strings.TrimSpace(entry.Kind) == "" {
		return false
	}
	if entry.Count < 0 {
		return false
	}
	return len(entry.Body) > 0 && json.Valid(entry.Body)
}

func sequenceOf(name string) (int64, bool) {
	trimmed := strings.TrimSuffix(strings.TrimPrefix(name, filePrefix), fileSuffix)
	seq, err := strconv.ParseInt(trimmed, 10, 64)
	if err != nil {
		return 0, false
	}
	return seq, true
}

// Enqueue atomically persists one batch. When the queue would exceed its size
// limit, the oldest files are dropped first. Enqueue returns ErrBatchTooLarge
// for a batch that cannot fit even in an empty buffer.
func (q *Queue) Enqueue(kind string, count int, body []byte) error {
	if q == nil || !q.enabled {
		return errors.New("disk buffer is disabled")
	}

	payload, err := json.Marshal(Entry{Kind: kind, Count: count, Body: body})
	if err != nil {
		return fmt.Errorf("encode buffer entry: %w", err)
	}
	size := int64(len(payload))

	q.mu.Lock()
	defer q.mu.Unlock()

	if q.maxSize > 0 && size > q.maxSize {
		q.dropped.Add(1)
		q.logger.Printf("error: %s batch (%d bytes) exceeds buffer.max_size_mb; dropping it", kind, size)
		return ErrBatchTooLarge
	}

	if expired := q.evictExpiredLocked(q.now()); expired > 0 {
		q.logger.Printf("buffer: dropped %d expired batch(es)", expired)
	}
	if trimmed := q.evictToFitLocked(size); trimmed > 0 {
		q.logger.Printf("buffer: dropped %d old batch(es) to stay within max_size_mb", trimmed)
	}

	seq := q.lastSeq + 1
	name := fmt.Sprintf("%s%020d%s", filePrefix, seq, fileSuffix)
	if err := q.writeAtomic(name, payload); err != nil {
		return err
	}

	q.lastSeq = seq
	q.files = append(q.files, fileEntry{name: name, size: size, modTime: q.now()})
	q.totalBytes += size
	return nil
}

func (q *Queue) writeAtomic(name string, payload []byte) error {
	tmp, err := os.CreateTemp(q.dir, tempPrefix+"*")
	if err != nil {
		return fmt.Errorf("create temp buffer file: %w", err)
	}
	tmpName := tmp.Name()
	cleanup := func() {
		_ = tmp.Close()
		_ = os.Remove(tmpName)
	}

	if err := tmp.Chmod(0o600); err != nil {
		q.logger.Printf("warning: cannot restrict permissions on %s: %v", tmpName, err)
	}
	if _, err := tmp.Write(payload); err != nil {
		cleanup()
		return fmt.Errorf("write buffer file: %w", err)
	}
	if err := tmp.Sync(); err != nil {
		cleanup()
		return fmt.Errorf("sync buffer file: %w", err)
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmpName)
		return fmt.Errorf("close buffer file: %w", err)
	}
	if err := os.Rename(tmpName, filepath.Join(q.dir, name)); err != nil {
		_ = os.Remove(tmpName)
		return fmt.Errorf("commit buffer file: %w", err)
	}
	return nil
}

// Next returns the oldest queued batch without removing it. Corrupt files found
// at runtime are removed and skipped. Remove must be called after a successful
// send.
func (q *Queue) Next() (Entry, string, bool) {
	if q == nil || !q.enabled {
		return Entry{}, "", false
	}

	q.mu.Lock()
	defer q.mu.Unlock()

	for len(q.files) > 0 {
		file := q.files[0]
		path := filepath.Join(q.dir, file.name)
		data, err := os.ReadFile(path)
		if err != nil {
			q.logger.Printf("warning: cannot read buffered batch %s: %v; removing it", path, err)
			q.removeAtLocked(0)
			continue
		}
		var entry Entry
		if err := json.Unmarshal(data, &entry); err != nil || !validEntry(entry) {
			q.logger.Printf("warning: corrupt buffered batch %s: removing it", path)
			q.removeAtLocked(0)
			continue
		}
		return entry, file.name, true
	}
	return Entry{}, "", false
}

// Remove deletes a batch file after it was delivered. Missing files are treated
// as already removed.
func (q *Queue) Remove(name string) bool {
	if q == nil || !q.enabled {
		return false
	}

	q.mu.Lock()
	defer q.mu.Unlock()

	for i, file := range q.files {
		if file.name != name {
			continue
		}
		if err := os.Remove(filepath.Join(q.dir, file.name)); err != nil && !os.IsNotExist(err) {
			q.logger.Printf("warning: cannot remove delivered buffer file %s: %v", file.name, err)
		}
		q.files = append(q.files[:i], q.files[i+1:]...)
		q.totalBytes -= file.size
		if q.totalBytes < 0 {
			q.totalBytes = 0
		}
		return true
	}
	return false
}

// EvictExpired drops batches older than max_age_hours. It is called
// periodically by the replay worker and before enqueueing a new batch.
func (q *Queue) EvictExpired() {
	if q == nil || !q.enabled {
		return
	}
	q.mu.Lock()
	dropped := q.evictExpiredLocked(q.now())
	q.mu.Unlock()
	if dropped > 0 {
		q.logger.Printf("buffer: dropped %d batch(es) older than max_age_hours", dropped)
	}
}

// EvictToFit drops the oldest files until the queue is below its size limit.
// It is exposed for maintenance and tests.
func (q *Queue) EvictToFit() {
	if q == nil || !q.enabled {
		return
	}
	q.mu.Lock()
	dropped := q.evictSizeLocked()
	q.mu.Unlock()
	if dropped > 0 {
		q.logger.Printf("buffer: dropped %d old batch(es) to stay within max_size_mb", dropped)
	}
}

// Len returns the number of queued batches.
func (q *Queue) Len() int {
	if q == nil || !q.enabled {
		return 0
	}
	q.mu.Lock()
	defer q.mu.Unlock()
	return len(q.files)
}

// Stats returns a consistent snapshot of queue counters.
func (q *Queue) Stats() Stats {
	if q == nil {
		return Stats{}
	}
	stats := Stats{DroppedTotal: q.dropped.Load()}
	if !q.enabled {
		return stats
	}
	q.mu.Lock()
	stats.SizeBytes = q.totalBytes
	stats.QueuedBatches = int64(len(q.files))
	q.mu.Unlock()
	return stats
}

// DroppedTotal returns the number of batches dropped by size/age eviction.
func (q *Queue) DroppedTotal() int64 {
	if q == nil {
		return 0
	}
	return q.dropped.Load()
}

func (q *Queue) evictToFitLocked(incoming int64) int {
	dropped := 0
	for len(q.files) > 0 && q.maxSize > 0 && q.totalBytes+incoming > q.maxSize {
		q.dropOldestLocked()
		dropped++
	}
	return dropped
}

func (q *Queue) evictSizeLocked() int {
	return q.evictToFitLocked(0)
}

func (q *Queue) evictExpiredLocked(now time.Time) int {
	if q.maxAge <= 0 {
		return 0
	}
	dropped := 0
	for len(q.files) > 0 {
		if now.Sub(q.files[0].modTime) <= q.maxAge {
			break
		}
		q.dropOldestLocked()
		dropped++
	}
	return dropped
}

func (q *Queue) dropOldestLocked() {
	if len(q.files) == 0 {
		return
	}
	file := q.files[0]
	q.files = q.files[1:]
	q.totalBytes -= file.size
	if q.totalBytes < 0 {
		q.totalBytes = 0
	}
	q.dropped.Add(1)
	if err := os.Remove(filepath.Join(q.dir, file.name)); err != nil && !os.IsNotExist(err) {
		q.logger.Printf("warning: cannot remove dropped buffer file %s: %v", file.name, err)
	}
}

func (q *Queue) removeAtLocked(index int) {
	if index < 0 || index >= len(q.files) {
		return
	}
	file := q.files[index]
	q.files = append(q.files[:index], q.files[index+1:]...)
	q.totalBytes -= file.size
	if q.totalBytes < 0 {
		q.totalBytes = 0
	}
	if err := os.Remove(filepath.Join(q.dir, file.name)); err != nil && !os.IsNotExist(err) {
		q.logger.Printf("warning: cannot remove buffer file %s: %v", file.name, err)
	}
}
