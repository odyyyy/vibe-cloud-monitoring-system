# syntax=docker/dockerfile:1.6
# multi-stage build для Go-агента

# ==================== ЭТАП 1: Сборка ====================
FROM golang:1.26-alpine AS builder
WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/agent ./cmd/agent

# ==================== ЭТАП 2: Финальный образ ====================
FROM alpine:3.20
RUN apk add --no-cache ca-certificates tzdata && \
    adduser -D -u 1001 agent

WORKDIR /app
COPY --from=builder /out/agent /usr/local/bin/agent
COPY config.yaml /app/config.yaml

USER agent

ENTRYPOINT ["agent"]
CMD ["-config", "/app/config.yaml"]
