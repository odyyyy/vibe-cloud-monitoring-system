# syntax=docker/dockerfile:1.6
# Multi-stage build: Node build -> unprivileged nginx static runtime.

# ==================== STAGE 1: build ====================
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ==================== STAGE 2: runtime image ====================
# The unprivileged image runs as uid 101 (nginx) and listens on 8080 by default.
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Runtime upstream for the /api proxy. The browser always calls /api on the same origin,
# so changing the backend location does not require rebuilding the frontend image.
ENV BACKEND_URL=http://backend:8080

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/healthz || exit 1
