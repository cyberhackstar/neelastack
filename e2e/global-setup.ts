import { request as playwrightRequest } from '@playwright/test';

/**
 * Runs once, before any spec file, in a single process — deliberately not duplicated inside
 * each spec's beforeEach. Journeys 4 and 5 run in parallel CI workers (playwright.config.ts:
 * fullyParallel + workers: 2) and both need an admin session; doing the mandatory
 * post-bootstrap password change here avoids two workers racing to change the same
 * account's password via the UI at the same time.
 *
 * Why this exists at all: migration V24 deletes the old seeded admin@neelastack.com /
 * ChangeMe@123 fixture. AdminBootstrapRunner now creates exactly one admin from
 * ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD (see ci-cd.yml's e2e job) with
 * mustChangePassword=true. MustChangePasswordFilter then rejects every authenticated
 * request from that account — including the admin fixture-building calls in journey 5 —
 * until /api/v1/auth/change-password has been called once. This does exactly that and
 * hands both spec files the resulting, permanently-usable credentials.
 */

const API_BASE_URL = process.env['API_BASE_URL'] ?? 'http://localhost:8080';
const BOOTSTRAP_EMAIL = process.env['E2E_ADMIN_EMAIL'] ?? 'admin@neelastack.test';
const BOOTSTRAP_PASSWORD = process.env['E2E_ADMIN_PASSWORD'];
// The password the suite settles on after the mandatory change. Exported via
// process.env so spec files (in the same CI job, same shell) can read it back.
const FINAL_ADMIN_PASSWORD = 'E2ePostBootstrap!Passw0rd23';

export default async function globalSetup(): Promise<void> {
  if (!BOOTSTRAP_PASSWORD) {
    // Local/dev runs against a stack that was never bootstrapped this way (e.g. a
    // pre-existing admin created some other way) — nothing to do.
    return;
  }

  const context = await playwrightRequest.newContext();
  try {
    const loginRes = await context.post(`${API_BASE_URL}/api/v1/auth/login`, {
      data: { email: BOOTSTRAP_EMAIL, password: BOOTSTRAP_PASSWORD },
    });
    if (!loginRes.ok()) {
      throw new Error(
        `global-setup: bootstrap admin login failed (${loginRes.status()}): ${await loginRes.text()}`
      );
    }
    const body = await loginRes.json();

    if (body.mustChangePassword) {
      const changeRes = await context.post(`${API_BASE_URL}/api/v1/auth/change-password`, {
        headers: { Authorization: `Bearer ${body.accessToken}` },
        data: { currentPassword: BOOTSTRAP_PASSWORD, newPassword: FINAL_ADMIN_PASSWORD },
      });
      if (!changeRes.ok()) {
        throw new Error(
          `global-setup: mandatory password change failed (${changeRes.status()}): ${await changeRes.text()}`
        );
      }
      process.env['E2E_ADMIN_PASSWORD'] = FINAL_ADMIN_PASSWORD;
    }
    // else: a previous run (or a retried job) already completed the change — the
    // bootstrap password itself is already the final one, nothing further to do.
  } finally {
    await context.dispose();
  }
}
