import { test, expect } from '@playwright/test';

const API_BASE_URL = process.env['API_BASE_URL'] ?? 'http://127.0.0.1:8080';

/**
 * Journey 3 (master prompt, Section 3): dashboard-list/dashboard-detail,
 * ClientEngagementController/ClientInvoiceController. Registers a throwaway client
 * account against the disposable CI-local stack (no seed data needed) rather than
 * relying on a fixture user existing in the DB, then verifies the authenticated
 * dashboard route actually renders.
 *
 * Security review P1 #1: /register no longer hands back a token pair, since an
 * unverified account previously got a working session before ever proving control of
 * the email address. This test now goes through the same steps a real user does --
 * register, land on /check-email, verify, then /login -- except it can't click a real
 * verification link (the disposable e2e stack has no inbox to check), so it verifies via
 * TestSupportController's force-verify-email endpoint, which only exists under
 * SPRING_PROFILES_ACTIVE=test.
 */
test.describe('Client portal dashboard', () => {
  const email = `e2e-client-${Date.now()}@example.com`;
  const password = 'E2eTestPassword!23';

  test('register -> verify -> login -> dashboard renders (empty state for a fresh account)', async ({
    page,
    request,
  }) => {
    await page.goto('/register');
    await page.locator('input[formcontrolname="fullName"]').fill('E2E Client');
    await page.getByLabel('Email', { exact: true }).fill(email);
    await page.locator('input[formcontrolname="password"]').fill(password);
    await page.getByRole('button', { name: /create account|register|sign up/i }).click();

    // RegisterComponent#submit now navigates to /check-email (with the email as a query
    // param) instead of straight into the app -- there's no session to land in yet.
    await expect(page).toHaveURL(/\/check-email/, { timeout: 10000 });

    const verifyRes = await request.post(
      `${API_BASE_URL}/api/v1/test-support/force-verify-email`,
      { data: { email } },
    );
    expect(verifyRes.ok()).toBeTruthy();

    await page.goto('/login');
    await page.getByLabel('Email', { exact: true }).fill(email);
    await page.locator('input[formcontrolname="password"]').fill(password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();

    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });
    await page.goto('/dashboard');
    await expect(page.getByRole('heading', { name: /dashboard|my projects/i })).toBeVisible();
    // A brand-new account has no engagements yet — the empty state is the correct,
    // expected render here, not a failure.
    await expect(page.locator('.card.empty, [class*="empty"]').first()).toBeVisible({ timeout: 10000 });
  });

  test('unauthenticated visit to /dashboard redirects to login', async ({ page, context }) => {
    await context.clearCookies();
    await page.goto('/dashboard');
    await page.waitForURL(/\/login/, { timeout: 10000 });
  });
});
