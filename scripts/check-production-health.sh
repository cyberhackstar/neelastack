#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-https://neelastack.com}"
MAX_BACKUP_AGE_HOURS="${MAX_BACKUP_AGE_HOURS:-30}"
HEALTH_URL="$BASE_URL/actuator/health"
HTTP_CODE="$(curl -fsS -o /tmp/neelastack-health.json -w '%{http_code}' "$HEALTH_URL")"
[[ "$HTTP_CODE" == "200" ]] || { echo "ERROR: health returned HTTP $HTTP_CODE"; exit 1; }
grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' /tmp/neelastack-health.json || { echo "ERROR: health is not UP"; cat /tmp/neelastack-health.json; exit 1; }
BACKUP_DIR="${BACKUP_DIR:-/opt/neelastack/backups}"
LATEST="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.sql.gz' -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -1 | cut -d' ' -f2- || true)"
if [[ -n "$LATEST" ]]; then
  AGE=$(( ( $(date +%s) - $(stat -c %Y "$LATEST") ) / 3600 ))
  if (( AGE > MAX_BACKUP_AGE_HOURS )); then echo "ERROR: latest backup is ${AGE}h old: $LATEST"; exit 1; fi
  echo "OK: latest backup ${AGE}h old: $LATEST"
else
  echo "ERROR: no PostgreSQL backup found in $BACKUP_DIR"; exit 1
fi
echo "OK: production health and backup age checks passed"
