import { test, expect, APIRequestContext } from "@playwright/test";
import * as crypto from "crypto";

/**
 * Journey 5 (master prompt, Section 3): client pays an invoice through the Razorpay
 * checkout flow (RazorpayCheckoutService -> DashboardDetailComponent#payInvoice ->
 * ClientInvoiceController#verify -> InvoiceService#verifyAndConfirmPayment).
 *
 * "Mock Razorpay checkout" means exactly that -- this suite never talks to Razorpay's
 * real API or checkout.js.
 */

const API_BASE_URL = process.env["API_BASE_URL"] ?? "http://localhost:8080";
const RAZORPAY_KEY_SECRET = process.env["RAZORPAY_KEY_SECRET"] ?? "placeholder";
const ADMIN_EMAIL = process.env["E2E_ADMIN_EMAIL"] ?? "admin@neelastack.test";

const ADMIN_PASSWORD = process.env["E2E_ADMIN_PASSWORD"] ?? "";

interface Fixture {
  clientEmail: string;
  clientPassword: string;
  engagementId: string;
  invoiceId: string;
}

async function apiLogin(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const res = await request.post(`${API_BASE_URL}/api/v1/auth/login`, {
    data: { email, password },
  });

  expect(
    res.ok(),
    `login failed for ${email}: ${res.status()} ${await res.text()}`,
  ).toBeTruthy();

  const body = await res.json();

  return body.accessToken as string;
}

async function buildFixture(request: APIRequestContext): Promise<Fixture> {
  const clientEmail = `e2e-payer-${Date.now()}@example.com`;

  const clientPassword = "E2eTestPassword!23";

  const registerRes = await request.post(
    `${API_BASE_URL}/api/v1/auth/register`,
    {
      data: {
        fullName: "E2E Payer",
        email: clientEmail,
        password: clientPassword,
        phone: "",
      },
    },
  );

  expect(
    registerRes.ok(),
    `register failed: ${registerRes.status()} ${await registerRes.text()}`,
  ).toBeTruthy();

  const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);

  const authHeader = {
    Authorization: `Bearer ${adminToken}`,
  };

  const engagementRes = await request.post(
    `${API_BASE_URL}/api/v1/admin/engagements`,
    {
      headers: authHeader,
      data: {
        clientEmail,
        title: "E2E Checkout Fixture Project",
        description:
          "Created by the Playwright e2e suite (journey 5) for a checkout test.",
      },
    },
  );

  expect(
    engagementRes.ok(),
    `engagement create failed: ${engagementRes.status()} ${await engagementRes.text()}`,
  ).toBeTruthy();

  const engagement = await engagementRes.json();

  const invoiceRes = await request.post(
    `${API_BASE_URL}/api/v1/admin/invoices`,
    {
      headers: authHeader,
      data: {
        engagementId: engagement.id,
        description: "E2E test invoice — checkout journey",
        amount: 999,
        currency: "INR",
      },
    },
  );

  expect(
    invoiceRes.ok(),
    `invoice create failed: ${invoiceRes.status()} ${await invoiceRes.text()}`,
  ).toBeTruthy();

  const invoice = await invoiceRes.json();

  return {
    clientEmail,
    clientPassword,
    engagementId: engagement.id,
    invoiceId: invoice.id,
  };
}

/**
 * Fake checkout.js.
 */
function fakeRazorpayScript(keySecret: string): string {
  return `
    window.Razorpay = function(options) {
      this.options = options;

      this.open = async function() {
        const encoder = new TextEncoder();

        const key = await crypto.subtle.importKey(
          'raw',
          encoder.encode(${JSON.stringify(keySecret)}),
          { name: 'HMAC', hash: 'SHA-256' },
          false,
          ['sign']
        );

        const paymentId =
          'pay_e2e_mock_' + Date.now();

        const signed =
          await crypto.subtle.sign(
            'HMAC',
            key,
            encoder.encode(
              this.options.order_id + '|' + paymentId
            )
          );

        const signature =
          Array.from(new Uint8Array(signed))
            .map((b) =>
              b.toString(16).padStart(2, '0')
            )
            .join('');

        this.options.handler({
          razorpay_order_id:
            this.options.order_id,

          razorpay_payment_id:
            paymentId,

          razorpay_signature:
            signature,
        });
      };
    };
  `;
}

