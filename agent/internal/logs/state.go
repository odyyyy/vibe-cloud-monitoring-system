package logs

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"sync"
)

// stateData is the on-disk representation of the agent's progress. It maps a log
// source path to the number of committed bytes already read from that file.
type stateData struct {
	Offsets map[string]int64 `json:"offsets"`
}

// stateStore persists log read offsets so the agent resumes where it left off
// after a restart. It is safe for concurrent use. A write is atomic (temp file +
// rename) so a crash cannot leave a partially written state file.
type stateStore struct {
	path string

	mu   sync.Mutex
	data stateData
}

// loadState reads the state file at path. A missing file yields an empty store and
// no error. A corrupt file yields an empty, usable store and an error: the agent
// logs a warning and keeps running instead of failing to start.
func loadState(path string) (*stateStore, error) {
	store := &stateStore{path: path, data: stateData{Offsets: map[string]int64{}}}

	data, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, fs.ErrNotExist) {
			return store, nil
		}
		return store, fmt.Errorf("read state %q: %w", path, err)
	}
	if err := json.Unmarshal(data, &store.data); err != nil {
		store.data = stateData{Offsets: map[string]int64{}}
		return store, fmt.Errorf("parse state %q: %w", path, err)
	}
	if store.data.Offsets == nil {
		store.data.Offsets = map[string]int64{}
	}
	return store, nil
}

// offset returns the persisted offset for a source path, if any.
func (s *stateStore) offset(path string) (int64, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	v, ok := s.data.Offsets[path]
	return v, ok
}

// set records the offset for a source path and persists it. It is a no-op when the
// value is unchanged, so polling every second does not rewrite the file.
func (s *stateStore) set(path string, offset int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if existing, ok := s.data.Offsets[path]; ok && existing == offset {
		return nil
	}
	s.data.Offsets[path] = offset
	return s.writeLocked()
}

// remove drops the persisted offset for a source that no longer exists (for
// example a deleted pod container) so the state file does not grow forever.
func (s *stateStore) remove(path string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.data.Offsets[path]; !ok {
		return nil
	}
	delete(s.data.Offsets, path)
	return s.writeLocked()
}

func (s *stateStore) writeLocked() error {
	if s.path == "" {
		return nil
	}
	data, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		return fmt.Errorf("encode state: %w", err)
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return fmt.Errorf("write state: %w", err)
	}
	if err := os.Rename(tmp, s.path); err != nil {
		return fmt.Errorf("commit state: %w", err)
	}
	return nil
}
