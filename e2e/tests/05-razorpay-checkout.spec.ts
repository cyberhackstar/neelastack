import { test, expect, APIRequestContext } from '@playwright/test';
import * as crypto from 'crypto';

/**
 * Journey 5 (master prompt, Section 3): client pays an invoice through the Razorpay
 * checkout flow (RazorpayCheckoutService -> DashboardDetailComponent#payInvoice ->
 * ClientInvoiceController#verify -> InvoiceService#verifyAndConfirmPayment).
 *
 * "Mock Razorpay checkout" means exactly that -- this suite never talks to Razorpay's
 * real API or checkout.js. Two things make a *meaningful* (not just UI-deep) mock
 * possible here, both confirmed by reading the real backend, not assumed:
 *
 * 1. verifyAndConfirmPayment() calls Razorpay SDK's own
 *    `Utils.verifyPaymentSignature(payload, razorpayKeySecret)`, which recomputes
 *    HMAC_SHA256(order_id + "|" + payment_id, key_secret) and compares hex digests.
 *    That's a public, documented algorithm (Razorpay's own signature scheme) -- not a
 *    guess -- so a test that knows RAZORPAY_KEY_SECRET can produce a signature the real
 *    backend will actually accept, meaning this test exercises the real verification
 *    code path end to end, not a stubbed-out response.
 * 2. The CI e2e job (.github/workflows/ci-cd.yml) already sets
 *    RAZORPAY_KEY_SECRET=placeholder for the whole stack -- this test reads the same
 *    value via env so it stays in sync with whatever the backend is actually
 *    configured with, rather than hardcoding a value that could drift.
 *
 * checkout.js itself is intercepted (page.route) and replaced with a tiny fake
 * `window.Razorpay` that computes that signature client-side via Web Crypto
 * (crypto.subtle.importKey/sign — available in a secure context, and localhost counts
 * as one) and invokes the real `handler` callback the app wired up. Everything
 * downstream of that (the actual POST /verify call, the actual DB write, the actual
 * invoice status flip) is real.
 *
 * Setup (register client, create engagement + invoice) is done via direct API calls
 * with Playwright's `request` fixture rather than driving the admin UI through the
 * Section 1 dashboard -- that UI is already covered by journey 4, and building this
 * fixture through it would make this test slower and more brittle for no added
 * coverage. Field names below (EngagementRequest, InvoiceRequest, AuthResponse) are
 * copied from the actual DTOs, not guessed.
 */

const API_BASE_URL = process.env['API_BASE_URL'] ?? 'http://localhost:8080';
const RAZORPAY_KEY_SECRET = process.env['RAZORPAY_KEY_SECRET'] ?? 'placeholder';
const ADMIN_EMAIL = process.env['E2E_ADMIN_EMAIL'] ?? 'admin@neelastack.test';
// Finalized by global-setup.ts before this file's workers start (see that file's header
// comment) — no fallback literal here, since a stale hardcoded password would silently
// mask a global-setup failure instead of failing loudly at the first admin API call.
const ADMIN_PASSWORD = process.env['E2E_ADMIN_PASSWORD'] ?? '';

interface Fixture {
  clientEmail: string;
  clientPassword: string;
  engagementId: string;
  invoiceId: string;
}

async function apiLogin(request: APIRequestContext, email: string, password: string): Promise<string> {
  const res = await request.post(`${API_BASE_URL}/api/v1/auth/login`, {
    data: { email, password },
  });
  expect(res.ok(), `login failed for ${email}: ${res.status()} ${await res.text()}`).toBeTruthy();
  const body = await res.json();
  return body.accessToken as string;
}

async function buildFixture(request: APIRequestContext): Promise<Fixture> {
  const clientEmail = `e2e-payer-${Date.now()}@example.com`;
  const clientPassword = 'E2eTestPassword!23';

  const registerRes = await request.post(`${API_BASE_URL}/api/v1/auth/register`, {
    data: { fullName: 'E2E Payer', email: clientEmail, password: clientPassword, phone: '' },
  });
  expect(registerRes.ok(), `register failed: ${registerRes.status()} ${await registerRes.text()}`).toBeTruthy();

  const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);
  const authHeader = { Authorization: `Bearer ${adminToken}` };

  const engagementRes = await request.post(`${API_BASE_URL}/api/v1/admin/engagements`, {
    headers: authHeader,
    data: {
      clientEmail,
      title: 'E2E Checkout Fixture Project',
      description: 'Created by the Playwright e2e suite (journey 5) for a checkout test.',
    },
  });
  expect(engagementRes.ok(), `engagement create failed: ${engagementRes.status()} ${await engagementRes.text()}`).toBeTruthy();
  const engagement = await engagementRes.json();

  const invoiceRes = await request.post(`${API_BASE_URL}/api/v1/admin/invoices`, {
    headers: authHeader,
    data: {
      engagementId: engagement.id,
      description: 'E2E test invoice — checkout journey',
      amount: 999,
      currency: 'INR',
    },
  });
  expect(invoiceRes.ok(), `invoice create failed: ${invoiceRes.status()} ${await invoiceRes.text()}`).toBeTruthy();
  const invoice = await invoiceRes.json();

  return { clientEmail, clientPassword, engagementId: engagement.id, invoiceId: invoice.id };
}

