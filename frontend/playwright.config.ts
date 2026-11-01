import { defineConfig, devices } from "@playwright/test";

const PORT = Number(process.env.E2E_PORT ?? 3000);

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`,
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  // Smoke tests only need the Next.js server; flows that call the API also need Spring + ai-service running.
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: `npm run start -- -p ${PORT}`, port: PORT, reuseExistingServer: !process.env.CI, timeout: 120_000 },
});
