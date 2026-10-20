// 백엔드 API 명세(docs/api/openapi.json)에서 프론트엔드 코드를 만든다.
// npm run dev·build·typecheck·test 전에 자동으로 실행된다. (package.json의 pre 스크립트)
// - src/api/generated/schema.d.ts : 모든 API의 경로·파라미터·응답 타입 (openapi-typescript)
// - src/api/generated/error-codes.ts : 에러 코드 목록 (명세의 x-error-codes)
// 생성 파일은 커밋하지 않는다. (.gitignore) 백엔드가 명세만 커밋하면 프론트는 항상 최신 명세로 빌드된다.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const specPath = path.resolve(root, '../docs/api/openapi.json');
const outDir = path.resolve(root, 'src/api/generated');

const spec = JSON.parse(fs.readFileSync(specPath, 'utf8'));
fs.mkdirSync(outDir, { recursive: true });

const header = '// 자동 생성 파일입니다. 직접 고치지 마세요. (npm run gen:api, 원본: docs/api/openapi.json)\n';

const ast = await openapiTS(spec);
fs.writeFileSync(path.join(outDir, 'schema.d.ts'), header + astToString(ast));

const codes = spec['x-error-codes'] ?? [];
const entries = codes
  .map((c) => `  ${JSON.stringify(c.code)}: { status: ${c.status}, message: ${JSON.stringify(c.message)} },`)
  .join('\n');
fs.writeFileSync(
  path.join(outDir, 'error-codes.ts'),
  `${header}
/** 서버 에러 코드 → HTTP 상태, 서버 기본 메시지 */
export const ERROR_CODES = {
${entries}
} as const;

export type ErrorCode = keyof typeof ERROR_CODES;
`,
);

console.log(`생성 완료: API ${Object.keys(spec.paths).length}개 경로, 에러 코드 ${codes.length}개`);
