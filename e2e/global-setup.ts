// e2e/global-setup.ts
import { request as playwrightRequest } from "@playwright/test";
import * as dotenv from "dotenv";
import * as fs from "fs";
import * as path from "path";
import * as OTPAuth from "otpauth";

dotenv.config({ path: path.resolve(process.cwd(), "../.env") });

const API_BASE_URL = process.env["API_BASE_URL"] ?? "http://127.0.0.1:8080";

const BOOTSTRAP_EMAIL =
  process.env["E2E_ADMIN_EMAIL"] ??
  process.env["ADMIN_BOOTSTRAP_EMAIL"] ??
  "admin@neelastack.com";

const BOOTSTRAP_PASSWORD =
  process.env["E2E_ADMIN_PASSWORD"] ?? process.env["ADMIN_BOOTSTRAP_PASSWORD"];

const FINAL_ADMIN_PASSWORD =
  process.env["E2E_FINAL_ADMIN_PASSWORD"] ?? "E2ePostBootstrap!Passw0rd23";

const MFA_SECRET_CACHE_PATH = path.resolve(
  __dirname,
  ".e2e-admin-mfa-secret.json",
);

const RAZORPAY_FIXTURE_CACHE_PATH = path.resolve(
  __dirname,
  ".e2e-razorpay-fixture.json",
);

const ADMIN_STORAGE_STATE_PATH = path.resolve(
  __dirname,
  ".e2e-admin-storage-state.json",
);

interface AuthResponseLike {
  accessToken?: string;
  mustChangePassword?: boolean;
  mfaRequired?: boolean;
  mfaToken?: string;
}

interface RazorpayFixture {
  clientEmail: string;
  clientPassword: string;
  engagementId: string;
  invoiceId: string;
}

function currentTotpCode(base32Secret: string): string {
  const totp = new OTPAuth.TOTP({
    algorithm: "SHA1",
    digits: 6,
    period: 30,
    secret: OTPAuth.Secret.fromBase32(base32Secret),
  });

  return totp.generate();
}

function loadCachedSecret(email: string): string | undefined {
  try {
    const cache = JSON.parse(
      fs.readFileSync(MFA_SECRET_CACHE_PATH, "utf-8"),
    ) as Record<string, string>;

    return cache[email];
  } catch {
    return undefined;
  }
}

function saveCachedSecret(email: string, secret: string): void {
  let cache: Record<string, string> = {};

  try {
    cache = JSON.parse(
      fs.readFileSync(MFA_SECRET_CACHE_PATH, "utf-8"),
    ) as Record<string, string>;
  } catch {
    // No existing cache file -- fine, start fresh.
  }

  cache[email] = secret;

  fs.writeFileSync(MFA_SECRET_CACHE_PATH, JSON.stringify(cache, null, 2));
}

function loadCachedRazorpayFixture(): RazorpayFixture | undefined {
  try {
    return JSON.parse(
      fs.readFileSync(RAZORPAY_FIXTURE_CACHE_PATH, "utf-8"),
    ) as RazorpayFixture;
  } catch {
    return undefined;
  }
}

function saveCachedRazorpayFixture(fixture: RazorpayFixture): void {
  fs.writeFileSync(
    RAZORPAY_FIXTURE_CACHE_PATH,
    JSON.stringify(fixture, null, 2),
  );
}

