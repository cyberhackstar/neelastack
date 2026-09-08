import { defineConfig, devices } from "@playwright/test";
import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, "../.env") });

/**
 * E2E suite (Section 3, master prompt). No Playwright/Cypress files existed anywhere
 * in the repo before this pass -- confirmed via full-repo search, not assumed -- so
 * this is a from-scratch build, not a "restore" of something that existed elsewhere.
 * If a separate e2e suite genuinely exists outside this repo (the prior changes doc
 * implied one was "uploaded earlier"), reconcile the two rather than keeping both.
 *
 * Runs against the CI-local docker-compose stack (see .github/workflows/ci-cd.yml's
 * e2e job) -- frontend on :4000 (SSR), backend on :8080.
 */
export default defineConfig({
  testDir: "./tests",
  globalSetup: require.resolve("./global-setup.ts"),
  // Keep the suite deterministic locally and in CI. Four or more concurrent
  // Chromium sessions can overwhelm the small SSR/test stack and create false
  // navigation/render failures even when the API is healthy.
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: Number.parseInt(process.env.E2E_WORKERS ?? "2", 10),
  reporter: [["html", { open: "never" }], ["list"]],
  use: {
    baseURL: process.env.BASE_URL ?? "http://localhost:4000",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
      },
    },
  ],
});

