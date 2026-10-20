import type { ModerationAlert, Schema } from '@/api';

/*
 * 판매자 스튜디오 화면의 계산 로직. (화면과 분리해 단위 테스트한다)
 */

type LiveStream = Schema<'LiveStreamResponse'>;
type UpdateRequest = Schema<'LiveStreamUpdateRequest'>;

/** 서버 검증과 같은 한도 (LiveStreamCreateRequest·LiveStreamUpdateRequest) */
export const TITLE_MAX_LENGTH = 100;
export const DESCRIPTION_MAX_LENGTH = 1000;
/** 시작 예정 시간은 지금부터 30일 이내 (백엔드 LiveStreamService.SCHEDULE_LIMIT) */
export const SCHEDULE_LIMIT_DAYS = 30;

/** 방송 만들기·수정 입력값. scheduledStartAt은 <input type="datetime-local">의 값("2026-10-20T20:00") 또는 빈 문자열 */
export type StreamFormValues = { title: string; description: string; scheduledStartAt: string };
export type StreamFormErrors = Partial<Record<keyof StreamFormValues, string>>;

/** 서버 시각("2026-10-20T20:00:00")을 datetime-local 입력값("2026-10-20T20:00")으로 */
export function toDateTimeInput(serverTime?: string): string {
  return serverTime ? serverTime.slice(0, 16) : '';
}

/** datetime-local 입력값을 서버 시각 문자열로. 서버는 시간대 없는 서울 시각을 받는다. (docs/api/README.md) */
export function fromDateTimeInput(value: string): string | undefined {
  return value ? `${value}:00` : undefined;
}

/** 지금 시각을 datetime-local 입력값 형식으로 (입력의 min·max에 쓴다) */
export function dateTimeInputOf(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function formValuesOf(liveStream?: LiveStream): StreamFormValues {
  return {
    title: liveStream?.title ?? '',
    description: liveStream?.description ?? '',
    scheduledStartAt: toDateTimeInput(liveStream?.scheduledStartAt),
  };
}

/** 입력을 검사한다. 오류가 없으면 빈 객체. (서버도 같은 규칙으로 다시 검사한다) */
export function validateStreamForm(values: StreamFormValues, now: Date): StreamFormErrors {
  const errors: StreamFormErrors = {};
  const title = values.title.trim();
  if (!title) errors.title = '방송 제목을 입력해 주세요.';
  else if (title.length > TITLE_MAX_LENGTH) errors.title = `방송 제목은 ${TITLE_MAX_LENGTH}자 이하로 입력해 주세요.`;

  if (values.description.trim().length > DESCRIPTION_MAX_LENGTH) {
    errors.description = `방송 설명은 ${DESCRIPTION_MAX_LENGTH}자 이하로 입력해 주세요.`;
  }

  if (values.scheduledStartAt) {
    const at = new Date(values.scheduledStartAt);
    const limit = new Date(now.getTime() + SCHEDULE_LIMIT_DAYS * 24 * 60 * 60 * 1000);
    if (Number.isNaN(at.getTime())) errors.scheduledStartAt = '시작 예정 시간을 다시 선택해 주세요.';
    else if (at <= now) errors.scheduledStartAt = '시작 예정 시간은 지금 이후로 정해 주세요.';
    else if (at > limit) errors.scheduledStartAt = `시작 예정 시간은 지금부터 ${SCHEDULE_LIMIT_DAYS}일 이내로 정해 주세요.`;
  }
  return errors;
}

/** 방송 만들기 요청 본문. 비운 설명·예정 시간은 보내지 않는다. */
export function toCreateRequest(values: StreamFormValues): Schema<'LiveStreamCreateRequest'> {
  return {
    title: values.title.trim(),
    description: values.description.trim() || undefined,
    scheduledStartAt: fromDateTimeInput(values.scheduledStartAt),
  };
}

/**
 * 방송 수정 요청 본문: 바뀐 값만 담는다. (서버는 null인 필드를 그대로 둔다)
 * - 설명을 모두 지우면 빈 문자열을 보내 설명을 지운다.
 * - 시작 예정 시간은 방송 시작 전에만 바꿀 수 있고, 지울 수는 없다. (비우면 그대로 둔다)
 * 바뀐 것이 없으면 null
 */
export function toUpdateRequest(original: LiveStream, values: StreamFormValues): UpdateRequest | null {
  const request: UpdateRequest = {};
  const title = values.title.trim();
  if (title !== (original.title ?? '')) request.title = title;

  const description = values.description.trim();
  if (description !== (original.description ?? '').trim()) request.description = description;

  if (original.status === 'SCHEDULED' && values.scheduledStartAt && values.scheduledStartAt !== toDateTimeInput(original.scheduledStartAt)) {
    request.scheduledStartAt = fromDateTimeInput(values.scheduledStartAt);
  }
  return Object.keys(request).length > 0 ? request : null;
}

/** 화면에 남겨 둘 검토 알림 수 */
export const MAX_MODERATION_ALERTS = 50;

/** 검토 알림을 맨 앞(최신)에 붙인다. 같은 채팅의 알림은 한 번만 남긴다. */
export function prependModerationAlert(list: ModerationAlert[], alert: ModerationAlert, max = MAX_MODERATION_ALERTS): ModerationAlert[] {
  if (list.some((a) => a.messageId === alert.messageId)) return list;
  return [alert, ...list].slice(0, max);
}

export type MediaKind = 'camera' | 'microphone';

/** 카메라·마이크를 켜지 못했을 때 보여줄 문구 (getUserMedia 오류 이름 기준) */
export function mediaErrorMessage(kind: MediaKind, error: unknown): string {
  const device = kind === 'camera' ? '카메라' : '마이크';
  const name = error instanceof Error ? error.name : '';
  switch (name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return `${device} 사용이 막혀 있어요. 주소창 왼쪽의 사이트 설정에서 ${device}를 허용한 뒤 다시 시도해 주세요.`;
    case 'NotFoundError':
    case 'OverconstrainedError':
      return `연결된 ${device}를 찾지 못했어요. ${device}를 연결한 뒤 다시 시도해 주세요.`;
    case 'NotReadableError':
    case 'AbortError':
      return `다른 프로그램이 ${device}를 쓰고 있어요. 그 프로그램을 닫고 다시 시도해 주세요.`;
    default:
      return `${device}를 켜지 못했어요. 다시 시도해 주세요.`;
  }
}
