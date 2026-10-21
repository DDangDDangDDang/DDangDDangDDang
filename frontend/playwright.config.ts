import { defineConfig, devices } from '@playwright/test';

/*
 * E2E 테스트 (로컬 백엔드 대상). 실행 방법은 frontend/README.md "E2E 테스트" 참고.
 *
 * - E2E_BASE_URL: 프론트 주소 (기본 http://localhost:5173). 이미 떠 있으면 그대로 쓰고, 없으면 npm run dev로 띄운다.
 * - E2E_BROWSER_CHANNEL: PC에 설치된 브라우저로 실행 (chrome, msedge). 없으면 Playwright 브라우저(npx playwright install chromium)
 */
// 계정 등 로컬 설정 (frontend/.env.e2e.local, 커밋하지 않음)
import { existsSync } from 'node:fs';
if (existsSync('.env.e2e.local')) process.loadEnvFile('.env.e2e.local');

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173';
const channel = process.env.E2E_BROWSER_CHANNEL || undefined;

export default defineConfig({
  testDir: './e2e',
  // 같은 계정으로 방송을 만들고 보므로 한 번에 하나씩 실행한다.
  workers: 1,
  fullyParallel: false,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    locale: 'ko-KR',
    // PC 웹 전용 (최소 폭 1280px)
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, channel } }],
  webServer: {
    command: 'npm run dev',
    url: baseURL,
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
