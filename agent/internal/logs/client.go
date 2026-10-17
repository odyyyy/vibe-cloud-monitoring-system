package logs

import (
	"context"

	"github.com/vibemonitoring/agent/internal/transport"
)

// ingestPath is the backend log ingestion route:
// LogIngestController is mapped to /api/v1/ingest and @PostMapping("/logs").
const ingestPath = "/api/v1/ingest/logs"

// ingestRequest mirrors the backend LogIngestRequest DTO.
type ingestRequest struct {
	Events []Event `json:"events"`
}

// Client sends log batches to the cloud ingestion API.
type Client struct {
	http *transport.Client
}

func NewClient(http *transport.Client) *Client {
	return &Client{http: http}
}

// Send performs a single POST of the given events. It returns an error for any
// non-2xx response; the API key is never included in the returned error.
func (c *Client) Send(ctx context.Context, events []Event) error {
	return c.http.PostJSON(ctx, ingestPath, ingestRequest{Events: events})
}
