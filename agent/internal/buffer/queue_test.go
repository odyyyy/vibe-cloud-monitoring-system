package buffer

import (
	"io"
	"log"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
)

func discardLogger() *log.Logger {
	return log.New(io.Discard, "", 0)
}

func testConfig(dir string) config.BufferConfig {
	return config.BufferConfig{
		Enabled:     true,
		Directory:   dir,
		MaxSizeMB:   1,
		MaxAgeHours: 24,
	}
}

func TestQueuePreservesOrderAcrossRestart(t *testing.T) {
	dir := t.TempDir()
	q := Open(testConfig(dir), discardLogger())
	if !q.Enabled() {
		t.Fatal("queue should be enabled")
	}

	for _, body := range []string{`{"n":1}`, `{"n":2}`, `{"n":3}`} {
		if err := q.Enqueue("logs", 1, []byte(body)); err != nil {
			t.Fatalf("Enqueue: %v", err)
		}
	}

	// Reopen to simulate an agent restart: files must be replayed oldest-first.
	q = Open(testConfig(dir), discardLogger())
	if got := q.Len(); got != 3 {
		t.Fatalf("queued = %d, want 3", got)
	}
	for i, want := range []string{`{"n":1}`, `{"n":2}`, `{"n":3}`} {
		entry, name, ok := q.Next()
		if !ok {
			t.Fatalf("Next %d: queue empty", i)
		}
		if string(entry.Body) != want {
			t.Errorf("batch %d body = %s, want %s", i, entry.Body, want)
		}
		if entry.Kind != "logs" || entry.Count != 1 {
			t.Errorf("batch %d metadata = %+v", i, entry)
		}
		if !q.Remove(name) {
			t.Fatalf("Remove(%s) failed", name)
		}
	}
	if _, _, ok := q.Next(); ok {
		t.Fatal("queue should be empty")
	}
	if stats := q.Stats(); stats.SizeBytes != 0 || stats.QueuedBatches != 0 {
		t.Errorf("stats after drain = %+v", stats)
	}
}

func TestQueueEvictsOldestWhenSizeExceeded(t *testing.T) {
	dir := t.TempDir()
	cfg := testConfig(dir)
	q := Open(cfg, discardLogger())

	body := make([]byte, 600*1024)
	for i := range body {
		body[i] = 'x'
	}
	payload := append([]byte(`{"data":"`), body...)
	payload = append(payload, []byte(`"}`)...)

	for i := 0; i < 3; i++ {
		if err := q.Enqueue("logs", 1, payload); err != nil {
			t.Fatalf("Enqueue %d: %v", i, err)
		}
	}

	stats := q.Stats()
	if stats.SizeBytes > cfg.MaxSizeBytes() {
		t.Errorf("size = %d bytes, limit = %d", stats.SizeBytes, cfg.MaxSizeBytes())
	}
	if stats.QueuedBatches != 1 {
		t.Errorf("queued = %d, want 1", stats.QueuedBatches)
	}
	if stats.DroppedTotal != 2 {
		t.Errorf("dropped = %d, want 2", stats.DroppedTotal)
	}
}

func TestQueueRejectsBatchLargerThanLimit(t *testing.T) {
	dir := t.TempDir()
	cfg := testConfig(dir)
	q := Open(cfg, discardLogger())

	payload := []byte(`{"data":"` + strings.Repeat("x", 2*1024*1024) + `"}`)

	if err := q.Enqueue("metrics", 5, payload); err != ErrBatchTooLarge {
		t.Fatalf("Enqueue error = %v, want ErrBatchTooLarge", err)
	}
	if stats := q.Stats(); stats.QueuedBatches != 0 || stats.DroppedTotal != 1 {
		t.Errorf("stats = %+v", stats)
	}
}

func TestQueueEvictsExpiredBatches(t *testing.T) {
	dir := t.TempDir()
	cfg := testConfig(dir)
	cfg.MaxAgeHours = 1
	q := Open(cfg, discardLogger())

	if err := q.Enqueue("logs", 1, []byte(`{"n":1}`)); err != nil {
		t.Fatalf("Enqueue: %v", err)
	}
	if _, _, ok := q.Next(); !ok {
		t.Fatal("expected a queued batch")
	}

	// Move the queue clock forward instead of changing file times so the test
	// exercises the same in-memory age comparison the replay worker uses.
	q.now = func() time.Time { return time.Now().Add(2 * time.Hour) }

	q.EvictExpired()

	if q.Len() != 0 {
		t.Fatalf("queued = %d, want 0", q.Len())
	}
	if got := q.DroppedTotal(); got != 1 {
		t.Errorf("dropped = %d, want 1", got)
	}
}

