package logs

import (
	"os"
	"path/filepath"
	"testing"
)

func TestStateStoreRoundTrip(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state.json")

	store, err := loadState(path)
	if err != nil {
		t.Fatalf("loadState: %v", err)
	}
	if _, ok := store.offset("/var/log/app.log"); ok {
		t.Fatal("expected no offset for unknown path")
	}

	if err := store.set("/var/log/app.log", 42); err != nil {
		t.Fatalf("set: %v", err)
	}
	if v, ok := store.offset("/var/log/app.log"); !ok || v != 42 {
		t.Fatalf("offset = %d (ok=%v), want 42", v, ok)
	}

	reloaded, err := loadState(path)
	if err != nil {
		t.Fatalf("reload: %v", err)
	}
	if v, ok := reloaded.offset("/var/log/app.log"); !ok || v != 42 {
		t.Fatalf("reloaded offset = %d (ok=%v), want 42", v, ok)
	}
}

func TestLoadStateMissingFileIsEmpty(t *testing.T) {
	store, err := loadState(filepath.Join(t.TempDir(), "does-not-exist.json"))
	if err != nil {
		t.Fatalf("loadState: %v", err)
	}
	if _, ok := store.offset("/x"); ok {
		t.Fatal("expected empty store")
	}
}

func TestLoadStateCorruptReturnsUsableStore(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state.json")
	if err := os.WriteFile(path, []byte("this is not json"), 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}

	store, err := loadState(path)
	if err == nil {
		t.Fatal("expected an error for a corrupt state file")
	}
	if store == nil {
		t.Fatal("expected a usable store even when corrupt")
	}
	if err := store.set("/x", 1); err != nil {
		t.Fatalf("store should still be usable: %v", err)
	}
}
