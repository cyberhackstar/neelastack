# Deploying Neelastack to the Oracle VM (Cloudflare Tunnel, neelastack.com)

This documents the *actual* topology on `backend` (the Oracle VM), and what changed in
this repo to fit it. Read this before your first deploy — it explains why the stack no
longer touches port 80/443 or runs certbot.

## Topology on this VM

The VM already runs other apps behind the same `cloudflared` tunnel:

| Cloudflare hostname          | Tunnel target             | What serves it                              |
|-------------------------------|----------------------------|----------------------------------------------|
| `api-electromart.bhawesh.shop`| `http://localhost:8082`   | `electromart-backend` container              |
| `api-apparel.bhawesh.shop`    | `http://localhost:8083`   | `apparel-backend` container                  |
| `apparel.bhawesh.shop`        | `http://localhost:80`     | host-level nginx (`sites-enabled/apparel-frontend`) |
| `bhawesh.shop`                 | `http://localhost:80`     | same host-level nginx, different `server_name` |
| **`neelastack.com`**           | **`http://localhost:4000`** | **this stack's `nginx` container**          |

Two things fall out of that last row:

1. **Only one port is tunneled for neelastack.com: 4000.** There's no
   `api-neelastack.bhawesh.shop`-style route, so the app can't rely on a separate API
   subdomain. The frontend already calls the API via a same-origin relative path
   (`/api/v1`, see `frontend/src/environments/environment.prod.ts`), and this stack's
   `nginx` container is what makes `/api/*` on port 4000 actually reach the backend.
2. **Cloudflare, not this VM, terminates public TLS.** cloudflared talks plain HTTP to
   `localhost:4000`. Anything this stack does with certbot/443 would be redundant at
   best and would break the tunnel at worst (cloudflared doesn't speak TLS to the
   origin here).

The original `docker-compose.prod.yml`/`infra/nginx/neelastack.conf` in this repo
assumed the opposite: a dedicated box with its own nginx owning ports 80/443 and
certbot issuing its own certificate. That's been changed (see "What changed" below) to
match this VM instead.

## What changed in this repo for this VM

- **`docker-compose.prod.yml`**: the `nginx` service now binds
  `127.0.0.1:4000:80` only — nothing else in the stack publishes a port. The
  `certbot` service is gone entirely.
- **`infra/nginx/neelastack.conf`**: single `listen 80` server block (the container's
  internal port 80, mapped to host 4000). No `443` block, no ACME challenge location,
  no `301` redirect to HTTPS (that would break the plain-HTTP hop from cloudflared).
  `X-Forwarded-Proto` is hardcoded to `https` since anything reaching this listener has
  already come through Cloudflare's HTTPS edge.
- **`backend/src/main/resources/application.yml`**: added
  `server.forward-headers-strategy: framework` so Spring trusts that
  `X-Forwarded-Proto: https` header — this matters concretely for Google OAuth2 login,
  which builds its redirect URI from the request scheme.
- **`.env.example`**: `CORS_ALLOWED_ORIGINS` no longer lists `www.neelastack.com` (not
  in the tunnel yet) and the unused `API_BASE_URL=https://api.neelastack.com/...` line
  was removed — the frontend never actually reads it at runtime (only Playwright e2e
  tests use `API_BASE_URL`, and CI sets that directly, not from `.env`).
- **`infra/deploy/deploy.sh`**: the health-check gate now also waits on
  `neelastack-nginx` (it has a real healthcheck now), and the smoke test checks
  `127.0.0.1:4000` directly in addition to the public `https://neelastack.com` URL, so
  a Cloudflare/DNS hiccup can't mask (or be mistaken for) an actual app failure.

Why not just reuse the host's existing nginx instead of running one in this stack?
Because that nginx already serves `apparel.bhawesh.shop`/`bhawesh.shop` on port 80 and
isn't tunneled for `neelastack.com` (the tunnel goes straight to `:4000`). Keeping
neelastack's proxy in its own container on its own loopback port keeps it fully
isolated from the other apps — a bad deploy or config change here can't touch them,
and vice versa.

## One-time VM setup

```bash
# on the VM
sudo mkdir -p /opt/neelastack
sudo chown ubuntu:ubuntu /opt/neelastack
cd /opt/neelastack
git clone <your-repo-url> .
cp .env.example .env
nano .env   # fill in every value — see the checklist below
```

`.env` checklist (all required unless noted):