func TestQueueCleansCorruptAndPartialFilesOnOpen(t *testing.T) {
	dir := t.TempDir()
	corrupt := filepath.Join(dir, "batch-00000000000000000001.json")
	if err := os.WriteFile(corrupt, []byte("{not json"), 0o600); err != nil {
		t.Fatalf("write corrupt: %v", err)
	}
	partial := filepath.Join(dir, ".tmp-partial")
	if err := os.WriteFile(partial, []byte("half"), 0o600); err != nil {
		t.Fatalf("write partial: %v", err)
	}
	valid := filepath.Join(dir, "batch-00000000000000000002.json")
	entry := `{"kind":"metrics","count":2,"body":{"metrics":[]}}`
	if err := os.WriteFile(valid, []byte(entry), 0o600); err != nil {
		t.Fatalf("write valid: %v", err)
	}

	q := Open(testConfig(dir), discardLogger())
	if got := q.Len(); got != 1 {
		t.Fatalf("queued = %d, want 1", got)
	}
	if _, err := os.Stat(corrupt); !os.IsNotExist(err) {
		t.Error("corrupt file was not removed")
	}
	if _, err := os.Stat(partial); !os.IsNotExist(err) {
		t.Error("partial temp file was not removed")
	}

	// A file corrupted while the agent runs is skipped and removed.
	_, name, ok := q.Next()
	if !ok {
		t.Fatal("expected the valid batch")
	}
	corrupted := filepath.Join(dir, name)
	if err := os.WriteFile(corrupted, []byte("garbage"), 0o600); err != nil {
		t.Fatalf("corrupt running file: %v", err)
	}
	if _, _, ok := q.Next(); ok {
		t.Fatal("corrupt batch should have been skipped")
	}
	if _, err := os.Stat(corrupted); !os.IsNotExist(err) {
		t.Error("runtime corrupt file was not removed")
	}
}

func TestQueueIgnoresSymlinks(t *testing.T) {
	dir := t.TempDir()
	target := filepath.Join(t.TempDir(), "outside.json")
	entry := `{"kind":"logs","count":1,"body":{"events":[]}}`
	if err := os.WriteFile(target, []byte(entry), 0o600); err != nil {
		t.Fatalf("write target: %v", err)
	}
	link := filepath.Join(dir, "batch-00000000000000000001.json")
	if err := os.Symlink(target, link); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}

	q := Open(testConfig(dir), discardLogger())
	if got := q.Len(); got != 0 {
		t.Fatalf("queued = %d, want 0 (symlink must be ignored)", got)
	}
	if _, err := os.Stat(target); err != nil {
		t.Errorf("symlink target should not be touched: %v", err)
	}
}

func TestQueuePermissions(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("POSIX permissions are not enforced on Windows")
	}
	dir := filepath.Join(t.TempDir(), "buffer")
	q := Open(testConfig(dir), discardLogger())
	if err := q.Enqueue("logs", 1, []byte(`{"n":1}`)); err != nil {
		t.Fatalf("Enqueue: %v", err)
	}

	dirInfo, err := os.Stat(dir)
	if err != nil {
		t.Fatalf("stat dir: %v", err)
	}
	if perm := dirInfo.Mode().Perm(); perm != 0o700 {
		t.Errorf("directory mode = %o, want 700", perm)
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatalf("read dir: %v", err)
	}
	if len(entries) != 1 {
		t.Fatalf("entries = %d, want 1", len(entries))
	}
	fileInfo, err := entries[0].Info()
	if err != nil {
		t.Fatalf("stat file: %v", err)
	}
	if perm := fileInfo.Mode().Perm(); perm != 0o600 {
		t.Errorf("file mode = %o, want 600", perm)
	}
}

func TestQueueConcurrentUse(t *testing.T) {
	dir := t.TempDir()
	q := Open(config.BufferConfig{Enabled: true, Directory: dir, MaxSizeMB: 64, MaxAgeHours: 24}, discardLogger())

	const workers = 8
	const perWorker = 20
	var wg sync.WaitGroup
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < perWorker; j++ {
				if err := q.Enqueue("logs", 1, []byte(`{"n":1}`)); err != nil {
					t.Errorf("Enqueue: %v", err)
				}
				_ = q.Stats()
			}
		}()
	}

	stop := make(chan struct{})
	var reader sync.WaitGroup
	reader.Add(1)
	go func() {
		defer reader.Done()
		for {
			select {
			case <-stop:
				return
			default:
			}
			if _, name, ok := q.Next(); ok {
				q.Remove(name)
			}
		}
	}()

	wg.Wait()
	close(stop)
	reader.Wait()

	if stats := q.Stats(); stats.QueuedBatches > workers*perWorker {
		t.Errorf("queued = %d, exceeds enqueued total", stats.QueuedBatches)
	}
}
