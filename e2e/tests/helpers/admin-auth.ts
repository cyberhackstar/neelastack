import { APIRequestContext, expect } from "@playwright/test";
import * as OTPAuth from "otpauth";

/**
 * The admin account now goes through mustChangePassword -> MFA enrollment -> MFA
 * step-up before it can touch any high-risk endpoint (see MustChangePasswordFilter,
 * MfaService, StepUpAuthFilter on the backend). Every e2e fixture that needs an
 * authenticated ROLE_ADMIN bearer token for /api/v1/admin/** mutations must walk that
 * same real flow -- not bypass it -- or the test proves nothing about production
 * behavior. This module is the single place that flow lives, so every spec file
 * exercises it identically instead of re-implementing (and re-breaking) it.
 */

const API_BASE_URL = process.env["API_BASE_URL"] ?? "http://localhost:8080";

/** Generates the current 6-digit TOTP code for a base32 secret (SHA1/6-digit/30s -- matches MfaService). */
export function currentTotpCode(base32Secret: string): string {
  const totp = new OTPAuth.TOTP({
    algorithm: "SHA1",
    digits: 6,
    period: 30,
    secret: OTPAuth.Secret.fromBase32(base32Secret),
  });
  return totp.generate();
}

async function json(res: { status(): number; text(): Promise<string> }) {
  return JSON.parse(await res.text());
}

/**
 * Logs in an admin account whose password has already been changed away from the
 * one-time bootstrap password (see ensureAdminReady below, which is what actually
 * performs that change once via global-setup). Handles the MFA challenge redirect
 * (/auth/login -> mfaRequired -> /auth/login/mfa) transparently using the account's
 * TOTP secret.
 */
export async function loginAdmin(
  request: APIRequestContext,
  email: string,
  password: string,
  totpSecret: string,
): Promise<string> {
  const loginRes = await request.post(`${API_BASE_URL}/api/v1/auth/login`, {
    data: { email, password },
  });

  expect(
    loginRes.ok(),
    `admin login failed for ${email}: ${loginRes.status()} ${await loginRes.text()}`,
  ).toBeTruthy();

  const body = await json(loginRes);

  if (body.mustChangePassword) {
    throw new Error(
      `admin ${email} still has mustChangePassword=true -- global-setup.ts should have ` +
        `already cleared this before any spec file runs. Re-run global setup (or check ` +
        `E2E_ADMIN_PASSWORD actually reflects the post-change password).`,
    );
  }

  if (!body.mfaRequired) {
    // Account somehow isn't MFA-enrolled yet -- global-setup.ts should always enroll it
    // before tests start, so this only trips if that step silently failed.
    throw new Error(
      `admin ${email} logged in without an MFA challenge -- MFA enrollment ` +
        `(global-setup.ts) did not take effect. Cannot proceed with a step-up flow ` +
        `that has nothing to step up.`,
    );
  }

  const mfaRes = await request.post(`${API_BASE_URL}/api/v1/auth/login/mfa`, {
    data: {
      mfaToken: body.mfaToken,
      code: currentTotpCode(totpSecret),
      useRecoveryCode: false,
    },
  });

  expect(
    mfaRes.ok(),
    `MFA login completion failed for ${email}: ${mfaRes.status()} ${await mfaRes.text()}`,
  ).toBeTruthy();

  const mfaBody = await json(mfaRes);
  return mfaBody.accessToken as string;
}

/**
 * Refreshes the step-up assertion StepUpAuthFilter checks on high-risk mutations
 * (invoices, payments, pricing rules, mfa/disable, mfa/force-reset, sessions). Callers
 * should call this immediately before each such mutation rather than relying on a
 * step-up performed earlier in the test run to still be within its TTL window (default
 * 10 minutes, MFA_STEP_UP_TTL_MINUTES) -- see review item #24 on beforeAll-shared state.
 */
// In e2e/tests/helpers/admin-auth.ts

export async function stepUp(
  request: APIRequestContext,
  accessToken: string,
  totpSecret: string,
  retryCount = 0,
): Promise<void> {
  const res = await request.post(`${API_BASE_URL}/api/v1/admin/mfa/step-up`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { code: currentTotpCode(totpSecret) },
  });

  if (!res.ok() && res.status() === 400) {
    const body = await json(res);
    if (body.message?.includes("Too many MFA attempts") && retryCount < 3) {
      // Wait with exponential backoff: 10s, 20s, 40s
      const delayMs = (retryCount + 1) * 10000;
      console.log(`MFA rate limited, retrying in ${delayMs}ms...`);
      await new Promise((resolve) => setTimeout(resolve, delayMs));
      return stepUp(request, accessToken, totpSecret, retryCount + 1);
    }
  }

  expect(
    res.ok(),
    `MFA step-up failed: ${res.status()} ${await res.text()}`,
  ).toBeTruthy();
}

/** Bearer-auth header object for admin API calls, built once you have a token. */
export function authHeader(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

/**
 * Logs in with a fresh step-up already applied -- the common case for a fixture that's
 * about to immediately create an engagement/invoice/etc. Equivalent to loginAdmin()
 * followed by stepUp(), but callers doing several mutations spread out in time should
 * still call stepUp() again before each one rather than trust this one to still be
 * fresh.
 */
export async function loginAdminWithStepUp(
  request: APIRequestContext,
  email: string,
  password: string,
  totpSecret: string,
): Promise<string> {
  const accessToken = await loginAdmin(request, email, password, totpSecret);
  await stepUp(request, accessToken, totpSecret);
  return accessToken;
}

export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `${name} is not set -- global-setup.ts should have populated it before any ` +
        `spec file runs. Check playwright.config.ts's globalSetup is wired up.`,
    );
  }
  return value;
}
