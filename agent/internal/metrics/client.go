package metrics

import (
	"context"

	"github.com/vibemonitoring/agent/internal/transport"
)

// ingestPath is the backend metric ingestion route:
// MetricIngestController is mapped to /api/v1/ingest and @PostMapping("/metrics").
const ingestPath = "/api/v1/ingest/metrics"

// ingestRequest mirrors the backend MetricIngestRequest DTO.
type ingestRequest struct {
	Metrics []Point `json:"metrics"`
}

// Client sends metric batches to the cloud ingestion API.
type Client struct {
	http *transport.Client
}

func NewClient(http *transport.Client) *Client {
	return &Client{http: http}
}

// Send performs a single POST of the given points. It returns an error for any
// non-2xx response; the API key is never included in the returned error.
func (c *Client) Send(ctx context.Context, points []Point) error {
	return c.http.PostJSON(ctx, ingestPath, ingestRequest{Metrics: points})
}