- `DB_PASSWORD`, `REDIS_PASSWORD` — strong, unique, not reused from the other apps'
  `shared-postgres`/`shared-redis`. This stack runs its **own** Postgres/Redis
  containers (`neelastack-postgres`, `neelastack-redis`) on their own network
  (`neelastack-net`) — deliberately not the `shared-network` the other apps use, so a
  problem in one app's data layer can't affect another's.
- `GITHUB_REPOSITORY_OWNER` — your GitHub username/org, lowercase, matching what
  `.github/workflows/ci-cd.yml` pushes to on GHCR.
- `JWT_SECRET` — `openssl rand -base64 48` or similar, 256-bit minimum.
- `MFA_ENCRYPTION_KEY` — `openssl rand -base64 32` (must decode to exactly 32 bytes).
- `CLOUDINARY_AUTH_TOKEN_KEY` — any long random string; enable "Token-based
  authentication" in the Cloudinary console first.
- `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` — used once, on first boot only,
  to create the first admin account. Change the password (and enroll MFA) immediately
  after first login.
- `RAZORPAY_*` — live keys, plus the webhook secret from the Razorpay dashboard once
  you've registered `https://neelastack.com/api/v1/payments/webhook`
  (`PaymentWebhookController`) as the webhook URL.
- `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` — from Google Cloud Console. Authorized
  redirect URI: `https://neelastack.com/login/oauth2/code/google`.
- `CORS_ALLOWED_ORIGINS`, `SITE_BASE_URL`, `FRONTEND_URL` — leave as `https://neelastack.com`
  unless you've also added a `www.neelastack.com` route to the tunnel.

**Never commit `.env`.** Confirm `.gitignore` covers it before your first commit on
this box.

### Add the deploy key + known_hosts to GitHub (for CI/CD)

The `deploy` job in `.github/workflows/ci-cd.yml` SSHes into this VM. In the repo's
GitHub Settings → Secrets and variables → Actions, set:

- `DEPLOY_HOST` — `ubuntu@<vm-public-ip-or-hostname>`
- `DEPLOY_SSH_KEY` — a private key whose public half is in this VM's
  `~/.ssh/authorized_keys` (don't reuse `backend_private.key` used for interactive
  login — mint a dedicated deploy key: `ssh-keygen -t ed25519 -f deploy_key -N ""`)
- `DEPLOY_KNOWN_HOSTS` — run `ssh-keyscan <vm-ip>` **from your own machine** and paste
  the output verbatim. Don't accept whatever the CI runner sees over the network.

## First deploy (manual, before wiring CI/CD)

```bash
cd /opt/neelastack
export GITHUB_REPOSITORY_OWNER=<your-lowercase-owner>
docker compose -f docker-compose.prod.yml pull   # requires images already pushed to GHCR
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps      # wait for all 5 to show healthy
```

Then verify locally on the VM, and from outside via the tunnel:

```bash
curl -s http://127.0.0.1:4000/api/v1/public/ping   # backend, via nginx, via loopback
curl -s https://neelastack.com/api/v1/public/ping   # same, via Cloudflare
curl -sI https://neelastack.com/                    # frontend SSR
```

From then on, pushes to `main` build+push GHCR images and call
`infra/deploy/deploy.sh <sha>` on this VM automatically, which health-checks and
smoke-tests the new version and auto-rolls-back on failure (see that script's own
header comment).

## Notes on "fast, reliable, secure" for this setup specifically

- **Fast**: nginx caches rendered SSR HTML for 60s with stale-while-revalidate up to an
  hour (`infra/nginx/neelastack.conf`), so most public page views never hit Node or
  Postgres at all. Static JS/CSS/images get long-lived immutable cache headers from
  the Angular build output already.
- **Reliable**: `deploy.sh` won't leave a broken version live — it gates on container
  health *and* an HTTP smoke test (both local and public), and rolls back automatically
  if either fails. `restart: unless-stopped` on every service means a VM reboot brings
  everything back without manual intervention.
- **Secure**: nginx sets HSTS/CSP/frame/nosniff headers; the app's containers publish
  no ports except nginx's, and that one only on loopback — nothing here is reachable
  except through the Cloudflare Tunnel, mirroring how the other apps on this box are
  already set up. Secrets live only in `.env` (never committed) and container env vars,
  never baked into images.

## Known gaps carried over from IMPLEMENTATION-STATUS.md

Deploying doesn't fix these — they're still open and worth tracking separately: no
frontend MFA UI, no login-time TOTP challenge, no superadmin force-reset role,
incomplete Playwright E2E coverage.
