package metrics

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/vibemonitoring/agent/internal/transport"
)

func TestClientSendsBatch(t *testing.T) {
	var gotPath string
	var gotBody ingestRequest

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		if err := json.NewDecoder(r.Body).Decode(&gotBody); err != nil {
			t.Errorf("decode body: %v", err)
		}
		w.WriteHeader(http.StatusAccepted)
	}))
	defer srv.Close()

	client := NewClient(transport.NewClient(srv.URL, "app_secret"))
	err := client.Send(context.Background(), []Point{{Name: "system.cpu.usage", Value: 1.5, Timestamp: time.Now().UTC()}})
	if err != nil {
		t.Fatalf("Send: %v", err)
	}
	if gotPath != ingestPath {
		t.Errorf("path = %q, want %q", gotPath, ingestPath)
	}
	if len(gotBody.Metrics) != 1 || gotBody.Metrics[0].Name != "system.cpu.usage" {
		t.Errorf("unexpected body: %+v", gotBody)
	}
}
