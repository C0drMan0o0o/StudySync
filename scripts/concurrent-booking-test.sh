#!/usr/bin/env bash
# Fires N concurrent POST /bookings requests for the exact same room + time
# slot to prove the Postgres exclusion constraint (V3.1) -- not just the
# application-level overlap check -- is what prevents double-booking under
# concurrency. See Step 9 / Step 7 of the build guide.
#
# Usage:
#   ./scripts/concurrent-booking-test.sh [concurrency]
#
# Env overrides:
#   BASE_URL     default http://localhost:8080
#   CONCURRENCY  default 20 (or first positional arg)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENCY="${1:-${CONCURRENCY:-20}}"

command -v jq >/dev/null || { echo "jq is required (brew install jq)" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

RUN_ID="$(date +%s)-$$"
EMAIL="concurrency-test-${RUN_ID}@example.com"

echo "== Concurrent booking test (concurrency=${CONCURRENCY}) =="
echo "Base URL: ${BASE_URL}"

if ! curl -s -o /dev/null -w '' --max-time 5 "${BASE_URL}/rooms" 2>/dev/null; then
  echo "Cannot reach ${BASE_URL} -- is the app running? (mvn spring-boot:run)" >&2
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
  -d "{\"name\":\"Concurrency Test Room ${RUN_ID}\",\"capacity\":4,\"location\":\"Test\"}")

if [ "${ROOM_STATUS}" != "201" ]; then
  echo "Room creation failed (HTTP ${ROOM_STATUS}):" >&2
  cat "${WORKDIR}/room.json" >&2
  exit 1
fi

ROOM_ID=$(jq -r '.id' "${WORKDIR}/room.json")
echo "-- Room id: ${ROOM_ID}"

# Pick a slot that's different on every run (avoids collisions across reruns)
# rather than relying on cleanup.
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
