#!/usr/bin/env bash
#
# Deploys a specific image tag, waits for containers to report healthy, runs a public
# HTTP smoke test against the live site, and automatically rolls back to the last
# known-good tag if either check fails.
#
# This closes audit items #4 (SHA-pinned deploys) and #5 (smoke-test/rollback gate):
# previously the deploy step was just `pull && up -d` against `:latest`, with no
# verification that the new version actually works before traffic hits it, and no way
# to get back to a working state other than SSHing in by hand.
#
# Usage: ./deploy.sh <image-tag>
#   <image-tag> is normally the git commit SHA that CI just built and pushed.
#
# Requires: docker compose v2, curl. Run from the directory containing
# docker-compose.prod.yml and .env (this is how the existing CI deploy job already
# invokes it — see .github/workflows/ci-cd.yml).
#
# Exit codes:
#   0 - new tag deployed and passed all checks
#   1 - new tag failed checks, but rollback to the previous tag succeeded
#   2 - new tag failed checks AND rollback failed (or there was nothing to roll back
#       to) — this means the site may currently be down and needs a human immediately

set -euo pipefail

IMAGE_TAG="${1:?Usage: deploy.sh <image-tag>}"
COMPOSE_FILE="docker-compose.prod.yml"
LAST_GOOD_FILE=".last_good_sha"
HEALTH_TIMEOUT_SECONDS=180
HEALTH_POLL_INTERVAL=5
CONTAINERS_TO_CHECK=(neelastack-postgres neelastack-redis neelastack-backend neelastack-frontend neelastack-nginx)

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

SITE_BASE_URL="${SITE_BASE_URL:-https://neelastack.com}"

log() { echo "[deploy] $(date -u '+%Y-%m-%dT%H:%M:%SZ') $*"; }

wait_for_healthy() {
  local elapsed=0
  while [ "$elapsed" -lt "$HEALTH_TIMEOUT_SECONDS" ]; do
    local all_healthy=true
    for c in "${CONTAINERS_TO_CHECK[@]}"; do
      local status
      status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$c" 2>/dev/null || echo "missing")
      # "no-healthcheck" is deliberately NOT accepted here: a container with no healthcheck
      # at all means this gate can't actually tell whether it's serving traffic correctly, so
      # treating that as a pass would make the gate a no-op for that container. Every
      # container in CONTAINERS_TO_CHECK is expected to define a real healthcheck (postgres
      # and redis via docker-compose.prod.yml, backend and frontend via their own
      # Dockerfiles) — if one is ever missing, this should fail loudly, not silently.
      if [ "$status" != "healthy" ]; then
        all_healthy=false
        log "  waiting: $c -> $status"
      fi
    done
    if [ "$all_healthy" = true ]; then
      log "All checked containers report healthy."
      return 0
    fi
    sleep "$HEALTH_POLL_INTERVAL"
    elapsed=$((elapsed + HEALTH_POLL_INTERVAL))
  done
  log "Timed out after ${HEALTH_TIMEOUT_SECONDS}s waiting for containers to become healthy."
  return 1
}

smoke_test() {
  local failures=0

  _check() {
    local desc="$1" url="$2" expect="$3"
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$url" || echo "000")
    if [ "$code" != "$expect" ]; then
      log "FAIL: $desc -> expected $expect, got $code ($url)"
      failures=$((failures + 1))
    else
      log "OK: $desc ($code)"
    fi
  }

  # Local checks hit nginx directly on the loopback port cloudflared talks to
  # (127.0.0.1:4000) — these catch a broken deploy even if Cloudflare or DNS is
  # having a bad day, so a public-side outage never masks (or gets confused with) an
  # actual application failure on this box.
  _check "local: backend ping" "http://127.0.0.1:4000/api/v1/public/ping" "200"
  _check "local: frontend SSR" "http://127.0.0.1:4000/"                   "200"

  # Public checks confirm the whole path actually works end-to-end through the
  # Cloudflare Tunnel — reachability, SSR actually rendering (not just the Node
  # process being "up"), and the two SEO-critical static routes nginx proxies
  # through to the backend.
  _check "public: backend ping" "$SITE_BASE_URL/api/v1/public/ping" "200"
  _check "public: frontend SSR" "$SITE_BASE_URL/"                    "200"
  _check "public: sitemap.xml"  "$SITE_BASE_URL/sitemap.xml"         "200"
  _check "public: robots.txt"   "$SITE_BASE_URL/robots.txt"          "200"

  return "$failures"
}

deploy_tag() {
  local tag="$1"
  log "Deploying image tag: $tag"
  IMAGE_TAG="$tag" docker compose -f "$COMPOSE_FILE" pull
  IMAGE_TAG="$tag" docker compose -f "$COMPOSE_FILE" up -d
}

previous_tag=""
if [ -f "$LAST_GOOD_FILE" ]; then
  previous_tag="$(cat "$LAST_GOOD_FILE")"
fi

deploy_tag "$IMAGE_TAG"

if wait_for_healthy && smoke_test; then
  echo "$IMAGE_TAG" > "$LAST_GOOD_FILE"
  log "Deploy of $IMAGE_TAG succeeded and passed smoke tests."
  docker image prune -f >/dev/null
  exit 0
fi

log "Deploy of $IMAGE_TAG failed health checks or smoke tests."

if [ -n "$previous_tag" ] && [ "$previous_tag" != "$IMAGE_TAG" ]; then
  log "Rolling back to last known-good tag: $previous_tag"
  deploy_tag "$previous_tag"
  if wait_for_healthy && smoke_test; then
    log "Rollback to $previous_tag succeeded. The bad tag ($IMAGE_TAG) never stayed live."
    exit 1
  else
    log "ROLLBACK ALSO FAILED. Site may be down. Manual intervention required immediately."
    exit 2
  fi
else
  log "No previous known-good tag on record ($LAST_GOOD_FILE missing or same as failing tag) — cannot roll back automatically. Manual intervention required."
  exit 2
fi
