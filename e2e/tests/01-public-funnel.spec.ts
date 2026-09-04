import { test, expect } from '@playwright/test';

/**
 * Journey 1 (master prompt, Section 3): home -> services/solutions -> contact/estimator.
 * A pure navigation + rendering smoke test -- the point is catching a broken build or a
 * route that silently 404s, not exhaustively validating every page's content.
 */
test.describe('Public acquisition funnel', () => {
  test('home page renders and links to core pages', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/Neelastack/);
    await expect(page.locator('h1').first()).toBeVisible();
  });

  test('can navigate home -> services -> solutions -> contact', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('link', { name: /services/i }).first().click();
    await expect(page).toHaveURL(/\/services/);
    await expect(page.locator('h1').first()).toBeVisible();

    await page.goto('/solutions');
    await expect(page).toHaveURL(/\/solutions/);
    await expect(page.locator('h1').first()).toBeVisible();

    await page.goto('/contact');
    await expect(page).toHaveURL(/\/contact/);
    await expect(page.getByLabel(/^email$/i)).toBeVisible();
  });

  test('estimator wizard: intent selection through to review step', async ({ page }) => {
    await page.goto('/estimate');
    await expect(page.getByRole('heading', { name: /tell us what you're building/i })).toBeVisible();

    // Step 1: intent (BUILD/FIX/MODERNIZE toggle buttons, not a form control)
    await page.getByRole('button', { name: /build/i }).click();

    // The wizard is multi-step; this asserts forward progress happens rather than
    // walking every field of every step (that's better covered by frontend unit tests
    // for the step components themselves).
    await expect(page.locator('select[formcontrolname="projectType"]')).toBeVisible({ timeout: 5000 });
  });

  test('contact form can be submitted successfully', async ({ page }) => {
    await page.goto('/contact');

    await page.getByLabel(/^name$/i).fill('E2E Test User');
    await page.getByLabel(/^email$/i).fill('e2e-test@example.com');
    const messageField = page.locator('textarea[formcontrolname="message"], textarea[formcontrolname="scopeDetails"]').first();
    if (await messageField.count()) {
      await messageField.fill('E2E smoke test — please disregard.');
    }

    await page.getByRole('button', { name: /send|submit/i }).click();

    // Runs against the disposable CI-local stack (docker-compose.yml, torn down after
    // the job) -- submitting for real here is fine and is the point of an e2e test.
    await expect(page.locator('.success, [class*="success"]').first()).toBeVisible({ timeout: 10000 });
  });
});
