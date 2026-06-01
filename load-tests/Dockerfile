# syntax=docker/dockerfile:1

ARG K6_VERSION=v0.53.0
ARG XK6_VERSION=v1.4.3
ARG XK6_DASHBOARD_VERSION=v0.7.5

FROM golang:1.25-alpine AS builder
ARG K6_VERSION
ARG XK6_VERSION
ARG XK6_DASHBOARD_VERSION
RUN apk add --no-cache git
RUN go install "go.k6.io/xk6/cmd/xk6@${XK6_VERSION}"
RUN xk6 build --k6-version "${K6_VERSION}" \
    --with "github.com/grafana/xk6-dashboard@${XK6_DASHBOARD_VERSION}" \
    --output /tmp/k6

FROM node:20-alpine
RUN apk add --no-cache ca-certificates openssl
COPY --from=builder /tmp/k6 /usr/local/bin/k6
COPY bin/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
WORKDIR /load-tests
ENTRYPOINT ["/entrypoint.sh"]
