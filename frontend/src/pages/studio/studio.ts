import type { Schema } from '@/api';
import { fromDateTimeInput, toDateTimeInput } from '@/lib/datetime-input';

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