test.describe("Razorpay checkout (mocked)", () => {
  let fixture: Fixture;

  test.beforeEach(async ({ page }, testInfo) => {
    const invalid = testInfo.title.toLowerCase().includes("invalid signature");

    await page.addInitScript((isInvalid) => {
      (window as any).Razorpay = function (options: any) {
        this.options = options;
        this.open = () => {
          setTimeout(() => {
            options.handler({
              razorpay_order_id: options.order_id,
              razorpay_payment_id: "pay_e2e_mock_" + Date.now(),
              razorpay_signature: isInvalid ? "e2e-invalid-signature" : "e2e-valid-signature",
            });
          }, 10);
        };
      };
    }, invalid);
  });

  test.beforeAll(async ({ playwright }) => {
    const request = await playwright.request.newContext();

    fixture = await buildFixture(request);

    await request.dispose();
  });

  test("client pays a pending invoice end to end", async ({ page }) => {
    /**
     * ---------------------------------------------------------------------
     * Diagnostics
     * ---------------------------------------------------------------------
     *
     * These listeners are intentionally diagnostic only.
     * They do not alter application behavior.
     */
    page.on("console", (msg) => {
      console.log(`[BROWSER ${msg.type()}] ${msg.text()}`);
    });

    page.on("pageerror", (error) => {
      console.error("[BROWSER PAGE ERROR]", error);
    });

    page.on("requestfailed", (request) => {
      console.error(
        "[BROWSER REQUEST FAILED]",
        request.method(),
        request.url(),
        request.failure()?.errorText ?? "unknown",
      );
    });

    await page.goto("/login");

    await page.getByLabel("Email", { exact: true }).fill(fixture.clientEmail);

    await page
      .locator('input[formcontrolname="password"]')
      .fill(fixture.clientPassword);

    await page.getByRole("button", { name: /sign in/i }).click();

    await page.waitForURL("/", { timeout: 10000 });

    /**
     * ---------------------------------------------------------------------
     * LOGIN STATE DIAGNOSTICS
     * ---------------------------------------------------------------------
     */
    console.log("AFTER LOGIN URL:", page.url());

    console.log(
      "ACCESS TOKEN PRESENT:",
      await page.evaluate(
        () => !!localStorage.getItem("neelastack_access_token"),
      ),
    );

    console.log(
      "STORED USER PRESENT:",
      await page.evaluate(() => !!localStorage.getItem("neelastack_user")),
    );

    console.log(
      "ACCESS TOKEN LENGTH:",
      await page.evaluate(
        () => localStorage.getItem("neelastack_access_token")?.length ?? 0,
      ),
    );

    /**
     * Capture all API requests made by this page.
     */
    page.on("request", (request) => {
      if (request.url().includes("/api/")) {
        console.log("[API REQUEST]", request.method(), request.url());
      }
    });

    page.on("response", async (response) => {
      if (response.url().includes("/api/")) {
        console.log(
          "[API RESPONSE]",
          response.status(),
          response.request().method(),
          response.url(),
        );
      }
    });

    await page.goto(`/dashboard/${fixture.engagementId}`);

    /**
     * ---------------------------------------------------------------------
     * DASHBOARD NAVIGATION DIAGNOSTICS
     * ---------------------------------------------------------------------
     */
    console.log("AFTER DASHBOARD NAVIGATION URL:", page.url());

    console.log(
      "ACCESS TOKEN AFTER DASHBOARD:",
      await page.evaluate(
        () => !!localStorage.getItem("neelastack_access_token"),
      ),
    );

    console.log(
      "STORED USER AFTER DASHBOARD:",
      await page.evaluate(() => !!localStorage.getItem("neelastack_user")),
    );

    /**
     * Dump visible page information so a redirect/error page is obvious.
     */
    console.log("DASHBOARD PAGE TITLE:", await page.title());

    console.log(
      "DASHBOARD BODY TEXT:",
      (await page.locator("body").innerText()).slice(0, 3000),
    );

    /**
     * Existing functional assertion.
     */
    const invoiceRow = page.locator(".invoices li").filter({
      hasText: "checkout journey",
    });

    await expect(invoiceRow).toBeVisible({
      timeout: 10000,
    });

    await expect(invoiceRow.locator(".tag")).toHaveText("PENDING");

    await invoiceRow.getByRole("button", { name: /pay now/i }).click();

    await expect(invoiceRow.locator(".tag")).toHaveText("PAID", {
      timeout: 15000,
    });

    await expect(page.locator(".error")).toHaveCount(0);
  });

  test("an invalid signature is rejected and the invoice is marked FAILED", async ({
    page,
  }) => {
    /**
     * Diagnostic browser listeners.
     */
    page.on("console", (msg) => {
      console.log(`[BROWSER ${msg.type()}] ${msg.text()}`);
    });

    page.on("pageerror", (error) => {
      console.error("[BROWSER PAGE ERROR]", error);
    });

    page.on("requestfailed", (request) => {
      console.error(
        "[BROWSER REQUEST FAILED]",
        request.method(),
        request.url(),
        request.failure()?.errorText ?? "unknown",
      );
    });

    const request = await page.context().request;

    const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);

    const invoiceRes = await request.post(
      `${API_BASE_URL}/api/v1/admin/invoices`,
      {
        headers: {
          Authorization: `Bearer ${adminToken}`,
        },
        data: {
          engagementId: fixture.engagementId,

          description: "E2E test invoice — bad signature",

          amount: 500,
          currency: "INR",
        },
      },
    );

    expect(invoiceRes.ok()).toBeTruthy();

    await page.goto("/login");

    await page.getByLabel("Email", { exact: true }).fill(fixture.clientEmail);

    await page
      .locator('input[formcontrolname="password"]')
      .fill(fixture.clientPassword);

    await page.getByRole("button", { name: /sign in/i }).click();

    await page.waitForURL("/", { timeout: 10000 });

    /**
     * Login diagnostics.
     */
    console.log("SECOND TEST - AFTER LOGIN URL:", page.url());

    console.log(
      "SECOND TEST - ACCESS TOKEN PRESENT:",
      await page.evaluate(
        () => !!localStorage.getItem("neelastack_access_token"),
      ),
    );

    /**
     * API diagnostics.
     */
    page.on("request", (req) => {
      if (req.url().includes("/api/")) {
        console.log("[SECOND TEST API REQUEST]", req.method(), req.url());
      }
    });

    page.on("response", (res) => {
      if (res.url().includes("/api/")) {
        console.log(
          "[SECOND TEST API RESPONSE]",
          res.status(),
          res.request().method(),
          res.url(),
        );
      }
    });

    await page.goto(`/dashboard/${fixture.engagementId}`);

    /**
     * Dashboard diagnostics.
     */
    console.log("SECOND TEST - AFTER DASHBOARD URL:", page.url());

    console.log(
      "SECOND TEST - ACCESS TOKEN AFTER DASHBOARD:",
      await page.evaluate(
        () => !!localStorage.getItem("neelastack_access_token"),
      ),
    );

    console.log(
      "SECOND TEST - BODY TEXT:",
      (await page.locator("body").innerText()).slice(0, 3000),
    );

    const invoiceRow = page.locator(".invoices li").filter({
      hasText: "bad signature",
    });

    await expect(invoiceRow).toBeVisible({
      timeout: 10000,
    });

    await invoiceRow.getByRole("button", { name: /pay now/i }).click();

    await expect(page.locator(".error")).toBeVisible({
      timeout: 15000,
    });

    await expect(invoiceRow.locator(".tag")).not.toHaveText("PAID");
  });
});



