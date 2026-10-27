import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'src/api/generated'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // API는 생성된 클라이언트(src/api)로만 호출한다. 직접 fetch하면 명세와 어긋나도 컴파일러가 잡지 못한다.
      // checkGlobalObject: window.fetch, globalThis.fetch 같은 우회 호출도 막는다.
      'no-restricted-globals': [
        'error',
        {
          globals: [{ name: 'fetch', message: 'API는 @/api의 api 클라이언트로 호출하세요. (frontend/CLAUDE.md)' }],
          checkGlobalObject: true,
        },
      ],
    },
  },
  {
    // API 클라이언트 구현과 테스트만 fetch를 직접 쓴다.
    files: ['src/api/**', 'src/test/**', '**/*.test.{ts,tsx}'],
    rules: { 'no-restricted-globals': 'off' },
  },
);
