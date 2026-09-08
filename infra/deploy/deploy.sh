#!/usr/bin/env bash
#
# Deploys a specific image tag, waits for containers to report healthy, runs a LOCAL
# HTTP smoke test against nginx on this box, and automatically rolls back to the last
# known-good tag if either check fails. A separate PUBLIC smoke test (through
# Cloudflare) also runs but is non-blocking — see public_smoke_test() below for why.
#
# This closes audit items #4 (SHA-pinned deploys), #5 (smoke-test/rollback gate), and
# #17 (public/external checks must not gate an internal rollback decision): the deploy
# step used to be just `pull && up -d` against `:latest`, with no verification that the
# new version actually works before traffic hits it, no way to get back to a working
# state other than SSHing in by hand, and (once a smoke test existed) a public-only
# outage (Cloudflare/DNS) could trigger a rollback of an otherwise-healthy deploy.
#
# Usage: ./deploy.sh <image-tag>
#   <image-tag> is normally the git commit SHA that CI just built and pushed.
#
# Requires: docker compose v2, curl. Run from the directory containing
# docker-compose.prod.yml and .env (this is how the existing CI deploy job already
# invokes it — see .github/workflows/ci-cd.yml).
#
# NOTE on database migrations: this script's rollback only reverts the APPLICATION
# image, never the database schema (Flyway migrations are one-way). "Roll back" here
# means "old app image + whatever schema the new image's migrations already applied",
# not "old app + old schema". See docs/DATABASE-MIGRATIONS.md for the expand/contract
# policy that keeps that combination safe.
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

# GitHub Actions authenticates to GHCR on the *runner* (docker/login-action in
# ci-cd.yml) so it can push the images it just built. That credential lives only on
# the runner -- it never reaches this VM. If GHCR_PACKAGES_PRIVATE is unset/true and
# the packages are private, `docker compose pull` below fails with an auth error
# unless this box has its own GHCR login. Rather than relying on a one-time manual
# `docker login ghcr.io` done by hand once and forgotten (which silently breaks the
# next time the credential/token expires), log in explicitly on every deploy using a
# read:packages-scoped token kept in .env -- so an expired/missing credential fails
# loudly here, at deploy time, with a clear fix, instead of as a confusing pull
# failure with no context.
ghcr_login_if_configured() {
  if [ -z "${GHCR_USERNAME:-}" ] || [ -z "${GHCR_PAT:-}" ]; then
    log "GHCR_USERNAME/GHCR_PAT not set in .env -- skipping explicit GHCR login."
    log "This is fine if the GHCR packages are public, or if this host already has a"
    log "long-lived 'docker login ghcr.io' credential cached in ~/.docker/config.json."
    log "If the pull below fails with 401/403, set GHCR_USERNAME + GHCR_PAT"
    log "(a GitHub PAT with the read:packages scope) in .env and re-run."
    return 0
  fi
  log "Logging in to ghcr.io as ${GHCR_USERNAME}..."
  if ! echo "${GHCR_PAT}" | docker login ghcr.io --username "${GHCR_USERNAME}" --password-stdin; then
    log "GHCR login failed -- check GHCR_USERNAME/GHCR_PAT in .env (PAT may be expired"
    log "or missing the read:packages scope)."
    return 1
  fi
}

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
  # (127.0.0.1:4000). This is the HARD deployment gate: it only depends on this box's
  # own containers, so it can't be masked or falsely tripped by anything outside this
  # host (Cloudflare, DNS, the tunnel). A failure here means the new image itself is
  # broken and MUST trigger a rollback.
  _check "local: backend ping" "http://127.0.0.1:4000/api/v1/public/ping" "200"
  _check "local: frontend SSR" "http://127.0.0.1:4000/"                   "200"

  return "$failures"
}

public_smoke_test() {
  # Public checks confirm the whole path actually works end-to-end through the
  # Cloudflare Tunnel — reachability, SSR actually rendering (not just the Node
  # process being "up"), and the two SEO-critical static routes nginx proxies through
  # to the backend. Deliberately NOT part of the pass/fail deploy gate (see
  # smoke_test() above): Cloudflare or DNS having a bad day is an external
  # infrastructure condition, not evidence the new application image is broken, and
  # must never by itself trigger a rollback of a perfectly healthy deploy. Failures
  # here are logged loudly so a human notices and can check Cloudflare/DNS directly,
  # but they never affect this script's exit code.
  local failures=0

  _check() {
    local desc="$1" url="$2" expect="$3"
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$url" || echo "000")
    if [ "$code" != "$expect" ]; then
      log "WARN (non-blocking): $desc -> expected $expect, got $code ($url)"
      failures=$((failures + 1))
    else
      log "OK: $desc ($code)"
    fi
  }

  _check "public: backend ping" "$SITE_BASE_URL/api/v1/public/ping" "200"
  _check "public: frontend SSR" "$SITE_BASE_URL/"                    "200"
  _check "public: sitemap.xml"  "$SITE_BASE_URL/sitemap.xml"         "200"
  _check "public: robots.txt"   "$SITE_BASE_URL/robots.txt"          "200"

  if [ "$failures" -gt 0 ]; then
    log "$failures public smoke check(s) failed -- deploy is NOT being rolled back for"
    log "this alone (see public_smoke_test() comment). Investigate Cloudflare/DNS/tunnel"
    log "if this persists."
  fi
}

deploy_tag() {
  local tag="$1"
  log "Deploying image tag: $tag"
  IMAGE_TAG="$tag" docker compose --env-file .env -f "$COMPOSE_FILE" pull
  IMAGE_TAG="$tag" docker compose --env-file .env -f "$COMPOSE_FILE" up -d
}

previous_tag=""
if [ -f "$LAST_GOOD_FILE" ]; then
  previous_tag="$(cat "$LAST_GOOD_FILE")"
fi

ghcr_login_if_configured

deploy_tag "$IMAGE_TAG"

if wait_for_healthy && smoke_test; then
  echo "$IMAGE_TAG" > "$LAST_GOOD_FILE"
  log "Deploy of $IMAGE_TAG succeeded and passed local smoke tests."
  public_smoke_test || true
  docker image prune -f >/dev/null
  exit 0
fi

log "Deploy of $IMAGE_TAG failed health checks or local smoke tests."

if [ -n "$previous_tag" ] && [ "$previous_tag" != "$IMAGE_TAG" ]; then
  log "Rolling back to last known-good tag: $previous_tag"
  deploy_tag "$previous_tag"
  if wait_for_healthy && smoke_test; then
    log "Rollback to $previous_tag succeeded. The bad tag ($IMAGE_TAG) never stayed live."
    public_smoke_test || true
    exit 1
  else
    log "ROLLBACK ALSO FAILED. Site may be down. Manual intervention required immediately."
    exit 2
  fi
else
  log "No previous known-good tag on record ($LAST_GOOD_FILE missing or same as failing tag) — cannot roll back automatically. Manual intervention required."
  exit 2
fi