async function buildRazorpayFixture(
  context: any,
  adminToken: string,
): Promise<RazorpayFixture> {
  const clientEmail = `e2e-payer-${Date.now()}@example.com`;
  const clientPassword = "E2eTestPassword!23";

  // Register client
  const registerRes = await context.post(
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

  if (!registerRes.ok()) {
    throw new Error(
      `global-setup: register failed: ${registerRes.status()} ${await registerRes.text()}`,
    );
  }

  // Create engagement
  const engagementRes = await context.post(
    `${API_BASE_URL}/api/v1/admin/engagements`,
    {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: {
        clientEmail,
        title: "E2E Checkout Fixture Project",
        description:
          "Created by the Playwright e2e suite (journey 5) for a checkout test.",
      },
    },
  );

  if (!engagementRes.ok()) {
    throw new Error(
      `global-setup: engagement create failed: ${engagementRes.status()} ${await engagementRes.text()}`,
    );
  }

  const engagement = await engagementRes.json();

  // Create invoice
  const invoiceRes = await context.post(
    `${API_BASE_URL}/api/v1/admin/invoices`,
    {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: {
        engagementId: engagement.id,
        description: "E2E test invoice — checkout journey",
        amount: 999,
        currency: "INR",
      },
    },
  );

  if (!invoiceRes.ok()) {
    throw new Error(
      `global-setup: invoice create failed: ${invoiceRes.status()} ${await invoiceRes.text()}`,
    );
  }

  const invoice = await invoiceRes.json();

  return {
    clientEmail,
    clientPassword,
    engagementId: engagement.id,
    invoiceId: invoice.id,
  };
}

/**
 * Global setup for the whole suite.
 *
 * Runs once in the main Playwright process before workers start.
 *
 * Steps:
 *  1. Wait for backend health.
 *  2. Log in with bootstrap credentials.
 *  3. Change the bootstrap password when required.
 *  4. Ensure MFA is enrolled.
 *  5. Persist the authenticated browser session and build the Razorpay fixture.
 *
 * Authenticates the admin once for browser storage state, then performs explicit
 * step-up only immediately before high-risk fixture creation.
 */
export default async function globalSetup(): Promise<void> {
  if (!BOOTSTRAP_PASSWORD) {
    throw new Error(
      "global-setup: no E2E admin password configured. " +
        "Set E2E_ADMIN_PASSWORD or ADMIN_BOOTSTRAP_PASSWORD.",
    );
  }

  const context = await playwrightRequest.newContext();

  try {
    // -----------------------------------------------------------------------
    // 1. Wait for backend health
    // -----------------------------------------------------------------------
    let healthy = false;

    for (let i = 0; i < 60; i++) {
      try {
        const response = await context.get(`${API_BASE_URL}/actuator/health`);

        if (response.ok()) {
          healthy = true;
          break;
        }
      } catch {
        // Backend may still be starting.
      }

      await new Promise((resolve) => setTimeout(resolve, 1000));
    }

    if (!healthy) {
      throw new Error(
        `global-setup: backend did not become healthy at ${API_BASE_URL}`,
      );
    }

    // -----------------------------------------------------------------------
    // 2. Bootstrap/final admin login
    // -----------------------------------------------------------------------
    let workingPassword: string;

    const bootstrapLogin = await context.post(
      `${API_BASE_URL}/api/v1/auth/login`,
      {
        data: {
          email: BOOTSTRAP_EMAIL,
          password: BOOTSTRAP_PASSWORD,
        },
      },
    );

    if (bootstrapLogin.ok()) {
      const body: AuthResponseLike = await bootstrapLogin.json();

      if (body.mustChangePassword) {
        if (!body.accessToken) {
          throw new Error(
            "global-setup: bootstrap login requires password change but returned no accessToken.",
          );
        }

        const changeRes = await context.post(
          `${API_BASE_URL}/api/v1/auth/change-password`,
          {
            headers: {
              Authorization: `Bearer ${body.accessToken}`,
            },
            data: {
              currentPassword: BOOTSTRAP_PASSWORD,
              newPassword: FINAL_ADMIN_PASSWORD,
            },
          },
        );

        if (!changeRes.ok()) {
          throw new Error(
            `global-setup: mandatory password change failed ` +
              `(${changeRes.status()}): ${await changeRes.text()}`,
          );
        }

        workingPassword = FINAL_ADMIN_PASSWORD;
      } else {
        // Bootstrap password is already the working password.
        workingPassword = BOOTSTRAP_PASSWORD;
      }
    } else {
      // Most likely an existing local Postgres volume whose bootstrap password
      // was already changed during a previous run.
      const finalLogin = await context.post(
        `${API_BASE_URL}/api/v1/auth/login`,
        {
          data: {
            email: BOOTSTRAP_EMAIL,
            password: FINAL_ADMIN_PASSWORD,
          },
        },
      );

      const finalLoginBody: AuthResponseLike = await finalLogin
        .json()
        .catch(() => ({}));

      if (!finalLogin.ok() && finalLoginBody.mfaRequired !== true) {
        throw new Error(
          `global-setup: admin login failed with both bootstrap and final ` +
            `credentials. Bootstrap returned ${bootstrapLogin.status()}; ` +
            `final returned ${finalLogin.status()}: ` +
            `${JSON.stringify(finalLoginBody)}`,
        );
      }

      workingPassword = FINAL_ADMIN_PASSWORD;
    }

    process.env["E2E_ADMIN_EMAIL"] = BOOTSTRAP_EMAIL;
    process.env["E2E_ADMIN_PASSWORD"] = workingPassword;

    // -----------------------------------------------------------------------
    // 3. MFA enrollment
    // -----------------------------------------------------------------------
    let totpSecret = loadCachedSecret(BOOTSTRAP_EMAIL);

    const probeLogin = await context.post(`${API_BASE_URL}/api/v1/auth/login`, {
      data: {
        email: BOOTSTRAP_EMAIL,
        password: workingPassword,
      },
    });

    if (!probeLogin.ok()) {
      throw new Error(
        `global-setup: post-password-change login failed unexpectedly ` +
          `(${probeLogin.status()}): ${await probeLogin.text()}`,
      );
    }

    const probeBody: AuthResponseLike = await probeLogin.json();

    if (probeBody.mfaRequired) {
      if (!totpSecret) {
        throw new Error(
          `global-setup: admin ${BOOTSTRAP_EMAIL} already has MFA enabled ` +
            `but no cached TOTP secret was found at ${MFA_SECRET_CACHE_PATH}. ` +
            `Restore the cache file or reset the local database with ` +
            `docker compose down -v and re-run.`,
        );
      }

      // Already enrolled; cached secret is sufficient.
    } else {
      // Not enrolled yet -- enroll exactly once.
      const accessToken = probeBody.accessToken;

      if (!accessToken) {
        throw new Error(
          "global-setup: login succeeded but no accessToken was returned before MFA enrollment.",
        );
      }

      const setupRes = await context.post(
        `${API_BASE_URL}/api/v1/admin/mfa/setup`,
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        },
      );

      if (!setupRes.ok()) {
        throw new Error(
          `global-setup: MFA setup failed (${setupRes.status()}): ` +
            `${await setupRes.text()}`,
        );
      }

      const setupBody = await setupRes.json();
      const setupSecret = setupBody.manualEntrySecret as string | undefined;

      if (!setupSecret) {
        throw new Error(
          "global-setup: MFA setup succeeded but manualEntrySecret was missing.",
        );
      }

      totpSecret = setupSecret;

      const verifyRes = await context.post(
        `${API_BASE_URL}/api/v1/admin/mfa/verify`,
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          data: {
            code: currentTotpCode(totpSecret),
          },
        },
      );

      if (!verifyRes.ok()) {
        throw new Error(
          `global-setup: MFA verify (enrollment) failed ` +
            `(${verifyRes.status()}): ${await verifyRes.text()}`,
        );
      }

      saveCachedSecret(BOOTSTRAP_EMAIL, totpSecret);
    }

    if (!totpSecret) {
      throw new Error(
        "global-setup: MFA setup completed but no TOTP secret is available.",
      );
    }

    process.env["E2E_ADMIN_TOTP_SECRET"] = totpSecret;

    // -----------------------------------------------------------------------
    // 4. Authenticate the admin once, persist browser state, then perform one
    // explicit step-up for the fixture creation. Test workers reuse this browser
    // session instead of racing the account-level MFA limiter.
    // -----------------------------------------------------------------------
    const adminLoginRes = await context.post(
      `${API_BASE_URL}/api/v1/auth/login`,
      { data: { email: BOOTSTRAP_EMAIL, password: workingPassword } },
    );

    if (!adminLoginRes.ok()) {
      throw new Error(
        `global-setup: final admin login failed: ${adminLoginRes.status()} ${await adminLoginRes.text()}`,
      );
    }

    const adminLoginBody: AuthResponseLike = await adminLoginRes.json();
    if (!adminLoginBody.mfaRequired || !adminLoginBody.mfaToken) {
      throw new Error(
        "global-setup: final admin login did not trigger MFA challenge",
      );
    }

    const mfaRes = await context.post(`${API_BASE_URL}/api/v1/auth/login/mfa`, {
      data: {
        mfaToken: adminLoginBody.mfaToken,
        code: currentTotpCode(totpSecret),
        useRecoveryCode: false,
      },
    });

    if (!mfaRes.ok()) {
      throw new Error(
        `global-setup: final MFA login failed: ${mfaRes.status()} ${await mfaRes.text()}`,
      );
    }

    const adminAuthBody = await mfaRes.json();
    const adminToken = adminAuthBody.accessToken as string | undefined;
    const adminRefreshToken = adminAuthBody.refreshToken as string | undefined;
    if (!adminToken || !adminRefreshToken) {
      throw new Error("global-setup: final MFA login returned no session tokens");
    }

    process.env["E2E_ADMIN_ACCESS_TOKEN"] = adminToken;
    process.env["E2E_ADMIN_REFRESH_TOKEN"] = adminRefreshToken;

    fs.writeFileSync(
      ADMIN_STORAGE_STATE_PATH,
      JSON.stringify({
        cookies: [],
        origins: [
          {
            origin: new URL(
              process.env["BASE_URL"] ?? "http://localhost:4000",
            ).origin,
            localStorage: [
              { name: "neelastack_access_token", value: adminToken },
              { name: "neelastack_refresh_token", value: adminRefreshToken },
              { name: "neelastack_user", value: JSON.stringify(adminAuthBody) },
            ],
          },
        ],
      }, null, 2),
    );

    const stepUpRes = await context.post(
      `${API_BASE_URL}/api/v1/admin/mfa/step-up`,
      {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: { code: currentTotpCode(totpSecret) },
      },
    );
    if (!stepUpRes.ok()) {
      throw new Error(
        `global-setup: MFA step-up failed: ${stepUpRes.status()} ${await stepUpRes.text()}`,
      );
    }

    console.log("global-setup: Building Razorpay e2e fixture...");
    const razorpayFixture = await buildRazorpayFixture(context, adminToken);

    saveCachedRazorpayFixture(razorpayFixture);

    process.env["E2E_RAZORPAY_CLIENT_EMAIL"] = razorpayFixture.clientEmail;
    process.env["E2E_RAZORPAY_CLIENT_PASSWORD"] =
      razorpayFixture.clientPassword;
    process.env["E2E_RAZORPAY_ENGAGEMENT_ID"] = razorpayFixture.engagementId;
    process.env["E2E_RAZORPAY_INVOICE_ID"] = razorpayFixture.invoiceId;

    console.log("global-setup: Razorpay fixture created successfully.");
  } finally {
    await context.dispose();
  }
}
