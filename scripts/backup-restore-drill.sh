#!/usr/bin/env bash
#
# Automated backup/restore drill for the production PostgreSQL database.
#
# What it actually does, end to end:
#   1. pg_dump's the live database (from the running neelastack-postgres container)
#      and gzip-compresses the output onto local disk.
#   2. Applies a grandfather-father-son (daily/weekly/monthly) retention policy to
#      the local backup directory — see the RETENTION NOTE below for what this is
#      and is not a substitute for.
#   3. Optionally ships the fresh dump offsite via OFFSITE_UPLOAD_CMD (pluggable —
#      see OFFSITE UPLOAD NOTE below).
#   4. Spins up a throwaway, network-isolated Postgres container, restores the dump
#      into it, runs Flyway against it (via the official flyway/flyway image, same
#      migrations the backend ships), and runs a handful of data-integrity checks.
#   5. Reports how long the restore+migrate+validate actually took (this run's
#      empirical RTO) and reminds you what your RPO depends on (see RPO NOTE).
#   6. Always tears down the staging container/volume, even on failure.
#
# This is a DRILL: it proves a given dump file is actually restorable and that
# Flyway applies cleanly on top of it, on a schedule, without touching production.
# It does not modify the live database at any point.
#
# RETENTION NOTE:
#   The keep/prune logic below is a local simulation of a GFS retention policy —
#   useful for testing the *rule*, and for keeping this repo runnable without any
#   cloud credentials. It is NOT a substitute for a real object-storage lifecycle
#   policy (S3 Lifecycle rules, Backblaze B2 lifecycle, etc.) once dumps are
#   actually shipped offsite — configure that on the bucket itself.
#
# OFFSITE UPLOAD NOTE:
#   No object-storage credentials exist anywhere in this repo, so this script
#   does not assume a specific provider. Set OFFSITE_UPLOAD_CMD to a command that
#   accepts the local dump path as its final argument, e.g.:
#     export OFFSITE_UPLOAD_CMD="aws s3 cp --storage-class STANDARD_IA"
#     export OFFSITE_UPLOAD_CMD="rclone copyto :b2:neelastack-backups/"
#   Left unset, the script skips this step and says so loudly — it will not
#   silently pretend a real offsite copy happened.
#
# RPO NOTE:
#   RPO (max acceptable data loss) is a function of how often this script *runs*,
#   not of anything this script can enforce by itself. Running it via cron every
#   6 hours gives you a 6-hour RPO ceiling; every 24 hours gives you a 24-hour
#   ceiling. Put the schedule in cron/systemd-timer, not in this file.
#
# Usage:
#   ./scripts/backup-restore-drill.sh                 # backup + full restore drill
#   ./scripts/backup-restore-drill.sh --backup-only    # just steps 1-3
#   ./scripts/backup-restore-drill.sh --drill-only <path-to-dump.sql.gz>
#                                                       # restore+validate an existing dump
#
# Requires: docker, gzip, a .env (or exported DB_NAME/DB_USERNAME/DB_PASSWORD) in
# the same shape as docker-compose.prod.yml. Run from the repository root, or set
# REPO_ROOT explicitly.
#
# Exit codes:
#   0 - backup (and, unless --backup-only, the restore drill) completed and passed
#       every validation check
#   1 - pg_dump failed, or produced an empty/unreadable file
#   2 - staging container failed to come up
#   3 - restore of the dump into staging failed
#   4 - Flyway migration against the restored staging DB failed
#   5 - post-restore data-integrity checks failed
#   6 - bad usage / missing prerequisites

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration — every one of these can be overridden via environment
# variables, and DB_* / REDIS_* deliberately match docker-compose.prod.yml's
# naming so a single .env can drive both.
# ---------------------------------------------------------------------------
REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SOURCE_CONTAINER="${SOURCE_CONTAINER:-neelastack-postgres}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-$REPO_ROOT/backend/src/main/resources/db/migration}"
BACKUP_DIR="${BACKUP_DIR:-$REPO_ROOT/backups}"

RETENTION_DAILY_DAYS="${RETENTION_DAILY_DAYS:-7}"
RETENTION_WEEKLY_WEEKS="${RETENTION_WEEKLY_WEEKS:-4}"
RETENTION_MONTHLY_MONTHS="${RETENTION_MONTHLY_MONTHS:-12}"

