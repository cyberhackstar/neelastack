import { request as playwrightRequest } from "@playwright/test";
import * as dotenv from "dotenv";
import * as fs from "fs";
import * as path from "path";
import * as OTPAuth from "otpauth";

dotenv.config({ path: path.resolve(process.cwd(), "../.env") });

const API_BASE_URL = process.env["API_BASE_URL"] ?? "http://localhost:8080";

const BOOTSTRAP_EMAIL =
  process.env["E2E_ADMIN_EMAIL"] ??
  process.env["ADMIN_BOOTSTRAP_EMAIL"] ??
  "admin@neelastack.com";

const BOOTSTRAP_PASSWORD =
  process.env["E2E_ADMIN_PASSWORD"] ??
  process.env["ADMIN_BOOTSTRAP_PASSWORD"];

const FINAL_ADMIN_PASSWORD =
  process.env["E2E_FINAL_ADMIN_PASSWORD"] ?? "E2ePostBootstrap!Passw0rd23";

// Persisted only on the machine running the suite (gitignored), so a local dev
// environment whose Postgres volume survives between runs doesn't hit
// "MFA is already enabled" on the second run with no way to recover the secret it
// enrolled the first time. In CI the Postgres volume is created fresh every run, so
// this file is simply absent and a new secret gets enrolled + persisted every time.
const MFA_SECRET_CACHE_PATH = path.resolve(__dirname, ".e2e-admin-mfa-secret.json");

interface AuthResponseLike {
  accessToken?: string;
  mustChangePassword?: boolean;
  mfaRequired?: boolean;
  mfaToken?: string;
}

function currentTotpCode(base32Secret: string): string {
  const totp = new OTPAuth.TOTP({
    algorithm: "SHA1",
    digits: 6,
    period: 30,
    secret: OTPAuth.Secret.fromBase32(base32Secret),
  });
  return totp.generate();
}

function loadCachedSecret(email: string): string | undefined {
  try {
    const cache = JSON.parse(fs.readFileSync(MFA_SECRET_CACHE_PATH, "utf-8"));
    return cache[email];
  } catch {
    return undefined;
  }
}

function saveCachedSecret(email: string, secret: string): void {
  let cache: Record<string, string> = {};
  try {
    cache = JSON.parse(fs.readFileSync(MFA_SECRET_CACHE_PATH, "utf-8"));
  } catch {
    // no existing cache file -- fine, start fresh
  }
  cache[email] = secret;
  fs.writeFileSync(MFA_SECRET_CACHE_PATH, JSON.stringify(cache, null, 2));
}

/**
 * Global setup for the whole suite (runs once, in the main Playwright process, before
 * any worker is forked -- env vars set here on `process.env` are inherited by every
 * worker). Brings the configured admin account from "freshly bootstrapped" to
 * "ready for a real, security-model-compliant test run":
 *
 *   1. Wait for the backend to be healthy.
 *   2. Log in with the bootstrap password.
 *   3. If mustChangePassword is set (always true for a brand-new bootstrap account --
 *      see AdminBootstrapRunner / MustChangePasswordFilter), change it.
 *   4. Ensure MFA is enrolled (POST /admin/mfa/setup + /verify), so every spec file can
 *      assume the account is already enrolled and only needs to step up, not enroll.
 *
 * Deliberately does NOT perform a step-up here: the step-up TTL (default 10 minutes)
 * is too short to assume it will still be valid by the time individual spec files run,
 * especially with retries/sharding. Each spec file's fixture calls stepUp() itself
 * (see tests/helpers/admin-auth.ts) immediately before any high-risk mutation.
 */
