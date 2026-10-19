# syntax=docker/dockerfile:1.6
# multi-stage build for the Go agent

# ==================== STAGE 1: build ====================
FROM golang:1.26-alpine AS builder
WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/agent ./cmd/agent

# ==================== STAGE 2: runtime image ====================
# Kubernetes pod log discovery is opt-in. The agent needs a ServiceAccount with
# get/list/watch on pods and read-only mounts of the host /var/log/containers and
# /var/log/pods directories. See k8s/rbac.yaml, k8s/daemonset.yaml and the
# README section "Kubernetes": the API key is injected from a Secret via
# AGENT_API_KEY and is never baked into this image.
FROM alpine:3.20
RUN apk add --no-cache ca-certificates tzdata && \
    adduser -D -u 1001 agent

WORKDIR /app
COPY --from=builder /out/agent /usr/local/bin/agent
COPY config.yaml /app/config.yaml
# Reference manifests for the minimal ServiceAccount (pods: get/list/watch) and
# the DaemonSet deployment. Copy them out with:
#   docker create --name agent-manifests vibemonitoring/agent:latest
#   docker cp agent-manifests:/app/k8s ./k8s
COPY --chown=agent:agent k8s /app/k8s

# The agent writes its log-offset state file into the working directory.
RUN chown -R agent:agent /app

USER agent

ENTRYPOINT ["agent"]
CMD ["-config", "/app/config.yaml"]
