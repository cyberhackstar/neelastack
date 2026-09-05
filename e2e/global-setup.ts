import { request as playwrightRequest } from "@playwright/test";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(process.cwd(), "../.env") });

const API_BASE_URL = process.env["API_BASE_URL"] ?? "http://localhost:8080";

const BOOTSTRAP_EMAIL =
  process.env["E2E_ADMIN_EMAIL"] ??
  process.env["ADMIN_BOOTSTRAP_EMAIL"] ??
  "admin@neelastack.com";

const BOOTSTRAP_PASSWORD =
  process.env["E2E_ADMIN_PASSWORD"] ??
  process.env["ADMIN_BOOTSTRAP_PASSWORD"];

const FINAL_ADMIN_PASSWORD =
  process.env["E2E_FINAL_ADMIN_PASSWORD"] ?? "E2ePostBootstrap!Passw0rd23";

export default async function globalSetup(): Promise<void> {
  if (!BOOTSTRAP_PASSWORD) {
    throw new Error(
      "global-setup: no E2E admin password configured. " +
        "Set E2E_ADMIN_PASSWORD or ADMIN_BOOTSTRAP_PASSWORD.",
    );
  }

  const context = await playwrightRequest.newContext();

  try {
    let healthy = false;

    for (let i = 0; i < 60; i++) {
      try {
        const r = await context.get(`${API_BASE_URL}/actuator/health`);
        if (r.ok()) {
          healthy = true;
          break;
        }
      } catch {}

      await new Promise((resolve) => setTimeout(resolve, 1000));
    }

    if (!healthy) {
      throw new Error(
        `global-setup: backend did not become healthy at ${API_BASE_URL}`,
      );
    }

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
      const body = await bootstrapLogin.json();

      if (body.mustChangePassword) {
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

        process.env["E2E_ADMIN_EMAIL"] = BOOTSTRAP_EMAIL;
        process.env["E2E_ADMIN_PASSWORD"] = FINAL_ADMIN_PASSWORD;
      } else {
        process.env["E2E_ADMIN_EMAIL"] = BOOTSTRAP_EMAIL;
        process.env["E2E_ADMIN_PASSWORD"] = BOOTSTRAP_PASSWORD;
      }

      return;
    }

    const finalLogin = await context.post(
      `${API_BASE_URL}/api/v1/auth/login`,
      {
        data: {
          email: BOOTSTRAP_EMAIL,
          password: FINAL_ADMIN_PASSWORD,
        },
      },
    );

    if (!finalLogin.ok()) {
      throw new Error(
        `global-setup: admin login failed with both bootstrap and final ` +
          `credentials. Bootstrap returned ${bootstrapLogin.status()}; ` +
          `final returned ${finalLogin.status()}: ${await finalLogin.text()}`,
      );
    }

    process.env["E2E_ADMIN_EMAIL"] = BOOTSTRAP_EMAIL;
    process.env["E2E_ADMIN_PASSWORD"] = FINAL_ADMIN_PASSWORD;
  } finally {
    await context.dispose();
  }
}
