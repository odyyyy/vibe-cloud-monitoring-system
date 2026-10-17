package logs

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/transport"
)

func TestClientSendsBatch(t *testing.T) {
	var gotPath, gotKey string
	var gotBody ingestRequest

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotKey = r.Header.Get("X-API-Key")
		if err := json.NewDecoder(r.Body).Decode(&gotBody); err != nil {
			t.Errorf("decode body: %v", err)
		}
		w.WriteHeader(http.StatusAccepted)
	}))
	defer srv.Close()

	client := NewClient(transport.NewClient(srv.URL, "app_secret"))
	err := client.Send(context.Background(), []Event{{
		Level:     "ERROR",
		Message:   "boom",
		Timestamp: time.Now().UTC(),
		Context:   `{"service":"checkout"}`,
	}})
	if err != nil {
		t.Fatalf("Send: %v", err)
	}
	if gotPath != ingestPath {
		t.Errorf("path = %q, want %q", gotPath, ingestPath)
	}
	if gotKey != "app_secret" {
		t.Errorf("X-API-Key = %q", gotKey)
	}
	if len(gotBody.Events) != 1 || gotBody.Events[0].Message != "boom" {
		t.Errorf("unexpected body: %+v", gotBody)
	}
}

func TestClientErrorDoesNotLeakKey(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "rejected", http.StatusBadRequest)
	}))
	defer srv.Close()

	client := NewClient(transport.NewClient(srv.URL, "app_supersecret"))
	err := client.Send(context.Background(), []Event{{Level: "INFO", Message: "hi", Timestamp: time.Now().UTC()}})
	if err == nil {
		t.Fatal("expected an error for HTTP 400")
	}
	if strings.Contains(err.Error(), "app_supersecret") {
		t.Fatal("error message leaked the API key")
	}
}
