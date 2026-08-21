#!/usr/bin/env bash
# Fires N concurrent POST /bookings requests to a service running on Render.
# Handles Render cold starts by waiting for the service to wake up before running the test.
#
# Usage:
#   ./scripts/concurrent-booking-test-render.sh [concurrency]
#
# Env overrides:
#   BASE_URL     default https://studysync-backend-a947.onrender.com
#   CONCURRENCY  default 20 (or first positional arg)

set -euo pipefail

BASE_URL="${BASE_URL:-https://studysync-backend-a947.onrender.com}"
CONCURRENCY="${1:-${CONCURRENCY:-20}}"

command -v jq >/dev/null || { echo "jq is required (brew install jq)" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

RUN_ID="$(date +%s)-$$"
EMAIL="concurrency-test-${RUN_ID}@example.com"

echo "== Render Concurrent Booking Test (concurrency=${CONCURRENCY}) =="
echo "Target URL: ${BASE_URL}"

# Wakeup / Polling loop for Render cold start
echo "-- Checking if service is awake (timeout 120s)..."
MAX_WAKEUP_ATTEMPTS=24
WAKEUP_INTERVAL=5
AWAKE=false

for ((i=1; i<=MAX_WAKEUP_ATTEMPTS; i++)); do
  # Check /rooms endpoint; any valid HTTP response (even 401/403/404/200) means the server is awake and routing requests.
  # We use a short connection timeout but a longer read timeout for cold start.
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 15 "${BASE_URL}/rooms" 2>/dev/null || echo "000")
  if [ "$HTTP_STATUS" != "000" ] && [ "$HTTP_STATUS" -ne 0 ]; then
    echo "   Service is awake! (HTTP Status: $HTTP_STATUS)"
    AWAKE=true
    break
  fi
  echo "   Waiting for service to wake up (attempt $i/$MAX_WAKEUP_ATTEMPTS)..."
  sleep $WAKEUP_INTERVAL
done

if [ "$AWAKE" = false ]; then
  echo "ERROR: Service at ${BASE_URL} did not wake up within 120 seconds." >&2
  exit 1
fi

echo "-- Registering test user ${EMAIL}"
REGISTER_STATUS=$(curl -s -o "${WORKDIR}/register.json" -w '%{http_code}' \
  -X POST "${BASE_URL}/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"password123\",\"name\":\"Concurrency Test\"}")

if [ "${REGISTER_STATUS}" != "201" ]; then
  echo "Registration failed (HTTP ${REGISTER_STATUS}):" >&2
  cat "${WORKDIR}/register.json" >&2
  exit 1
fi

TOKEN=$(jq -r '.token' "${WORKDIR}/register.json")

echo "-- Creating test room"
ROOM_STATUS=$(curl -s -o "${WORKDIR}/room.json" -w '%{http_code}' \
  -X POST "${BASE_URL}/rooms" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "{\"name\":\"Concurrency Test Room ${RUN_ID}\",\"capacity\":4,\"location\":\"Render Test\"}")

if [ "${ROOM_STATUS}" != "201" ]; then
  echo "Room creation failed (HTTP ${ROOM_STATUS}):" >&2
  cat "${WORKDIR}/room.json" >&2
  exit 1
fi

ROOM_ID=$(jq -r '.id' "${WORKDIR}/room.json")
echo "-- Room id: ${ROOM_ID}"

# Pick a slot that's different on every run
START_TIME=$(python3 -c "
import datetime, random
start = datetime.datetime.now() + datetime.timedelta(days=1, minutes=random.randint(0, 100000))
print(start.replace(second=0, microsecond=0).isoformat())
")
END_TIME=$(python3 -c "
import datetime
start = datetime.datetime.fromisoformat('${START_TIME}')
print((start + datetime.timedelta(hours=1)).isoformat())
")

echo "-- Slot: ${START_TIME} -> ${END_TIME}"

BOOKING_BODY="${WORKDIR}/booking-body.json"
jq -n --argjson roomId "${ROOM_ID}" --arg start "${START_TIME}" --arg end "${END_TIME}" \
  '{roomId: $roomId, startTime: $start, endTime: $end}' > "${BOOKING_BODY}"

mkdir -p "${WORKDIR}/results"

fire_booking() {
  local idx="$1"
  local status
  status=$(curl -s -o "${WORKDIR}/results/${idx}.body" -w '%{http_code}' \
    -X POST "${BASE_URL}/bookings" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${TOKEN}" \
    --data-binary "@${BOOKING_BODY}")
  echo "${status}" > "${WORKDIR}/results/${idx}.status"
}
export -f fire_booking
export WORKDIR BASE_URL TOKEN BOOKING_BODY

echo "-- Firing ${CONCURRENCY} concurrent identical booking requests..."
seq 1 "${CONCURRENCY}" | xargs -P "${CONCURRENCY}" -I{} bash -c 'fire_booking "$@"' _ {}

SUCCESS_COUNT=$(cat "${WORKDIR}"/results/*.status | grep -c '^201$' || true)
CONFLICT_COUNT=$(cat "${WORKDIR}"/results/*.status | grep -c '^409$' || true)
OTHER_COUNT=$(( CONCURRENCY - SUCCESS_COUNT - CONFLICT_COUNT ))

echo ""
echo "== Results =="
echo "201 Created : ${SUCCESS_COUNT}"
echo "409 Conflict: ${CONFLICT_COUNT}"
echo "Other       : ${OTHER_COUNT}"

if [ "${OTHER_COUNT}" -gt 0 ]; then
  echo ""
  echo "Unexpected status codes seen:"
  for f in "${WORKDIR}"/results/*.status; do
    code=$(cat "$f")
    if [ "${code}" != "201" ] && [ "${code}" != "409" ]; then
      idx=$(basename "$f" .status)
      echo "  request ${idx} -> ${code}:"
      sed 's/^/    /' "${WORKDIR}/results/${idx}.body"
    fi
  done
fi

echo ""
if [ "${SUCCESS_COUNT}" -eq 1 ] && [ "${CONFLICT_COUNT}" -eq $((CONCURRENCY - 1)) ]; then
  echo "PASS: exactly one booking succeeded, the rest were rejected as conflicts."
  echo "The DB exclusion constraint held under concurrency."
  exit 0
else
  echo "FAIL: expected exactly 1 success and $((CONCURRENCY - 1)) conflicts."
  echo "If SUCCESS_COUNT > 1, the DB constraint did NOT prevent a double-booking."
  exit 1
fi
