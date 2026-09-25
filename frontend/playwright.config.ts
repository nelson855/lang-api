import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4173',
    locale: 'zh-CN',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', testMatch: ['desktop.spec.ts', 'language.spec.ts', 'wallet.spec.ts'], use: { ...devices['Desktop Chrome'] } },
    {
      name: 'mobile-320',
      testMatch: ['mobile.spec.ts'],
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 320, height: 568 },
        hasTouch: true,
        isMobile: true,
      },
    },
  ],
  webServer: {
    command: 'npm run preview -- --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 60000,
  },
});