export default async function globalSetup(): Promise<void> {
  if (!BOOTSTRAP_PASSWORD) {
    throw new Error(
      "global-setup: no E2E admin password configured. " +
        "Set E2E_ADMIN_PASSWORD or ADMIN_BOOTSTRAP_PASSWORD.",
    );
  }

  const context = await playwrightRequest.newContext();

  try {
    let healthy = false;

    for (let i = 0; i < 60; i++) {
      try {
        const r = await context.get(`${API_BASE_URL}/actuator/health`);
        if (r.ok()) {
          healthy = true;
          break;
        }
      } catch {}

      await new Promise((resolve) => setTimeout(resolve, 1000));
    }

    if (!healthy) {
      throw new Error(
        `global-setup: backend did not become healthy at ${API_BASE_URL}`,
      );
    }

    let workingPassword: string;

    const bootstrapLogin = await context.post(
      `${API_BASE_URL}/api/v1/auth/login`,
      { data: { email: BOOTSTRAP_EMAIL, password: BOOTSTRAP_PASSWORD } },
    );

    if (bootstrapLogin.ok()) {
      const body: AuthResponseLike = await bootstrapLogin.json();

      if (body.mustChangePassword) {
        const changeRes = await context.post(
          `${API_BASE_URL}/api/v1/auth/change-password`,
          {
            headers: { Authorization: `Bearer ${body.accessToken}` },
            data: {
              currentPassword: BOOTSTRAP_PASSWORD,
              newPassword: FINAL_ADMIN_PASSWORD,
            },
          },
        );

        if (!changeRes.ok()) {
          throw new Error(
            `global-setup: mandatory password change failed ` +
              `(${changeRes.status()}): ${await changeRes.text()}`,
          );
        }

        workingPassword = FINAL_ADMIN_PASSWORD;
      } else {
        // Bootstrap password already IS the working password (e.g. a previous local
        // run already changed it and this one matches -- unlikely, but handled).
        workingPassword = BOOTSTRAP_PASSWORD;
      }
    } else {
      // Bootstrap password didn't work -- most likely because a previous run already
      // changed it. Try the final password instead.
      const finalLogin = await context.post(
        `${API_BASE_URL}/api/v1/auth/login`,
        { data: { email: BOOTSTRAP_EMAIL, password: FINAL_ADMIN_PASSWORD } },
      );

      if (!finalLogin.ok() && (await finalLogin.json()).mfaRequired !== true) {
        throw new Error(
          `global-setup: admin login failed with both bootstrap and final ` +
            `credentials. Bootstrap returned ${bootstrapLogin.status()}; ` +
            `final returned ${finalLogin.status()}: ${await finalLogin.text()}`,
        );
      }

      workingPassword = FINAL_ADMIN_PASSWORD;
    }

    process.env["E2E_ADMIN_EMAIL"] = BOOTSTRAP_EMAIL;
    process.env["E2E_ADMIN_PASSWORD"] = workingPassword;

    // --- MFA enrollment -----------------------------------------------------
    // A freshly-changed-password login might itself now come back with
    // mfaRequired=true if a previous run already enrolled MFA on this account
    // (persisted Postgres volume) -- handle that by reusing the cached secret and
    // skipping straight to "already enrolled".
    let totpSecret = loadCachedSecret(BOOTSTRAP_EMAIL);

    const probeLogin = await context.post(`${API_BASE_URL}/api/v1/auth/login`, {
      data: { email: BOOTSTRAP_EMAIL, password: workingPassword },
    });

    if (!probeLogin.ok()) {
      throw new Error(
        `global-setup: post-password-change login failed unexpectedly ` +
          `(${probeLogin.status()}): ${await probeLogin.text()}`,
      );
    }

    const probeBody: AuthResponseLike = await probeLogin.json();

    if (probeBody.mfaRequired) {
      if (!totpSecret) {
        throw new Error(
          `global-setup: admin ${BOOTSTRAP_EMAIL} already has MFA enabled but no ` +
            `cached TOTP secret was found at ${MFA_SECRET_CACHE_PATH}. This happens ` +
            `if that file was deleted after a previous run enrolled MFA against a ` +
            `Postgres volume that's still around. Either restore the cache file or ` +
            `reset the local database (e.g. \`docker compose down -v\`) and re-run.`,
        );
      }
      // Already enrolled and we have the secret -- nothing further to do here.
      // (accessToken from this login is intentionally discarded; spec files log in
      // fresh via loginAdmin()/loginAdminWithStepUp() as needed.)
    } else {
      // Not enrolled yet -- enroll now, once, for the whole suite.
      const accessToken = probeBody.accessToken;

      const setupRes = await context.post(
        `${API_BASE_URL}/api/v1/admin/mfa/setup`,
        { headers: { Authorization: `Bearer ${accessToken}` } },
      );

      if (!setupRes.ok()) {
        throw new Error(
          `global-setup: MFA setup failed (${setupRes.status()}): ` +
            `${await setupRes.text()}`,
        );
      }

      const setupBody = await setupRes.json();
      totpSecret = setupBody.manualEntrySecret as string;

      const verifyRes = await context.post(
        `${API_BASE_URL}/api/v1/admin/mfa/verify`,
        {
          headers: { Authorization: `Bearer ${accessToken}` },
          data: { code: currentTotpCode(totpSecret) },
        },
      );

      if (!verifyRes.ok()) {
        throw new Error(
          `global-setup: MFA verify (enrollment) failed ` +
            `(${verifyRes.status()}): ${await verifyRes.text()}`,
        );
      }

      saveCachedSecret(BOOTSTRAP_EMAIL, totpSecret);
    }

    process.env["E2E_ADMIN_TOTP_SECRET"] = totpSecret;
  } finally {
    await context.dispose();
  }
}
