package transport

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestBackoffDelayGrowsExpCapped(t *testing.T) {
	initial := time.Second
	max := 10 * time.Second

	want := []time.Duration{1 * time.Second, 2 * time.Second, 4 * time.Second, 8 * time.Second, 10 * time.Second, 10 * time.Second}
	for attempt, expected := range want {
		if got := BackoffDelay(initial, max, attempt, false); got != expected {
			t.Errorf("attempt %d = %s, want %s", attempt, got, expected)
		}
	}
}

func TestBackoffDelayJitterStaysWithinBounds(t *testing.T) {
	base := BackoffDelay(2*time.Second, time.Minute, 1, false)
	for i := 0; i < 100; i++ {
		got := BackoffDelay(2*time.Second, time.Minute, 1, true)
		if got < base || got > base+base/5 {
			t.Fatalf("jittered delay %s outside [%s, %s]", got, base, base+base/5)
		}
	}
}

func TestWaitDelayHonorsRetryAfter(t *testing.T) {
	retryAfter := 5 * time.Second
	got := WaitDelay(time.Second, time.Minute, 0, false, retryAfter)
	if got != retryAfter {
		t.Errorf("WaitDelay = %s, want Retry-After %s", got, retryAfter)
	}

	got = WaitDelay(time.Second, time.Minute, 3, false, time.Millisecond)
	if got != 8*time.Second {
		t.Errorf("WaitDelay = %s, want exponential value 8s", got)
	}
}

func TestAPIErrorRetryClassification(t *testing.T) {
	cases := []struct {
		status    int
		retryable bool
		auth      bool
	}{
		{http.StatusTooManyRequests, true, false},
		{http.StatusInternalServerError, true, false},
		{http.StatusBadGateway, true, false},
		{http.StatusBadRequest, false, false},
		{http.StatusUnauthorized, false, true},
		{http.StatusForbidden, false, true},
		{http.StatusRequestEntityTooLarge, false, false},
	}
	for _, tc := range cases {
		err := &APIError{StatusCode: tc.status}
		if got := IsRetryable(err); got != tc.retryable {
			t.Errorf("status %d retryable = %v, want %v", tc.status, got, tc.retryable)
		}
		if got := IsAuthFailure(err); got != tc.auth {
			t.Errorf("status %d auth = %v, want %v", tc.status, got, tc.auth)
		}
	}
}

func TestPostRawParsesRetryAfterAndClassifies(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Retry-After", "3")
		http.Error(w, "busy", http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := NewClient(server.URL, "app_test")
	err := client.PostRaw(t.Context(), "/api/v1/ingest/logs", []byte(`{}`))
	if err == nil {
		t.Fatal("expected an error")
	}
	if !IsRetryable(err) {
		t.Error("429 should be retryable")
	}
	if got := RetryAfter(err); got != 3*time.Second {
		t.Errorf("RetryAfter = %s, want 3s", got)
	}
}

func TestPostRawRejectsAuthWithoutRetry(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
	}))
	defer server.Close()

	client := NewClient(server.URL, "app_secret")
	err := client.PostRaw(t.Context(), "/api/v1/ingest/logs", []byte(`{}`))
	if err == nil {
		t.Fatal("expected an error")
	}
	if IsRetryable(err) {
		t.Error("401 must not be retryable")
	}
	if !IsAuthFailure(err) {
		t.Error("401 should be classified as an auth failure")
	}
	if got := RetryAfter(err); got != 0 {
		t.Errorf("RetryAfter = %s, want 0", got)
	}
}
