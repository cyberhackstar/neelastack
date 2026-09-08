#!/usr/bin/env bash
#
# Resolves the current digest for each mutable infra image tag used in
# docker-compose.prod.yml (postgres, redis, nginx) and prints the docker-compose
# override needed to pin them by digest instead of by tag.
#
# Why this matters: `postgres:16-alpine`, `redis:7-alpine`, and `nginx:1.27-alpine`
# are ARM64-compatible today, but they're mutable tags — the alpine base layer under
# any of them can be rebuilt and repushed at any time, so a future `docker compose
# pull` on the VM can silently pick up a different image than the one that was tested.
# Pinning by digest makes "what's running" fully reproducible and auditable: the same
# digest always resolves to the exact same bytes.
#
# This is NOT run automatically as part of deploy.sh — pinning is a deliberate,
# reviewed action (a maintainer decides when to move to a newer base image), not
# something that should happen silently on every deploy. Run this by hand when you
# want to take a new pin, review the diff, commit it, then deploy as normal.
#
# Usage (run on any machine with Docker and registry access — does not need to be the
# Oracle VM):
#   ./infra/deploy/pin-images.sh
#
# Requires: docker CLI with buildx (for `docker buildx imagetools inspect`, which
# resolves a tag's digest for the ARM64 platform specifically without pulling the
# full image).

set -euo pipefail

PLATFORM="linux/arm64"

IMAGES=(
  "postgres:16-alpine"
  "redis:7-alpine"
  "nginx:1.27-alpine"
)

echo "# Resolved on $(date -u '+%Y-%m-%dT%H:%M:%SZ') for platform ${PLATFORM}."
echo "# Paste the corresponding 'image:' line into docker-compose.prod.yml, replacing"
echo "# the mutable tag, then commit this change and review it like any other."
echo

for image in "${IMAGES[@]}"; do
  echo "Resolving ${image}..." >&2

  digest="$(
    docker buildx imagetools inspect "${image}" --format '{{json .Manifest}}' 2>/dev/null \
      | python3 -c '
import json, sys
manifest = json.load(sys.stdin)
# Multi-platform index: find the arm64 entry. Single-platform manifest: use its own digest.
if "manifests" in manifest:
    for m in manifest["manifests"]:
        platform = m.get("platform", {})
        if platform.get("architecture") == "arm64":
            print(m["digest"])
            break
else:
    print(manifest["digest"])
' 2>/dev/null || true
  )"

  if [ -z "${digest:-}" ]; then
    echo "  Could not resolve a digest for ${image} — check registry access and that" >&2
    echo "  docker buildx is installed, then re-run. Skipping." >&2
    continue
  fi

  repo="${image%%:*}"
  echo "  ${repo}@${digest}" >&2
  echo "image: ${repo}@${digest}  # was ${image}"
done
