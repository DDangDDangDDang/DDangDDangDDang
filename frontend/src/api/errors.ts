import { ERROR_CODES, type ErrorCode } from './generated/error-codes';

/** 서버가 실패로 응답했을 때 던지는 오류. code는 docs/api/openapi.json의 x-error-codes 중 하나 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

/**
 * 화면에 보여줄 문구를 서버 기본 메시지 대신 바꾸고 싶을 때 여기에 적는다.
 * 적지 않은 코드는 서버 메시지를 그대로 쓴다.
 */
const USER_MESSAGES: Partial<Record<ErrorCode, string>> = {};

const NETWORK_ERROR = 'NETWORK_ERROR';

/** 오류를 사용자에게 보여줄 한 문장으로 바꾼다. 화면에서는 이 함수만 쓴다. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === NETWORK_ERROR) return '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.';
    const custom = USER_MESSAGES[error.code as ErrorCode];
    if (custom) return custom;
    if (error.message) return error.message;
    const known = ERROR_CODES[error.code as ErrorCode];
    if (known) return known.message;
  }
  return '알 수 없는 오류가 발생했어요. 잠시 후 다시 시도해 주세요.';
}

/** 특정 에러 코드인지 확인한다. 코드 오타는 타입 오류로 잡힌다. */
export function isErrorCode(error: unknown, code: ErrorCode): boolean {
  return error instanceof ApiError && error.code === code;
}

export function networkError(cause: unknown): ApiError {
  const error = new ApiError(NETWORK_ERROR, '', 0);
  error.cause = cause;
  return error;
}
