import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { BrowserPushSetting } from '@/components/notification/BrowserPushSetting';
import { buttonVariants, Card, CardBody, CardHeader, CardTitle, ErrorState, Notice, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { useWebPush } from '@/lib/web-push';

type Setting = Schema<'NotificationSettingResponse'>;
type Category = NonNullable<Setting['category']>;

const SETTINGS_KEY = ['notifications', 'settings'] as const;
const PREFERENCES_KEY = ['notifications', 'preferences'] as const;

/**
 * 알림 설정: 브라우저 알림 켜기·끄기(사이트를 보고 있을 때도 받을지 포함), 알림 종류별 받기·안 받기, 보관 기간 안내.
 * 끈 종류의 알림은 알림함에도 쌓이지 않고, 실시간 팝업·브라우저 알림도 오지 않는다.
 */
export function NotificationSettingsPage() {
  const session = useSession();
  const push = useWebPush(session?.user.userId);
  const queryClient = useQueryClient();
  const settings = useQuery({
    queryKey: SETTINGS_KEY,
    queryFn: () => call(api.GET('/api/v1/notifications/settings')),
  });
  const update = useMutation({
    mutationFn: ({ category, enabled }: { category: Category; enabled: boolean }) =>
      call(api.PUT('/api/v1/notifications/settings/{category}', { params: { path: { category } }, body: { enabled } })),
    onSuccess: (data) => queryClient.setQueryData(SETTINGS_KEY, data),
  });
  // 사이트를 보고 있을 때도 브라우저 알림 받기 (기본 꺼짐: 보고 있으면 화면 안 팝업만)
  const preferences = useQuery({
    queryKey: PREFERENCES_KEY,
    queryFn: () => call(api.GET('/api/v1/notifications/preferences')),
  });
  const updatePreference = useMutation({
    mutationFn: (pushWhenVisible: boolean) => call(api.PUT('/api/v1/notifications/preferences', { body: { pushWhenVisible } })),
    onSuccess: (data) => queryClient.setQueryData(PREFERENCES_KEY, data),
  });

  return (
    <div className="mx-auto max-w-[720px]">
      <PageHeader
        title="알림 설정"
        description="받을 알림을 고르고, 탭을 닫아도 알림을 받을지 정해요."
        actions={
          <Link to="/me/auction-alerts" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            알림 받는 경매
          </Link>
        }
      />
      <div className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-body-lg">브라우저 알림</CardTitle>
          </CardHeader>
          <CardBody>
            {push.state === 'unsupported' ? (
              <p className="text-label text-neutral-500">이 브라우저는 브라우저 알림을 지원하지 않아요. 사이트를 열어 두면 화면 안 알림으로 알려 드려요.</p>
            ) : (
              <div className="flex flex-col gap-1">
                <BrowserPushSetting push={push} />
                <ul aria-label="브라우저 알림 방식" className="mt-2 border-t border-neutral-100">
                  <SwitchRow
                    id="notification-push-when-visible"
                    label="사이트를 보고 있을 때도 받기"
                    description={
                      push.state === 'on'
                        ? '땅땅땅 탭을 보고 있어도 브라우저 알림을 띄워요. (화면 안 팝업과 함께 떠요)'
                        : '브라우저 알림을 켜면 적용돼요. 땅땅땅 탭을 보고 있어도 브라우저 알림을 띄워요.'
                    }
                    checked={preferences.data?.pushWhenVisible ?? false}
                    pending={preferences.isPending || updatePreference.isPending}
                    onChange={(checked) => updatePreference.mutate(checked)}
                  />
                </ul>
                {(preferences.isError || updatePreference.isError) && (
                  <Notice tone="error">{errorMessage(updatePreference.error ?? preferences.error)}</Notice>
                )}
              </div>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-body-lg">받을 알림</CardTitle>
          </CardHeader>
          <CardBody className="flex flex-col gap-3">
            {settings.isPending ? (
              <div className="flex flex-col gap-2">
                {Array.from({ length: 6 }, (_, i) => (
                  <Skeleton key={i} className="h-12" />
                ))}
              </div>
            ) : settings.isError ? (
              <ErrorState message={errorMessage(settings.error)} onRetry={() => void settings.refetch()} />
            ) : (
              <ul aria-label="알림 종류" className="flex flex-col divide-y divide-neutral-100">
                {(settings.data ?? []).map((setting) => (
                  <SettingRow
                    key={setting.category}
                    setting={setting}
                    // 바꾸는 중에는 모든 스위치를 막는다. (응답 순서가 바뀌어 앞 응답이 뒤 변경을 덮지 않게)
                    pending={update.isPending}
                    onChange={(enabled) => setting.category && update.mutate({ category: setting.category, enabled })}
                  />
                ))}
              </ul>
            )}
            {update.isError && <Notice tone="error">{errorMessage(update.error)}</Notice>}
          </CardBody>
        </Card>

        <p className="text-label text-neutral-500">알림함의 알림은 90일 동안 보관하고, 그 뒤에는 자동으로 지워요.</p>
      </div>
    </div>
  );
}

/** 알림 종류 한 줄 */
function SettingRow({ setting, pending, onChange }: { setting: Setting; pending: boolean; onChange: (enabled: boolean) => void }) {
  return (
    <SwitchRow
      id={`notification-setting-${setting.category}`}
      label={setting.label ?? ''}
      description={setting.description}
      checked={setting.enabled ?? true}
      pending={pending}
      onChange={onChange}
    />
  );
}

/** 이름·설명과 켜기 스위치 한 줄 */
function SwitchRow({
  id,
  label,
  description,
  checked: enabled,
  pending,
  onChange,
}: {
  id: string;
  label: string;
  description?: string;
  checked: boolean;
  pending: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <li className="flex items-center justify-between gap-4 py-3">
      <label htmlFor={id} className="flex min-w-0 flex-col gap-0.5">
        <span className="text-body font-semibold">{label}</span>
        {description && <span className="text-label text-neutral-500">{description}</span>}
      </label>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={enabled}
        disabled={pending}
        onClick={() => onChange(!enabled)}
        className={cn(
          'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors disabled:opacity-60',
          enabled ? 'bg-brand-500' : 'bg-neutral-200',
        )}
      >
        <span className="sr-only">{enabled ? '받는 중' : '받지 않음'}</span>
        <span aria-hidden="true" className={cn('inline-block size-5 rounded-full bg-neutral-0 shadow-sm transition-transform', enabled ? 'translate-x-5' : 'translate-x-0.5')} />
      </button>
    </li>
  );
}
