import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// 백엔드에 CORS 설정이 없으므로 개발 서버 프록시로 같은 출처처럼 요청한다.
// Refresh Token 쿠키(SameSite=Strict)도 그대로 오간다.
// 백엔드 주소는 환경변수나 test-client/.env.local 의 BACKEND_URL로 바꿀 수 있다.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, '');
  const backend = env.BACKEND_URL || 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: backend },
        '/ws': { target: backend, ws: true },
      },
    },
  };
});
