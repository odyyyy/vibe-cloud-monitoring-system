// Package transport provides the shared HTTP transport used by the metric and
// log ingestion clients: a JSON POST helper with a fixed timeout and a scoped
// X-API-Key credential, plus retry classification and bounded exponential
// backoff for transient failures.
package transport

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const (
	requestTimeout = 10 * time.Second
	maxErrorBody   = 2 << 10
)

// APIError describes a non-2xx response from the backend. It is returned by
// PostRaw/PostJSON so callers can decide whether the failure is retryable
// without parsing error strings.
type APIError struct {
	StatusCode int
	Status     string
	Body       string
	// RetryAfter is the delay requested through the Retry-After header, or zero
	// when the header is missing or invalid.
	RetryAfter time.Duration
}

func (e *APIError) Error() string {
	body := strings.TrimSpace(e.Body)
	if body == "" {
		return fmt.Sprintf("backend returned %s", e.Status)
	}
	return fmt.Sprintf("backend returned %s: %s", e.Status, body)
}

// Retryable reports whether the status code represents a transient failure.
// 429 and 5xx are retryable; every other non-2xx response (400, 401, 403, 413,
// ...) is a permanent rejection and must not be retried.
func (e *APIError) Retryable() bool {
	return e.StatusCode == http.StatusTooManyRequests || e.StatusCode >= http.StatusInternalServerError
}

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

// PostJSON marshals payload and POSTs it to path with the X-API-Key header.
func (c *Client) PostJSON(ctx context.Context, path string, payload any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("encode payload: %w", err)
	}
	return c.PostRaw(ctx, path, body)
}

// PostRaw POSTs an already encoded JSON body to path with the X-API-Key header.
// It returns an *APIError for any non-2xx response; the API key is never
// included in the returned error.
func (c *Client) PostRaw(ctx context.Context, path string, body []byte) error {
	url := c.baseURL + path

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
		return &APIError{
			StatusCode: resp.StatusCode,
			Status:     resp.Status,
			Body:       string(respBody),
			RetryAfter: parseRetryAfter(resp.Header.Get("Retry-After")),
		}
	}
	return nil
}

// IsRetryable reports whether err is a transient failure worth retrying.
// Network and transport errors are retryable; context cancellation is not.
// API errors delegate to APIError.Retryable.
func IsRetryable(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, context.Canceled) {
		return false
	}
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		return apiErr.Retryable()
	}
	return true
}

// IsAuthFailure reports whether err is a 401 or 403 response, which almost
// always means the API key is wrong or revoked.
func IsAuthFailure(err error) bool {
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		return false
	}
	return apiErr.StatusCode == http.StatusUnauthorized || apiErr.StatusCode == http.StatusForbidden
}

// RetryAfter returns the server-requested delay for err, or zero.
func RetryAfter(err error) time.Duration {
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		return 0
	}
	return apiErr.RetryAfter
}

// parseRetryAfter accepts both forms defined by RFC 9110: a number of seconds
// or an HTTP date.
func parseRetryAfter(value string) time.Duration {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0
	}
	if seconds, err := strconv.Atoi(value); err == nil {
		if seconds <= 0 {
			return 0
		}
		return time.Duration(seconds) * time.Second
	}
	if when, err := http.ParseTime(value); err == nil {
		delay := time.Until(when)
		if delay <= 0 {
			return 0
		}
		return delay
	}
	return 0
}
