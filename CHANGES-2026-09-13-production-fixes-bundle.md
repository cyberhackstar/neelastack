# Production fixes bundle — 2026-09-13

## Fixed in this bundle

### 1. Team-member upload 413 at Nginx
`infra/nginx/neelastack.conf` now sets `client_max_body_size 10m` at the server level.
This keeps the reverse proxy above the Spring Boot 10 MB multipart request limit, while the UI and service continue enforcing a 5 MB maximum for profile photos.

### 2. Better Team CMS error for oversized requests
The admin Team Members UI now recognizes HTTP 413 and shows a specific upload-size message instead of the generic `Could not save team member.` error.

### 3. Graceful expired/revoked admin sessions
When a protected API request returns 401 and the refresh-token attempt is unrecoverable, the auth interceptor now clears the local session and routes the browser to `/login` instead of leaving protected pages displaying a cascade of 401 errors.

### 4. Nginx config regression test in CI
CI now validates `infra/nginx/neelastack.conf` with the same `nginx:1.27-alpine` image used in production. Deployment depends on this validation job.

## Known separate issue
GitHub Actions run #61 being stuck in `Queued` is a GitHub-hosted runner/scheduling state, not an application source-code error. The successful #60 run and the workflow definition do not indicate an application failure for #61. A fresh GitHub Actions run should be used after the next commit; no source change can directly cancel a stuck GitHub-side run.

## Verification performed locally in this environment
- Workflow YAML parsed successfully.
- Production Nginx config contains the 10 MB request limit.
- No remaining application references to the nonexistent `/api/v1/admin/staff` endpoint were found; the remaining `/admin/staff` occurrence is the legitimate Angular admin route.
- Docker/Nginx runtime tests could not be executed here because Docker is not installed in this execution environment.

### 5. Fixed CI Nginx validation false failure
The CI `nginx-config-test` job previously ran the production Nginx config inside a standalone `nginx:1.27-alpine` container. Because that container was not on the production Compose network, Docker DNS could not resolve the intentional production upstream names `backend` and `frontend`, causing `nginx -t` to fail with `host not found in upstream "backend"` even though the production Compose topology is correct.

The validation container now maps `backend` and `frontend` to loopback solely for the static configuration test. This keeps the production config unchanged while allowing `nginx -t` to validate syntax successfully in CI.
