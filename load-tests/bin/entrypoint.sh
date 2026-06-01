#!/bin/sh
set -eu

TOKENS_FILE="${K6_TOKENS_FILE:-/tmp/k6/tokens.json}"
TOKEN_COUNT="${K6_TOKEN_COUNT:-1500}"
PRIVATE_KEY_FILE="${JWT_PRIVATE_KEY_FILE:-/jwt/test-private-key.b64}"

mkdir -p "$(dirname "$TOKENS_FILE")" /reports

if [ -f "$PRIVATE_KEY_FILE" ]; then
node /load-tests/lib/generate-tokens.mjs "$PRIVATE_KEY_FILE" "$TOKENS_FILE" "$TOKEN_COUNT"
else
  echo "JWT private key file not found: $PRIVATE_KEY_FILE" >&2
  exit 1
fi

export K6_WEB_DASHBOARD="${K6_WEB_DASHBOARD:-true}"
export K6_WEB_DASHBOARD_EXPORT="${K6_WEB_DASHBOARD_EXPORT:-/reports/xk6-dashboard.html}"

exec k6 "$@"