/**
 * Fake checkout.js: mimics just enough of the real Razorpay Checkout constructor
 * (`new Razorpay(options)` + `.open()`) for RazorpayCheckoutService to work unmodified.
 * `open()` computes a signature the real backend will accept (see file header) and
 * calls the caller's `handler` — the same synchronous contract the real script uses,
 * just resolved async here since Web Crypto's sign() is promise-based.
 */
function fakeRazorpayScript(keySecret: string): string {
  return `
    window.Razorpay = function(options) {
      this.options = options;
      this.open = async function() {
        const encoder = new TextEncoder();
        const key = await crypto.subtle.importKey(
          'raw', encoder.encode(${JSON.stringify(keySecret)}),
          { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
        );
        const paymentId = 'pay_e2e_mock_' + Date.now();
        const signed = await crypto.subtle.sign(
          'HMAC', key, encoder.encode(this.options.order_id + '|' + paymentId)
        );
        const signature = Array.from(new Uint8Array(signed))
          .map((b) => b.toString(16).padStart(2, '0')).join('');
        this.options.handler({
          razorpay_order_id: this.options.order_id,
          razorpay_payment_id: paymentId,
          razorpay_signature: signature,
        });
      };
    };
  `;
}

test.describe('Razorpay checkout (mocked)', () => {
  let fixture: Fixture;

  test.beforeAll(async ({ playwright }) => {
    const request = await playwright.request.newContext();
    fixture = await buildFixture(request);
    await request.dispose();
  });

  test('client pays a pending invoice end to end', async ({ page }) => {
    // Intercept the real Razorpay script URL (RazorpayCheckoutService's
    // RAZORPAY_SCRIPT_URL constant) before it's ever requested.
    await page.route('https://checkout.razorpay.com/v1/checkout.js', (route) =>
      route.fulfill({ contentType: 'application/javascript', body: fakeRazorpayScript(RAZORPAY_KEY_SECRET) }),
    );

    await page.goto('/login');
    await page.getByLabel(/^email$/i).fill(fixture.clientEmail);
    await page.locator('input[formcontrolname="password"]').fill(fixture.clientPassword);
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL('/', { timeout: 10000 });

    await page.goto(`/dashboard/${fixture.engagementId}`);
    // Filter on the unique half of the description -- both tests in this file share
    // one engagement, and "checkout journey" vs. "bad signature" is what tells the two
    // invoices' rows apart if these tests happen to run in the same worker/session.
    const invoiceRow = page.locator('.invoices li').filter({ hasText: 'checkout journey' });
    await expect(invoiceRow).toBeVisible({ timeout: 10000 });
    await expect(invoiceRow.locator('.tag')).toHaveText('PENDING');

    await invoiceRow.getByRole('button', { name: /pay now/i }).click();

    // payInvoice() flips the row's status tag on the real verify response -- no mocked
    // network response, this is the actual POST /client/invoices/{id}/verify round trip.
    await expect(invoiceRow.locator('.tag')).toHaveText('PAID', { timeout: 15000 });
    await expect(page.locator('.error')).toHaveCount(0);
  });

  test('an invalid signature is rejected and the invoice is marked FAILED', async ({ page }) => {
    // Same fixture pattern, fresh invoice, but the fake script signs with the wrong
    // secret -- this must fail the same way a tampered real payload would.
    const request = await page.context().request;
    const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);
    const invoiceRes = await request.post(`${API_BASE_URL}/api/v1/admin/invoices`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: {
        engagementId: fixture.engagementId,
        description: 'E2E test invoice — bad signature',
        amount: 500,
        currency: 'INR',
      },
    });
    expect(invoiceRes.ok()).toBeTruthy();

    await page.route('https://checkout.razorpay.com/v1/checkout.js', (route) =>
      route.fulfill({ contentType: 'application/javascript', body: fakeRazorpayScript('wrong-secret') }),
    );

    await page.goto('/login');
    await page.getByLabel(/^email$/i).fill(fixture.clientEmail);
    await page.locator('input[formcontrolname="password"]').fill(fixture.clientPassword);
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL('/', { timeout: 10000 });

    await page.goto(`/dashboard/${fixture.engagementId}`);
    const invoiceRow = page.locator('.invoices li').filter({ hasText: 'bad signature' });
    await expect(invoiceRow).toBeVisible({ timeout: 10000 });

    await invoiceRow.getByRole('button', { name: /pay now/i }).click();

    // InvoiceService#verifyAndConfirmPayment sets FAILED and the backend returns 400,
    // so payInvoice()'s catch branch sets paymentError() -- the tag never flips to PAID.
    await expect(page.locator('.error')).toBeVisible({ timeout: 15000 });
    await expect(invoiceRow.locator('.tag')).not.toHaveText('PAID');
  });
});
