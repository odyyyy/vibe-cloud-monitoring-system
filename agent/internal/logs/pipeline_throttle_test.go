package logs

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/config"
	"github.com/vibemonitoring/agent/internal/transport"
)

func newThrottledPipeline(t *testing.T, stateFile string, throttle config.LogThrottlingConfig, sources ...config.LogSource) *Pipeline {
	t.Helper()
	cfg := &config.Config{
		BackendURL:      "http://localhost:8080",
		APIKey:          "app_test",
		Logs:            sources,
		LogBatchSize:    500,
		LogFlushSeconds: 5,
		StateFile:       stateFile,
		LogThrottling:   throttle,
	}
	p, err := NewPipeline(cfg, NewClient(transport.NewClient(cfg.BackendURL, cfg.APIKey)), discardLogger())
	if err != nil {
		t.Fatalf("NewPipeline: %v", err)
	}
	return p
}

func TestPipelineForwardsAllLogsUnderThreshold(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "app.log")
	if err := os.WriteFile(path, nil, 0o600); err != nil {
		t.Fatalf("create file: %v", err)
	}

	p := newThrottledPipeline(t, filepath.Join(dir, "state.json"), config.LogThrottlingConfig{
		MaxLogsPerSecond: 100,
		SampleRate:       0.1,
	}, config.LogSource{Path: path, Format: config.LogFormatText})
	src := p.sources[0]
	src.throttle.now = func() time.Time { return time.Unix(1_700_000_000, 0) }
	p.pollSource(src)

	var lines strings.Builder
	for i := 0; i < 10; i++ {
		lines.WriteString("INFO handled request ")
		lines.WriteString(strconv.Itoa(i))
		lines.WriteString("\n")
	}
	appendLine(t, path, lines.String())
	p.pollSource(src)

	if len(p.buffer) != 10 {
		t.Fatalf("expected 10 events below the threshold, got %d", len(p.buffer))
	}
	if got := p.DroppedTotal(); got != 0 {
		t.Fatalf("DroppedTotal = %d, want 0", got)
	}
}

func TestPipelineThrottlesFloodButForwardsCritical(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "app.log")
	if err := os.WriteFile(path, nil, 0o600); err != nil {
		t.Fatalf("create file: %v", err)
	}

	p := newThrottledPipeline(t, filepath.Join(dir, "state.json"), config.LogThrottlingConfig{
		MaxLogsPerSecond:    100,
		SampleRate:          0, // drop every excess event, making the assertion exact
		AlwaysForwardLevels: []string{"FATAL", "CRITICAL"},
	}, config.LogSource{Path: path, Format: config.LogFormatText})
	src := p.sources[0]
	fixed := time.Unix(1_700_000_000, 0)
	src.throttle.now = func() time.Time { return fixed }
	p.pollSource(src)

	var lines strings.Builder
	for i := 0; i < 1000; i++ {
		lines.WriteString("INFO flood ")
		lines.WriteString(strconv.Itoa(i))
		lines.WriteString("\n")
	}
	lines.WriteString("FATAL database connection lost\n")
	appendLine(t, path, lines.String())
	p.pollSource(src)

	// 100 INFO events fit under the limit; the FATAL bypasses it.
	if len(p.buffer) != 101 {
		t.Fatalf("expected 101 forwarded events (100 INFO + 1 FATAL), got %d", len(p.buffer))
	}
	if got := p.DroppedTotal(); got != 900 {
		t.Fatalf("DroppedTotal = %d, want 900", got)
	}

	var fatal *Event
	for i := range p.buffer {
		if p.buffer[i].severity == "FATAL" {
			fatal = &p.buffer[i]
			break
		}
	}
	if fatal == nil {
		t.Fatal("FATAL event was not forwarded")
	}
	if fatal.Level != "ERROR" {
		t.Errorf("FATAL level = %q, want the normalized ERROR sent to the backend", fatal.Level)
	}
}

func TestPipelineThrottlingIsPerSource(t *testing.T) {
	dir := t.TempDir()
	noisyPath := filepath.Join(dir, "noisy.log")
	quietPath := filepath.Join(dir, "quiet.log")
	for _, path := range []string{noisyPath, quietPath} {
		if err := os.WriteFile(path, nil, 0o600); err != nil {
			t.Fatalf("create %s: %v", path, err)
		}
	}

	p := newThrottledPipeline(t, filepath.Join(dir, "state.json"), config.LogThrottlingConfig{
		MaxLogsPerSecond: 100,
		SampleRate:       0,
	},
		config.LogSource{Path: noisyPath, Format: config.LogFormatText},
		config.LogSource{Path: quietPath, Format: config.LogFormatText},
	)
	fixed := time.Unix(1_700_000_000, 0)
	noisy := p.sources[0]
	quiet := p.sources[1]
	noisy.throttle.now = func() time.Time { return fixed }
	quiet.throttle.now = func() time.Time { return fixed }
	p.pollSource(noisy)
	p.pollSource(quiet)

	var noise strings.Builder
	for i := 0; i < 1000; i++ {
		noise.WriteString("INFO noise\n")
	}
	appendLine(t, noisyPath, noise.String())
	appendLine(t, quietPath, "INFO quiet and important\n")

	p.pollSource(noisy)
	p.pollSource(quiet)

	var quietForwarded bool
	for _, event := range p.buffer {
		if strings.Contains(event.Message, "quiet and important") {
			quietForwarded = true
		}
	}
	if !quietForwarded {
		t.Fatal("a noisy source must not starve a quiet source")
	}
	if got := p.DroppedTotal(); got != 900 {
		t.Fatalf("DroppedTotal = %d, want 900 from the noisy source only", got)
	}
}
