import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, isErrorCode, type Schema } from '@/api';
import { Button, Input, Notice, Textarea } from '@/components/ui';
import {
  DESCRIPTION_MAX_LENGTH,
  formValuesOf,
  SCHEDULE_LIMIT_DAYS,
  TITLE_MAX_LENGTH,
  toCreateRequest,
  toUpdateRequest,
  validateStreamForm,
  type StreamFormErrors,
  type StreamFormValues,
} from './studio';
import { dateTimeInputOf } from '@/lib/datetime-input';
import { HashtagField } from './HashtagField';

type LiveStream = Schema<'LiveStreamResponse'>;

type Props =
  | { mode: 'create'; onSaved: (liveStream: LiveStream) => void; onActiveExists: () => void }
  | { mode: 'edit'; liveStream: LiveStream; onSaved: (liveStream: LiveStream) => void; onCancel: () => void };

/**
 * 방송 만들기·수정 입력. 제목(필수)·설명·해시태그·시작 예정 시간.
 * 시작 예정 시간은 방송 시작 전에만 정하거나 바꿀 수 있다.
 */
export function StreamForm(props: Props) {
  const original = props.mode === 'edit' ? props.liveStream : undefined;
  const [values, setValues] = useState<StreamFormValues>(() => formValuesOf(original));
  const [errors, setErrors] = useState<StreamFormErrors>({});
  const [notice, setNotice] = useState<string>();
  // 해시태그 입력칸에 태그로 더하지 못한 글(형식 오류)이 남았는지 (HashtagField)
  const [hashtagInvalid, setHashtagInvalid] = useState(false);
  const canSchedule = props.mode === 'create' || original?.status === 'SCHEDULED';

  const mutation = useMutation({
    mutationFn: async (): Promise<LiveStream | undefined> => {
      if (props.mode === 'create') {
        return call(api.POST('/api/v1/live-streams', { body: toCreateRequest(values) }));
      }
      const body = toUpdateRequest(props.liveStream, values);
      if (!body) return props.liveStream; // 바뀐 것이 없으면 요청하지 않는다.
      const liveStreamId = props.liveStream.id ?? 0;
      return call(api.PATCH('/api/v1/live-streams/{liveStreamId}', { params: { path: { liveStreamId } }, body }));
    },
    onSuccess: (liveStream) => {
      if (liveStream) props.onSaved(liveStream);
    },
    onError: (error) => {
      if (isErrorCode(error, 'LIVESTREAM_012') || isErrorCode(error, 'LIVESTREAM_011')) {
        setErrors({ scheduledStartAt: errorMessage(error) });
        return;
      }
      if (isErrorCode(error, 'LIVESTREAM_015')) {
        setErrors({ hashtags: errorMessage(error) });
        return;
      }
      if (props.mode === 'create' && isErrorCode(error, 'LIVESTREAM_010')) {
        // 다른 창에서 이미 방송을 만들었다. 그 방송을 불러온다.
        props.onActiveExists();
        return;
      }
      setNotice(errorMessage(error));
    },
  });

  const update = (patch: Partial<StreamFormValues>) => {
    setValues((prev) => ({ ...prev, ...patch }));
    const key = Object.keys(patch)[0] as keyof StreamFormValues;
    if (errors[key]) setErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setNotice(undefined);
    // 수정할 때 예정 시간을 그대로 두면 서버에 보내지 않으므로 검사하지 않는다. (예정 시간이 지난 방송도 제목·설명은 고칠 수 있게)
    const scheduleChanged = canSchedule && values.scheduledStartAt !== formValuesOf(original).scheduledStartAt;
    const next = validateStreamForm(scheduleChanged ? values : { ...values, scheduledStartAt: '' }, new Date());
    setErrors(next);
    // 해시태그 입력칸에 형식이 맞지 않는 글이 남아 있으면 저장하지 않는다. (오류는 입력칸 아래에 이미 보임)
    if (Object.keys(next).length === 0 && !hashtagInvalid) mutation.mutate();
  };

  const now = new Date();
  const scheduleHint =
    props.mode === 'edit' && original?.scheduledStartAt
      ? '한 번 정한 시간은 지울 수 없고 바꿀 수만 있어요.'
      : `정하지 않아도 돼요. 정하면 방송 예정 목록에 나와요. (${SCHEDULE_LIMIT_DAYS}일 이내)`;

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <Input
        label={`방송 제목 (${TITLE_MAX_LENGTH}자 이하)`}
        placeholder="예) 빈티지 필름 카메라 경매"
        maxLength={TITLE_MAX_LENGTH}
        value={values.title}
        onChange={(e) => update({ title: e.target.value })}
        error={errors.title}
        required
      />
      <Textarea
        label="방송 설명"
        placeholder="어떤 상품을 소개하는지 시청자에게 알려 주세요."
        maxLength={DESCRIPTION_MAX_LENGTH}
        rows={3}
        value={values.description}
        onChange={(e) => update({ description: e.target.value })}
        error={errors.description}
        hint={
          <span>
            연락처나 외부 링크는 적을 수 없어요. <span className="num">{values.description.length}</span>/{DESCRIPTION_MAX_LENGTH}
          </span>
        }
      />
      <HashtagField tags={values.hashtags} onChange={(hashtags) => update({ hashtags })} error={errors.hashtags} onValidityChange={(valid) => setHashtagInvalid(!valid)} />
      {canSchedule && (
        <div className="w-[320px]">
          <Input
            label="시작 예정 시간 (선택)"
            type="datetime-local"
            min={dateTimeInputOf(now)}
            max={dateTimeInputOf(new Date(now.getTime() + SCHEDULE_LIMIT_DAYS * 24 * 60 * 60 * 1000))}
            value={values.scheduledStartAt}
            onChange={(e) => update({ scheduledStartAt: e.target.value })}
            error={errors.scheduledStartAt}
            hint={scheduleHint}
          />
        </div>
      )}

      {notice && <Notice tone="error">{notice}</Notice>}

      <div className="flex gap-2">
        <Button type="submit" variant={props.mode === 'create' ? 'primary' : 'secondary'} loading={mutation.isPending}>
          {props.mode === 'create' ? '방송 만들기' : '저장'}
        </Button>
        {props.mode === 'edit' && (
          <Button type="button" variant="outline" onClick={props.onCancel} disabled={mutation.isPending}>
            취소
          </Button>
        )}
      </div>
    </form>
  );
}
