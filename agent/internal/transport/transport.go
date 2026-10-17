// Package transport provides the shared HTTP transport used by the metric and
// log ingestion clients: a JSON POST helper with a fixed timeout and a scoped
// X-API-Key credential, plus bounded exponential backoff for retries.
package transport

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

const (
	requestTimeout = 10 * time.Second
	maxErrorBody   = 2 << 10
)

// Client posts JSON payloads to the backend using a scoped X-API-Key. It is safe
// for concurrent use.
type Client struct {
	baseURL    string
	apiKey     string
	httpClient *http.Client
}

// NewClient builds a client for baseURL, normalizing a trailing slash.
func NewClient(baseURL, apiKey string) *Client {
	return &Client{
		baseURL:    strings.TrimRight(baseURL, "/"),
		apiKey:     apiKey,
		httpClient: &http.Client{Timeout: requestTimeout},
	}
}

// PostJSON marshals payload and POSTs it to path with the X-API-Key header. It
// returns an error for any non-2xx response; the API key is never included in the
// returned error.
func (c *Client) PostJSON(ctx context.Context, path string, payload any) error {
	url := c.baseURL + path

	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("encode payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", c.apiKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("post %s: %w", url, err)
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(io.LimitReader(resp.Body, maxErrorBody))
	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		return fmt.Errorf("backend returned %s: %s", resp.Status, strings.TrimSpace(string(respBody)))
	}
	return nil
}
