import { test, expect } from '@playwright/test';

/**
 * Journey 3 (master prompt, Section 3): dashboard-list/dashboard-detail,
 * ClientEngagementController/ClientInvoiceController. Registers a throwaway client
 * account against the disposable CI-local stack (no seed data needed) rather than
 * relying on a fixture user existing in the DB, then verifies the authenticated
 * dashboard route actually renders. AuthService#login does not gate on emailVerified,
 * so this doesn't need to click through an email verification link.
 */
test.describe('Client portal dashboard', () => {
  const email = `e2e-client-${Date.now()}@example.com`;
  const password = 'E2eTestPassword!23';

  test('register -> login -> dashboard renders (empty state for a fresh account)', async ({ page }) => {
    await page.goto('/register');
    await page.locator('input[formcontrolname="fullName"]').fill('E2E Client');
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.locator('input[formcontrolname="password"]').fill(password);
    await page.getByRole('button', { name: /create account|register|sign up/i }).click();

    // Correction: RegisterComponent#submit always navigates to '/' on success (it does
    // not redirect to /dashboard or /login) -- confirmed directly in
    // register.component.ts. The original version of this test waited on a URL
    // register.component.ts never produces and would have hung/timed out in CI.
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


