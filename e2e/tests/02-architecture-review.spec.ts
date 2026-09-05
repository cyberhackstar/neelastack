import { test, expect } from '@playwright/test';

/**
 * Journey 2 (master prompt, Section 3): architecture-review.component.ts +
 * PublicArchitectureReviewController. Exercises the full form -> submit -> confirmation
 * path against the disposable CI-local stack.
 */
test.describe('Architecture review submission', () => {
  test('can submit a complete architecture review request', async ({ page }) => {
    await page.goto('/architecture-review');
    await expect(page.getByRole('heading', { name: /free architecture review/i })).toBeVisible();

    await page.getByLabel("Name", { exact: true }).fill('E2E Test User');
    await page.getByLabel("Email", { exact: true }).fill('e2e-arch-review@example.com');
    await page.locator('input[formcontrolname="applicationUrl"]').fill('https://example.com');
    await page.locator('textarea[formcontrolname="currentStack"]').fill(
      'Django + React monolith, ~5 years old, slow admin panel, no automated tests.',
    );

    await page.getByRole('button', { name: /submit|request|get.*review/i }).click();

    await expect(page.locator('.card.success, [class*="success"]').first()).toBeVisible({ timeout: 10000 });
  });

  test('rejects submission with missing required fields', async ({ page }) => {
    await page.goto('/architecture-review');
    // Submit with nothing filled in — Angular reactive-forms validation should block it
    // client-side (button disabled or form untouched), never reaching the backend with
    // an incomplete payload.
    const submit = page.getByRole('button', { name: /submit|request|get.*review/i });
    await submit.click();
    await expect(page.locator('.card.success, [class*="success"]')).toHaveCount(0);
  });
});


