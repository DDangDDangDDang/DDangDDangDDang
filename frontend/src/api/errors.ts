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
const USER_MESSAGES: Partial<Record<ErrorCode, string>> = {
  AUCTION_002: '판매 중인 상품만 경매에 올릴 수 있어요.',
  AUCTION_003: '이 상품은 이미 시작 전이거나 진행 중인 경매가 있어요.',
  AUCTION_004: '마감 시각은 시작 시각보다 뒤여야 해요.',
  AUCTION_005: '즉시구매가는 시작가보다 커야 해요.',
  AUCTION_006: '내 경매만 취소하거나 방송에 올릴 수 있어요.',
  AUCTION_007: '시작 전이거나 진행 중인 경매만 취소할 수 있어요.',
  AUCTION_008: '입찰이 있는 경매는 취소할 수 없어요.',
  AUCTION_009: '경매 정보가 바뀌었어요. 새로 불러온 뒤 다시 시도해 주세요.',
  AUCTION_010: '시작 전이거나 진행 중인 경매만 방송에 올릴 수 있어요.',
  AUCTION_011: '다른 방송에 올라가 있는 경매예요. 그 방송이 끝나면 올릴 수 있어요.',
  AUCTION_012: '지금은 입찰할 수 없는 경매예요. (시작 전이거나 이미 끝났어요)',
  AUCTION_013: '마감 시각이 지나 입찰할 수 없어요.',
  AUCTION_014: '내 경매에는 입찰하거나 구매할 수 없어요.',
  AUCTION_015: '이미 최고 입찰자예요. 다른 사람이 더 높게 입찰하면 다시 입찰할 수 있어요.',
  AUCTION_016: '그 사이 현재가가 올랐어요. 금액을 다시 정해 주세요.',
  AUCTION_017: '즉시구매가 이상으로는 입찰할 수 없어요. [즉시 구매]를 이용해 주세요.',
  AUCTION_018: '입찰이 몰리고 있어요. 잠시 후 다시 시도해 주세요.',
  AUCTION_019: '즉시 구매가 없는 경매예요.',
  PRODUCT_002: '내 상품만 수정하거나 삭제할 수 있어요.',
  PRODUCT_003: '소분류 카테고리를 골라 주세요.',
  PRODUCT_004: '시작 전이거나 진행 중인 경매가 있는 상품은 수정하거나 삭제할 수 없어요.',
  CATEGORY_004: '하위 카테고리가 있어 삭제할 수 없어요.',
  CATEGORY_005: '이 카테고리에 등록된 상품이 있어 삭제할 수 없어요.',
  CATEGORY_006: '상품이 등록된 카테고리에는 하위 카테고리를 만들 수 없어요.',
};

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
