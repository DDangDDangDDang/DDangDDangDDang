/**
 * 판매자 신청 전 휴대폰 본인인증 (포트원 본인인증 창).
 * 화면은 본인인증 ID만 서버로 보내고, 이름·생년월일은 서버가 포트원에서 직접 조회한다.
 */

export type IdentityConfig = { storeId: string; channelKey: string };

/**
 * 포트원 본인인증 창 설정 (frontend/.env.local의 VITE_PORTONE_STORE_ID, VITE_PORTONE_IDENTITY_CHANNEL_KEY).
 * 본인인증 채널(다날 등)은 결제 채널과 따로 만든다. 없으면 undefined
 */
export function identityConfig(env: Record<string, string | undefined> = import.meta.env): IdentityConfig | undefined {
  const storeId = env.VITE_PORTONE_STORE_ID?.trim();
  const channelKey = env.VITE_PORTONE_IDENTITY_CHANNEL_KEY?.trim();
  return storeId && channelKey ? { storeId, channelKey } : undefined;
}

/** 본인인증 건마다 새 ID. 포트원은 이미 인증을 마친 ID로 다시 인증할 수 없다. */
export function newIdentityVerificationId(): string {
  return `identity-${crypto.randomUUID()}`;
}

/** 생년월일(yyyy-MM-dd) → 1990.01.31 */
export function formatBirthDate(value: string | undefined): string {
  if (!value) return '-';
  const [year, month, day] = value.split('-');
  return year && month && day ? `${year}.${month}.${day}` : value;
}

/** 내 본인인증 결과 쿼리 키 (GET /seller-applications/identity) */
export const MY_IDENTITY_KEY = ['seller-identity', 'mine'] as const;
