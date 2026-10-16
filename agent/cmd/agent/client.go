package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// ingestPath is the backend ingestion route:
// MetricIngestController is mapped to /api/v1/ingest and @PostMapping("/metrics").
const ingestPath = "/api/v1/ingest/metrics"

const (
	requestTimeout = 10 * time.Second
	maxErrorBody   = 2 << 10
)

// MetricIngestRequest mirrors the backend MetricIngestRequest DTO.
type MetricIngestRequest struct {
	Metrics []MetricPoint `json:"metrics"`
}

// HTTPMetricsClient sends metric batches to the cloud ingestion API using a
// scoped X-API-Key credential.
type HTTPMetricsClient struct {
	baseURL    string
	apiKey     string
	httpClient *http.Client
}

func NewHTTPMetricsClient(cfg *Config) *HTTPMetricsClient {
	return &HTTPMetricsClient{
		baseURL:    strings.TrimRight(cfg.BackendURL, "/"),
		apiKey:     cfg.APIKey,
		httpClient: &http.Client{Timeout: requestTimeout},
	}
}

// Send performs a single POST of the given metrics. It returns an error for any
// non-2xx response; the API key is never included in the returned error.
func (c *HTTPMetricsClient) Send(ctx context.Context, metrics []MetricPoint) error {
	payload, err := json.Marshal(MetricIngestRequest{Metrics: metrics})
	if err != nil {
		return fmt.Errorf("encode payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+ingestPath, bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", c.apiKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("post metrics: %w", err)
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(io.LimitReader(resp.Body, maxErrorBody))
	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		return fmt.Errorf("backend returned %s: %s", resp.Status, strings.TrimSpace(string(body)))
	}
	return nil
}
