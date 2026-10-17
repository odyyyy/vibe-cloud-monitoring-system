package logs

import (
	"io"
	"log"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/transport"
)

func discardLogger() *log.Logger {
	return log.New(io.Discard, "", 0)
}

func newTestPipeline(t *testing.T, stateFile string, sources ...config.LogSource) *Pipeline {
	t.Helper()
	cfg := &config.Config{
		BackendURL:      "http://localhost:8080",
		APIKey:          "app_test",
		Logs:            sources,
		LogBatchSize:    500,
		LogFlushSeconds: 5,
		StateFile:       stateFile,
	}
	p, err := NewPipeline(cfg, NewClient(transport.NewClient(cfg.BackendURL, cfg.APIKey)), discardLogger())
	if err != nil {
		t.Fatalf("NewPipeline: %v", err)
	}
	return p
}

func appendLine(t *testing.T, path, line string) {
	t.Helper()
	f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600)
	if err != nil {
		t.Fatalf("open append: %v", err)
	}
	defer f.Close()
	if _, err := f.WriteString(line); err != nil {
		t.Fatalf("append: %v", err)
	}
}

func TestPipelineReadsAppendedLines(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "app.log")
	if err := os.WriteFile(path, []byte("old line\n"), 0o600); err != nil {
		t.Fatalf("seed file: %v", err)
	}

	p := newTestPipeline(t, filepath.Join(dir, "state.json"), config.LogSource{Path: path, ServiceName: "svc"})
	src := p.sources[0]

	// First poll tails from the end, so pre-existing content is skipped.
	p.pollSource(src)
	if len(p.buffer) != 0 {
		t.Fatalf("expected no events on first poll, got %d", len(p.buffer))
	}

	appendLine(t, path, "2024-05-06T07:08:09Z INFO started\n")
	appendLine(t, path, "2024-05-06T07:08:10Z ERROR boom\n")
	p.pollSource(src)

	if len(p.buffer) != 2 {
		t.Fatalf("expected 2 events, got %d", len(p.buffer))
	}
	if p.buffer[0].Level != "INFO" || p.buffer[1].Level != "ERROR" {
		t.Fatalf("unexpected levels: %q, %q", p.buffer[0].Level, p.buffer[1].Level)
	}
	if p.buffer[1].Message != "2024-05-06T07:08:10Z ERROR boom" {
		t.Fatalf("unexpected message: %q", p.buffer[1].Message)
	}
}

func TestPipelineSurvivesRotation(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "app.log")
	if err := os.WriteFile(path, nil, 0o600); err != nil {
		t.Fatalf("create file: %v", err)
	}

	p := newTestPipeline(t, filepath.Join(dir, "state.json"), config.LogSource{Path: path})
	src := p.sources[0]
	p.pollSource(src)

	appendLine(t, path, "INFO before rotation\n")
	p.pollSource(src)
	if len(p.buffer) != 1 {
		t.Fatalf("expected 1 event before rotation, got %d", len(p.buffer))
	}

	// Rotate: move the current file aside and create a fresh one at the same path.
	if err := os.Rename(path, path+".1"); err != nil {
		t.Fatalf("rotate: %v", err)
	}
	if err := os.WriteFile(path, []byte("ERROR after rotation\n"), 0o600); err != nil {
		t.Fatalf("create rotated file: %v", err)
	}

	p.pollSource(src)
	if len(p.buffer) != 2 {
		t.Fatalf("expected event from rotated file, got %d events", len(p.buffer))
	}
	if p.buffer[1].Level != "ERROR" || p.buffer[1].Message != "ERROR after rotation" {
		t.Fatalf("unexpected rotated event: %+v", p.buffer[1])
	}
}

func TestPipelineResumesFromSavedOffset(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "app.log")
	if err := os.WriteFile(path, []byte("INFO already present\n"), 0o600); err != nil {
		t.Fatalf("seed file: %v", err)
	}

	statePath := filepath.Join(dir, "state.json")
	store, err := loadState(statePath)
	if err != nil {
		t.Fatalf("loadState: %v", err)
	}
	if err := store.set(path, 0); err != nil {
		t.Fatalf("set offset: %v", err)
	}

	p := newTestPipeline(t, statePath, config.LogSource{Path: path})
	src := p.sources[0]
	if !src.hasState {
		t.Fatal("expected a saved offset to be loaded")
	}

	// With a saved offset of 0 the existing content is read instead of being
	// skipped (which is what a first run without state would do).
	p.pollSource(src)
	if len(p.buffer) != 1 {
		t.Fatalf("expected to resume from saved offset and read 1 event, got %d", len(p.buffer))
	}
}

func TestPipelineWaitsForCompleteLine(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "app.log")
	if err := os.WriteFile(path, nil, 0o600); err != nil {
		t.Fatalf("create file: %v", err)
	}

	p := newTestPipeline(t, filepath.Join(dir, "state.json"), config.LogSource{Path: path})
	src := p.sources[0]
	p.pollSource(src)

	appendLine(t, path, "INFO partial")
	p.pollSource(src)
	if len(p.buffer) != 0 {
		t.Fatalf("incomplete line must not be emitted, got %d events", len(p.buffer))
	}

	appendLine(t, path, " done\n")
	p.pollSource(src)
	if len(p.buffer) != 1 {
		t.Fatalf("expected 1 event after newline, got %d", len(p.buffer))
	}
	if p.buffer[0].Message != "INFO partial done" {
		t.Fatalf("unexpected message: %q", p.buffer[0].Message)
	}
}

func TestPipelineRedactsAndSetsContext(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "app.log")
	if err := os.WriteFile(path, nil, 0o600); err != nil {
		t.Fatalf("create file: %v", err)
	}

	p := newTestPipeline(t, filepath.Join(dir, "state.json"), config.LogSource{Path: path, ServiceName: "checkout"})
	src := p.sources[0]
	p.pollSource(src)

	appendLine(t, path, "INFO auth failed password=secret123\n")
	p.pollSource(src)

	if len(p.buffer) != 1 {
		t.Fatalf("expected 1 event, got %d", len(p.buffer))
	}
	if strings.Contains(p.buffer[0].Message, "secret123") {
		t.Fatalf("secret leaked in message: %q", p.buffer[0].Message)
	}
	if !strings.Contains(p.buffer[0].Context, "checkout") {
		t.Fatalf("service name missing from context: %q", p.buffer[0].Context)
	}
}
