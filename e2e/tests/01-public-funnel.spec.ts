import { test, expect } from '@playwright/test';

/**
 * Journey 1 (master prompt, Section 3): home -> services/solutions -> contact/estimator.
 * A pure navigation + rendering smoke test -- the point is catching a broken build or a
 * route that silently 404s, not exhaustively validating every page's content.
 */
test.describe('Public acquisition funnel', () => {
  test('home page renders and links to core pages', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveTitle(/Neelastack/);
    await expect(page.locator('h1').first()).toBeVisible();
  });

  test('primary navigation exposes an explicit Home link', async ({ page }) => {
    await page.goto('/services', { waitUntil: 'domcontentloaded' });
    const home = page.getByRole('link', { name: /^home$/i }).first();
    await expect(home).toBeVisible();
    await home.click();
    await expect(page).toHaveURL(/\/$/);
    await expect(page.locator('h1').first()).toBeVisible();
  });

  test('can navigate home -> services -> solutions -> contact', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    await page.getByRole('link', { name: /services/i }).first().click();
    await expect(page).toHaveURL(/\/services/);
    await expect(page.locator('h1').first()).toBeVisible();

    await page.goto('/solutions', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/solutions/);
    await expect(page.locator('h1').first()).toBeVisible();

    await page.goto('/contact', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/contact/);
    await expect(page.getByLabel("Email", { exact: true })).toBeVisible();
  });

  test('estimator wizard: intent selection through to review step', async ({ page }) => {
    await page.goto('/estimate', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /tell us what you're building/i })).toBeVisible();

    // Step 1: intent (BUILD/FIX/MODERNIZE toggle buttons, not a form control)
    await page.getByRole('button', { name: /build/i }).click();
    await page.getByRole('button', { name: /Next/i }).click();

    // The wizard is multi-step; this asserts forward progress happens rather than
    // walking every field of every step (that's better covered by frontend unit tests
    // for the step components themselves).
    await expect(page.locator('select[formcontrolname="projectType"]')).toBeVisible({ timeout: 5000 });
  });

  test('contact form can be submitted successfully', async ({ page }) => {
    await page.goto('/contact', { waitUntil: 'domcontentloaded' });

    await page.getByLabel("Name", { exact: true }).fill('E2E Test User');
    await page.getByLabel("Email", { exact: true }).fill('e2e-test@example.com');
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








test.describe('Business acquisition audit', () => {
  test('free business audit validates, scores and unlocks the full report', async ({ page }) => {
    await page.goto('/free-business-audit', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /see how strong your business is online/i })).toBeVisible();

    await page.getByRole('button', { name: /show my digital score/i }).click();
    const industrySelect = page.getByLabel('Industry');
    await expect(industrySelect).toBeVisible();
    await expect(industrySelect).toHaveValue('');

    await industrySelect.selectOption({ label: 'Gym / fitness business' });
    await expect(industrySelect).toHaveValue('Gym / fitness business');
    await page.getByLabel('Website presence').selectOption({ label: 'Basic / outdated website' });
    await page.getByLabel('Customer action').selectOption({ label: 'Mostly offline' });
    await page.getByLabel('Lead capture').selectOption({ label: 'Manual / unclear' });
    await page.getByLabel('Local discovery').selectOption({ label: 'Weak / unsure' });
    await page.getByLabel('Primary goal').selectOption({ label: 'Get more customers' });

    const scorePromise = page.waitForResponse(
      (response) => response.url().includes('/api/v1/public/business-audit/score') && response.request().method() === 'POST',
      { timeout: 20000 },
    );
    await page.getByRole('button', { name: /show my digital score/i }).click();
    const scoreResponse = await scorePromise;
    expect(scoreResponse.status()).toBe(200);
    await expect(page.getByRole('heading', { name: /room to grow|needs attention|solid foundation/i })).toBeVisible();

    await page.getByRole('button', { name: /get my full business report/i }).click();
    await page.getByLabel('Name', { exact: true }).fill('E2E Audit User');
    await page.getByLabel('Email', { exact: true }).fill('e2e-business-audit@example.com');
    await page.getByLabel('Company', { exact: true }).fill('E2E Business');
    await page.getByLabel('City', { exact: true }).fill('Jaipur');

    const unlockPromise = page.waitForResponse(
      (response) => response.url().includes('/api/v1/public/business-audit/unlock') && response.request().method() === 'POST',
      { timeout: 20000 },
    );
    await page.getByRole('button', { name: /unlock my report/i }).click();
    const unlockResponse = await unlockPromise;
    expect(unlockResponse.status()).toBe(201);
    await expect(page.getByText('YOUR BUSINESS REPORT', { exact: false })).toBeVisible();
    await expect(page.getByText(/\d+\/100/)).toBeVisible();
  });
});

test.describe('Public form validation and scheduling', () => {
  test('contact form explains invalid required fields instead of silently doing nothing', async ({ page }) => {
    await page.goto('/contact', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /send project brief/i }).click();
    await expect(page.getByText('Your name is required.')).toBeVisible();
    await expect(page.getByText('Email address is required.')).toBeVisible();
    await expect(page.getByText(/tell us a little about the project/i)).toBeVisible();
  });

  test('schedule a call CTA opens the booking flow', async ({ page }) => {
    await page.goto('/contact', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /schedule a call/i }).click();
    await expect(page).toHaveURL(/\/book$/);
    await expect(page.getByRole('heading', { name: /choose a time to talk/i })).toBeVisible();
  });

  test('mobile form focus zoom resets after leaving the field', async ({ page }) => {
    await page.goto('/contact', { waitUntil: 'domcontentloaded' });
    const viewport = page.locator('meta[name="viewport"]');
    const original = await viewport.getAttribute('content');
    const email = page.getByLabel('Email', { exact: true });
    await email.focus();
    await email.blur();
    await page.waitForTimeout(450);
    await expect(viewport).toHaveAttribute('content', original ?? 'width=device-width, initial-scale=1');
  });

  test('auth forms show inline validation feedback', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /^sign in$/i }).click();
    await expect(page.getByText('Email address is required.')).toBeVisible();
    await expect(page.getByText('Password is required.')).toBeVisible();

    await page.goto('/register', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /create account/i }).click();
    await expect(page.getByText('Full name is required.')).toBeVisible();
    await expect(page.getByText('Email address is required.')).toBeVisible();
    await expect(page.getByText('Password is required.')).toBeVisible();

    await page.goto('/forgot-password', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /send reset link/i }).click();
    await expect(page.getByText('Email address is required.')).toBeVisible();
  });
});
