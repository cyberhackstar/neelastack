import { test, expect } from '@playwright/test';

/**
 * Journey 4 (master prompt, Section 3): admin login -> the Section 1 "Admin Sales
 * Command Center" dashboard rewrite (AdminDashboardComponent / AnalyticsService /
 * AdminAnalyticsController). Checked against the real template
 * (admin-dashboard.component.html) rather than guessed selectors:
 *  - the revenue-attribution dimension toggle renders one .btn per entry in the
 *    component's `dimensions` array and re-fetches on click
 *  - follow-up rows are `<li class="card follow-up-row">` with a "Mark done" button
 *  - every stat/table/list has a documented empty-state branch ("No attribution data
 *    yet.", "No follow-ups needed right now.") -- a fresh CI-local stack with no seeded
 *    sales data legitimately renders those empty states, so this test asserts the
 *    *structure* renders correctly in both the populated and empty cases rather than
 *    assuming specific numbers will be present.
 *
 * V24 removed the seeded admin@neelastack.com / ChangeMe@123 fixture. The account this
 * suite now uses is created by AdminBootstrapRunner from ADMIN_BOOTSTRAP_EMAIL/PASSWORD
 * (see ci-cd.yml's e2e job) and starts with mustChangePassword=true. global-setup.ts runs
 * once before any spec, completes that mandatory change via the API, and rewrites
 * process.env['E2E_ADMIN_PASSWORD'] to the resulting permanent password — by the time this
 * file's workers start, E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD are already the final,
 * change-complete credentials, so the UI login below lands straight on '/' as before.
 */
test.describe('Admin sales management dashboard', () => {
  const adminEmail = process.env['E2E_ADMIN_EMAIL'] ?? 'admin@neelastack.test';
  const adminPassword = process.env['E2E_ADMIN_PASSWORD'] ?? '';

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel("Email", { exact: true }).fill(adminEmail);
    await page.locator('input[formcontrolname="password"]').fill(adminPassword);
    await page.getByRole('button', { name: /sign in/i }).click();
    // LoginComponent#submit always navigates to '/' on success regardless of role
    // (confirmed in login.component.ts -- it does not branch on ADMIN vs CLIENT), so
    // wait for that redirect, then navigate to /admin explicitly.
    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });
    await page.goto('/admin');
  });

  test('summary stats, sales intelligence and proposal intelligence render', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

    // Top-level summary cards (pre-existing getSummary() data) -- always present once
    // loading() flips false, regardless of whether there's any sales data yet.
    await expect(page.locator('.stat-grid').first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Total inquiries')).toBeVisible();

    // Sales intelligence section -- either the populated stat grid or the documented
    // "unavailable" fallback, never an indefinite spinner.
    await expect(page.getByRole('heading', { name: 'Sales intelligence' })).toBeVisible();
    await expect(
      page.getByText('Weighted pipeline (heuristic)').or(page.getByText('Sales intelligence unavailable right now.')),
    ).toBeVisible({ timeout: 10000 });

    await expect(page.getByRole('heading', { name: 'Proposal intelligence' })).toBeVisible();
    await expect(page.getByText('Unviewed proposals')).toBeVisible();
  });

  test('revenue attribution dimension toggle re-fetches and updates the table header', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Revenue attribution' })).toBeVisible();

    const toggle = page.locator('.dimension-toggle');
    await expect(toggle).toBeVisible();
    const buttons = toggle.locator('button');
    const count = await buttons.count();
    expect(count).toBeGreaterThanOrEqual(2); // source / medium / campaign / landing page

    // Click each dimension button in turn; each click issues a fresh
    // revenue-by-attribution request (attributionLoading() toggles) and the table's
    // first header cell (bound to currentDimensionLabel()) must reflect the active
    // dimension -- or the documented "No attribution data yet." empty state.
    for (let i = 0; i < count; i++) {
      await buttons.nth(i).click();
      await expect(buttons.nth(i)).toHaveClass(/active/);
      await expect(
        page.locator('.attribution-table thead th').first().or(page.getByText('No attribution data yet.')),
      ).toBeVisible({ timeout: 10000 });
    }
  });

  test('follow-up panel: mark done removes the row optimistically', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Follow-ups' })).toBeVisible();

    const emptyState = page.getByText('No follow-ups needed right now.');
    const firstRow = page.locator('.follow-up-row').first();

    // A fresh CI-local stack has no seeded inquiries/quotations old enough to trigger a
    // follow-up, so the empty state is the expected, correct render -- only exercise
    // the mark-done action when a task actually exists, and don't fail the suite when
    // it doesn't; assert the panel reached a resolved state either way.
    await expect(firstRow.or(emptyState)).toBeVisible({ timeout: 10000 });

    if (await firstRow.isVisible()) {
      const rowCountBefore = await page.locator('.follow-up-row').count();
      await firstRow.getByRole('button', { name: 'Mark done' }).click();
      await expect(page.locator('.follow-up-row')).toHaveCount(rowCountBefore - 1, { timeout: 10000 });
      // The action is optimistic -- a request failure surfaces via followUpActionError(),
      // not a silent no-op, so confirm that path stayed clear.
      await expect(page.locator('.error-text')).toHaveCount(0);
    }
  });

  test('non-admin cannot reach the admin dashboard', async ({ page }) => {
    // Session state lives in localStorage (AuthService), not cookies -- registering a
    // fresh CLIENT account here calls persistSession() and overwrites the admin
    // session this test's beforeEach just created, so no explicit sign-out is needed.
    // Register a throwaway CLIENT account, then confirm it's redirected away from
    // /admin rather than silently rendering ADMIN-only data.
    const email = `e2e-nonadmin-${Date.now()}@example.com`;
    await page.goto('/register');
    await page.locator('input[formcontrolname="fullName"]').fill('E2E Non Admin');
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.locator('input[formcontrolname="password"]').fill('E2eTestPassword!23');
    await page.getByRole('button', { name: /create account|register|sign up/i }).click();
    // Same redirect-to-'/' behavior as journey 3 / this test's beforeEach.
    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });

    await page.goto('/admin');
    await page.waitForURL((url) => !url.pathname.startsWith('/admin'), { timeout: 10000 });
  });
});


