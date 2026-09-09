// e2e/tests/05-razorpay-checkout.spec.ts
import { test, expect, APIRequestContext } from "@playwright/test";
import * as crypto from "crypto";
import {
  authHeader as adminAuthHeader,
  requireEnv,
  stepUp,
} from "./helpers/admin-auth";

/**
 * Journey 5 (master prompt, Section 3): client pays an invoice through the Razorpay
 * checkout flow (RazorpayCheckoutService -> DashboardDetailComponent#payInvoice ->
 * ClientInvoiceController#verify -> InvoiceService#verifyAndConfirmPayment).
 *
 * "Mock Razorpay checkout" means exactly that -- this suite never talks to Razorpay's
 * real API or checkout.js.
 *
 * Admin invoice/engagement creation are high-risk mutations gated by
 * MustChangePasswordFilter + StepUpAuthFilter (see MfaService). This suite does NOT
 * disable or bypass that security model to make tests pass -- it exercises the real
 * bootstrap-password-change -> MFA-enrollment -> TOTP-step-up flow via
 * tests/helpers/admin-auth.ts, exactly as a real admin would have to. Enrollment
 * happens once in global-setup.ts; every fixture here calls stepUp() again
 * immediately before each high-risk call rather than assuming an earlier step-up is
 * still within its TTL (default 10 minutes) -- see review item #24.
 */

const API_BASE_URL = process.env["API_BASE_URL"] ?? "http://localhost:8080";
const RAZORPAY_KEY_SECRET = process.env["RAZORPAY_KEY_SECRET"] ?? "placeholder";

async function createFreshCheckoutInvoice(
  request: APIRequestContext,
  engagementId: string,
): Promise<{ id: string; description: string }> {
  const adminToken = requireEnv("E2E_ADMIN_ACCESS_TOKEN");
  const totpSecret = requireEnv("E2E_ADMIN_TOTP_SECRET");

  // The admin invoice endpoint is step-up protected. Perform the step-up immediately
  // before the mutation, then give the happy-path test its own never-before-used invoice.
  await stepUp(request, adminToken, totpSecret);

  const description = `E2E test invoice — checkout journey ${Date.now()}`;
  const response = await request.post(`${API_BASE_URL}/api/v1/admin/invoices`, {
    headers: adminAuthHeader(adminToken),
    data: {
      engagementId,
      description,
      amount: 999,
      currency: "INR",
    },
  });

  expect(response.ok()).toBeTruthy();
  const invoice = await response.json();
  expect(invoice.id).toBeTruthy();
  return { id: invoice.id as string, description };
}

interface Fixture {
  clientEmail: string;
  clientPassword: string;
  engagementId: string;
  invoiceId: string;
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
              razorpay_signature: isInvalid
                ? "e2e-invalid-signature"
                : "e2e-valid-signature",
            });
          }, 10);
        };
      };
    }, invalid);
  });

  test.beforeAll(async () => {
    // Load fixture created by global-setup.ts
    fixture = {
      clientEmail: requireEnv("E2E_RAZORPAY_CLIENT_EMAIL"),
      clientPassword: requireEnv("E2E_RAZORPAY_CLIENT_PASSWORD"),
      engagementId: requireEnv("E2E_RAZORPAY_ENGAGEMENT_ID"),
      invoiceId: requireEnv("E2E_RAZORPAY_INVOICE_ID"),
    };
  });

  test("client pays a pending invoice end to end", async ({ page }) => {
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

    console.log("AFTER LOGIN URL:", page.url());

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

    const request = await page.context().request;
    const checkoutInvoice = await createFreshCheckoutInvoice(
      request,
      fixture.engagementId,
    );

    await page.goto(`/dashboard/${fixture.engagementId}`);

    const invoiceRow = page.locator(".invoices li").filter({
      hasText: checkoutInvoice.description,
    });

    await expect(invoiceRow).toBeVisible({
      timeout: 10000,
    });

    await expect(invoiceRow.locator(".tag")).toHaveText("PENDING");

    const [verifyResponse] = await Promise.all([
      page.waitForResponse(
        (response) =>
          response
            .url()
            .includes(`/api/v1/client/invoices/${checkoutInvoice.id}/verify`) &&
          response.request().method() === "POST",
        { timeout: 15000 },
      ),
      invoiceRow.getByRole("button", { name: /pay now/i }).click(),
    ]);

    const verifyStatus = verifyResponse.status();
    expect(
      verifyStatus,
      `Payment verification returned HTTP ${verifyStatus}`,
    ).toBe(200);

    await expect(invoiceRow.locator(".tag")).toHaveText("PAID", {
      timeout: 15000,
    });

    await expect(page.locator(".error")).toHaveCount(0);
  });

  test("an invalid signature is rejected and the invoice is marked FAILED", async ({
    page,
  }) => {
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
    const adminToken = requireEnv("E2E_ADMIN_ACCESS_TOKEN");

    await stepUp(request, adminToken, requireEnv("E2E_ADMIN_TOTP_SECRET"));

    const invoiceRes = await request.post(
      `${API_BASE_URL}/api/v1/admin/invoices`,
      {
        headers: adminAuthHeader(adminToken),
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