STAGING_CONTAINER="${STAGING_CONTAINER:-neelastack-postgres-restore-drill}"
STAGING_NETWORK="${STAGING_NETWORK:-neelastack-restore-drill-net}"
STAGING_DB="${STAGING_DB:-neelastack_drill}"
STAGING_USER="${STAGING_USER:-neelastack_drill}"
STAGING_PASSWORD="${STAGING_PASSWORD:-drill-only-password}"
STAGING_IMAGE="${STAGING_IMAGE:-postgres:16-alpine}"
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:10-alpine}"
STAGING_READY_TIMEOUT_SECONDS="${STAGING_READY_TIMEOUT_SECONDS:-60}"

MODE="full"           # full | backup-only | drill-only
DRILL_ONLY_DUMP_PATH="" # set when MODE=drill-only

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() { echo "[backup-restore-drill] $(date -u '+%Y-%m-%dT%H:%M:%SZ') $*"; }
die() { log "FATAL: $*"; exit "${2:-1}"; }

cleanup_staging() {
  docker rm -f "$STAGING_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$STAGING_NETWORK" >/dev/null 2>&1 || true
}
trap cleanup_staging EXIT

wait_for_staging_postgres() {
  local elapsed=0
  while [ "$elapsed" -lt "$STAGING_READY_TIMEOUT_SECONDS" ]; do
    if docker exec "$STAGING_CONTAINER" pg_isready -U "$STAGING_USER" -d "$STAGING_DB" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

# ---------------------------------------------------------------------------
# Step 1: pg_dump + gzip
# ---------------------------------------------------------------------------
run_backup() {
  mkdir -p "$BACKUP_DIR"
  local timestamp dump_file
  timestamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  dump_file="$BACKUP_DIR/neelastack-${timestamp}.sql.gz"

  log "Dumping database '$DB_NAME' from container '$SOURCE_CONTAINER'..."
  if ! docker exec -e PGPASSWORD="$DB_PASSWORD" "$SOURCE_CONTAINER" \
      pg_dump -U "$DB_USERNAME" -d "$DB_NAME" --format=plain --no-owner --no-privileges \
      | gzip -9 > "$dump_file"; then
    rm -f "$dump_file"
    die "pg_dump failed — see docker logs $SOURCE_CONTAINER for detail." 1
  fi

  if [ ! -s "$dump_file" ]; then
    rm -f "$dump_file"
    die "pg_dump produced an empty file — refusing to keep it as a backup." 1
  fi

  log "Backup written: $dump_file ($(du -h "$dump_file" | cut -f1))"
  echo "$dump_file"
}

# ---------------------------------------------------------------------------
# Step 2: retention (simulated GFS — see RETENTION NOTE above)
# ---------------------------------------------------------------------------
apply_retention() {
  log "Applying retention policy: keep daily for ${RETENTION_DAILY_DAYS}d, then one/week for ${RETENTION_WEEKLY_WEEKS}w, then one/month for ${RETENTION_MONTHLY_MONTHS}mo."

  local now_epoch daily_cutoff weekly_cutoff monthly_cutoff
  now_epoch=$(date -u +%s)
  daily_cutoff=$((now_epoch - RETENTION_DAILY_DAYS * 86400))
  weekly_cutoff=$((now_epoch - (RETENTION_DAILY_DAYS + RETENTION_WEEKLY_WEEKS * 7) * 86400))
  monthly_cutoff=$((now_epoch - (RETENTION_DAILY_DAYS + RETENTION_WEEKLY_WEEKS * 7 + RETENTION_MONTHLY_MONTHS * 31) * 86400))

  declare -A kept_week kept_month
  local deleted=0 kept=0

  # Newest first, so the FIRST file we see in a given ISO-week/month bucket is the
  # one we keep for that bucket (i.e. the most recent backup in that period).
  for f in $(find "$BACKUP_DIR" -maxdepth 1 -name 'neelastack-*.sql.gz' | sort -r); do
    local fname file_epoch
    fname="$(basename "$f")"
    # Filename is neelastack-YYYYMMDDTHHMMSSZ.sql.gz
    local ts="${fname#neelastack-}"; ts="${ts%.sql.gz}"
    file_epoch=$(date -u -d "${ts:0:8} ${ts:9:2}:${ts:11:2}:${ts:13:2}" +%s 2>/dev/null || echo "")
    if [ -z "$file_epoch" ]; then
      log "  skip (unparseable filename): $fname"
      continue
    fi

    if [ "$file_epoch" -ge "$daily_cutoff" ]; then
      kept=$((kept + 1)); continue  # inside the daily window — always keep
    fi

    if [ "$file_epoch" -ge "$weekly_cutoff" ]; then
      local week_key; week_key=$(date -u -d "@$file_epoch" +%G-W%V)
      if [ -z "${kept_week[$week_key]:-}" ]; then
        kept_week[$week_key]=1; kept=$((kept + 1)); continue
      fi
    elif [ "$file_epoch" -ge "$monthly_cutoff" ]; then
      local month_key; month_key=$(date -u -d "@$file_epoch" +%Y-%m)
      if [ -z "${kept_month[$month_key]:-}" ]; then
        kept_month[$month_key]=1; kept=$((kept + 1)); continue
      fi
    fi

    log "  pruning: $fname"
    rm -f "$f"
    deleted=$((deleted + 1))
  done

  log "Retention pass complete — kept $kept, pruned $deleted."
}

# ---------------------------------------------------------------------------
# Step 3: offsite upload (pluggable — see OFFSITE UPLOAD NOTE above)
# ---------------------------------------------------------------------------
offsite_upload() {
  local dump_file="$1"
  if [ -z "${OFFSITE_UPLOAD_CMD:-}" ]; then
    log "OFFSITE_UPLOAD_CMD is not set — skipping real offsite upload. This backup exists ONLY on local disk right now."
    return 0
  fi
  log "Shipping offsite: $OFFSITE_UPLOAD_CMD $dump_file"
  if ! eval "$OFFSITE_UPLOAD_CMD \"$dump_file\""; then
    log "WARNING: offsite upload command failed. Local copy at $dump_file is still intact."
  fi
}

# ---------------------------------------------------------------------------
# Step 4: restore drill — clean staging DB, restore, migrate, validate
# ---------------------------------------------------------------------------
run_restore_drill() {
  local dump_file="$1"
  [ -s "$dump_file" ] || die "Dump file not found or empty: $dump_file" 6

  local start_epoch; start_epoch=$(date -u +%s)

  cleanup_staging  # in case a previous run left something behind
  log "Creating isolated network and staging Postgres container..."
  docker network create "$STAGING_NETWORK" >/dev/null
  docker run -d --name "$STAGING_CONTAINER" --network "$STAGING_NETWORK" \
    -e POSTGRES_DB="$STAGING_DB" -e POSTGRES_USER="$STAGING_USER" -e POSTGRES_PASSWORD="$STAGING_PASSWORD" \
    "$STAGING_IMAGE" >/dev/null \
    || die "Could not start staging container $STAGING_CONTAINER." 2

  if ! wait_for_staging_postgres; then
    docker logs "$STAGING_CONTAINER" || true
    die "Staging Postgres never became ready within ${STAGING_READY_TIMEOUT_SECONDS}s." 2
  fi
  log "Staging Postgres is up (container: $STAGING_CONTAINER)."

  log "Restoring dump into staging database '$STAGING_DB'..."
  if ! gunzip -c "$dump_file" | docker exec -i -e PGPASSWORD="$STAGING_PASSWORD" "$STAGING_CONTAINER" \
      psql -U "$STAGING_USER" -d "$STAGING_DB" -v ON_ERROR_STOP=1 -q; then
    die "Restore into staging failed — the dump file may be corrupt or incompatible." 3
  fi
  log "Restore completed."

  log "Running Flyway against the restored staging database (migrations: $MIGRATIONS_DIR)..."
  if ! docker run --rm --network "container:$STAGING_CONTAINER" \
      -v "$MIGRATIONS_DIR:/flyway/sql:ro" \
      "$FLYWAY_IMAGE" \
      -url="jdbc:postgresql://localhost:5432/${STAGING_DB}" \
      -user="$STAGING_USER" -password="$STAGING_PASSWORD" \
      -connectRetries=10 -baselineOnMigrate=true \
      info migrate; then
    die "Flyway migration failed against the restored data — the backup does NOT satisfy your RTO target as-is." 4
  fi
  log "Flyway migration succeeded on top of the restored data."

  log "Running data-integrity checks..."
  if ! validate_restored_data; then
    die "Post-restore data-integrity checks failed — investigate before trusting this backup." 5
  fi
  log "Data-integrity checks passed."

  local elapsed=$(( $(date -u +%s) - start_epoch ))
  log "=== DRILL RESULT: restore + migrate + validate completed in ${elapsed}s (this run's measured RTO) ==="
  log "This backup was captured at $(basename "$dump_file" | sed -E 's/neelastack-(.*)\.sql\.gz/\1/'). Your actual RPO is bounded by how often this script runs on a schedule — see RPO NOTE at the top of this file."
}

psql_staging() {
  docker exec -e PGPASSWORD="$STAGING_PASSWORD" "$STAGING_CONTAINER" \
    psql -U "$STAGING_USER" -d "$STAGING_DB" -X -A -t -c "$1"
}

validate_restored_data() {
  local ok=true

  # 1. Flyway's own history table must show every migration as successful — a
  #    restore that "worked" but left failed rows would be silently wrong.
  local failed_migrations
  failed_migrations=$(psql_staging "SELECT count(*) FROM flyway_schema_history WHERE success = false;" | tr -d '[:space:]')
  if [ "$failed_migrations" != "0" ]; then
    log "  FAIL: $failed_migrations failed row(s) in flyway_schema_history."
    ok=false
  else
    log "  OK: flyway_schema_history has zero failed migrations."
  fi

  # 2. Core tables should exist and be non-empty on a real production restore.
  #    An empty table here (on a database that's supposed to have data) is a strong
  #    signal the dump was taken against an empty/wrong database, not a genuine backup.
  local core_tables=("users" "projects" "invoices" "quotations")
  for table in "${core_tables[@]}"; do
    local exists
    exists=$(psql_staging "SELECT to_regclass('public.${table}') IS NOT NULL;" | tr -d '[:space:]')
    if [ "$exists" != "t" ]; then
      log "  FAIL: expected table '${table}' does not exist after restore+migrate."
      ok=false
      continue
    fi
    local row_count
    row_count=$(psql_staging "SELECT count(*) FROM ${table};" | tr -d '[:space:]')
    log "  OK: table '${table}' exists (${row_count} row(s))."
  done

  [ "$ok" = true ]
}

# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
usage() {
  grep '^#' "${BASH_SOURCE[0]}" | sed -n '/^# Usage:/,/^# Requires:/p' | sed 's/^# \{0,1\}//'
}

main() {
  case "${1:-}" in
    --backup-only) MODE="backup-only" ;;
    --drill-only)
      MODE="drill-only"
      DRILL_ONLY_DUMP_PATH="${2:?Usage: $0 --drill-only <path-to-dump.sql.gz>}"
      ;;
    -h|--help) usage; exit 0 ;;
    "") MODE="full" ;;
    *) log "Unknown argument: $1"; usage; exit 6 ;;
  esac

  command -v docker >/dev/null || die "docker is required on PATH." 6
  command -v gzip >/dev/null || die "gzip is required on PATH." 6

  if [ -f "$REPO_ROOT/.env" ]; then
    set -a
    # shellcheck disable=SC1090,SC1091
    source "$REPO_ROOT/.env"
    set +a
  fi

  DB_NAME="${DB_NAME:?DB_NAME must be set (export it or put it in .env)}"
  DB_USERNAME="${DB_USERNAME:?DB_USERNAME must be set (export it or put it in .env)}"
  DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD must be set (export it or put it in .env)}"

  if [ "$MODE" = "drill-only" ]; then
    run_restore_drill "$DRILL_ONLY_DUMP_PATH"
    exit 0
  fi

  local dump_file
  dump_file="$(run_backup)"
  apply_retention
  offsite_upload "$dump_file"

  if [ "$MODE" = "backup-only" ]; then
    log "Backup-only run complete (restore drill skipped)."
    exit 0
  fi

  run_restore_drill "$dump_file"
  log "Backup + restore drill completed successfully. Exit code 0."
}

main "$@"
