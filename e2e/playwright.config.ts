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
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
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
        launchOptions: {
          executablePath:
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
        },
      },
    },
  ],
});
