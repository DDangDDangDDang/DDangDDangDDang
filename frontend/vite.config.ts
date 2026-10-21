import { defineConfig, loadEnv } from 'vite';
import { configDefaults } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath, URL } from 'node:url';

// 백엔드에 CORS 설정이 없으므로 개발 서버 프록시로 같은 출처처럼 요청한다.
// 리프레시 토큰 쿠키(SameSite=Strict)도 그대로 오간다.
// 백엔드 주소는 환경변수나 frontend/.env.local 의 BACKEND_URL로 바꿀 수 있다.
export default defineConfig(({ mode }) => {
  // 어느 폴더에서 실행하든 frontend/.env.local을 읽는다.
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), '');
  const backend = env.BACKEND_URL || 'http://localhost:8080';

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 5173,
      // E2E 결과 폴더는 감시하지 않는다. (Windows에서 보고서 파일이 잠겨 개발 서버가 죽음)
      watch: { ignored: ['**/playwright-report/**', '**/test-results/**'] },
      proxy: {
        '/api': { target: backend },
        '/ws': { target: backend, ws: true },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      // e2e/는 Playwright가 실행한다. (npm run e2e)
      exclude: [...configDefaults.exclude, 'e2e/**'],
      css: false,
    },
  };
});
