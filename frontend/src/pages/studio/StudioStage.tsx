import type { LocalVideoTrack } from "livekit-client";
import { Loader2, Mic, MicOff, RefreshCw, Video, VideoOff } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";
import { errorMessage } from "@/api";
import { Badge, Button, Notice, Select } from "@/components/ui";
import { cn } from "@/lib/cn";
import { IDLE_AUTO_END_MINUTES, type MediaKind } from "@/lib/media";
import type { LocalDevice } from "@/components/broadcast/use-local-media";
import type { PublisherState } from "@/components/broadcast/use-publisher";

type Props = {
  live: boolean;
  camera: LocalDevice<"camera">;
  microphone: LocalDevice<"microphone">;
  publisher: PublisherState & { retry: () => void };
  onToggle: (kind: MediaKind) => void;
  onSelectDevice: (kind: MediaKind, deviceId: string) => void;
};

/** 내 카메라 미리보기 + 송출 상태 + 마이크·카메라 켜기/끄기 + 장치 선택 */
export function StudioStage({
  live,
  camera,
  microphone,
  publisher,
  onToggle,
  onSelectDevice,
}: Props) {
  const showVideo = camera.track && camera.enabled;

  return (
    <div className="flex flex-col gap-3">
      <div className="relative flex aspect-video items-center justify-center overflow-hidden rounded-lg bg-stage text-neutral-400">
        {camera.track && (
          <PreviewVideo track={camera.track} hidden={!camera.enabled} />
        )}
        {!showVideo && (
          <CameraMessage camera={camera} onRetry={() => onToggle("camera")} />
        )}

        <div className="absolute top-4 left-4 flex gap-2">
          <PublishBadge live={live} phase={publisher.phase} />
        </div>
        {live && <PublishOverlay publisher={publisher} />}
        {/* 연결은 됐지만 카메라·마이크를 모두 꺼서 아무것도 송출하지 않는 경우 */}
        {live &&
          publisher.phase === "connected" &&
          !(camera.enabled && camera.track) &&
          !(microphone.enabled && microphone.track) && (
            <div className="absolute inset-x-4 top-14 flex justify-center">
              <OverlayLine
                icon={<VideoOff className="size-4" aria-hidden="true" />}
              >
                카메라와 마이크가 모두 꺼져 송출이 멈췄어요. {IDLE_NOTICE}
              </OverlayLine>
            </div>
          )}

        <div className="absolute bottom-4 left-4 flex gap-2">
          <StageToggle
            on={microphone.enabled && !!microphone.track}
            label={
              microphone.enabled && microphone.track
                ? "마이크 끄기"
                : "마이크 켜기"
            }
            disabled={microphone.starting}
            onClick={() => onToggle("microphone")}
          >
            {microphone.enabled && microphone.track ? (
              <Mic className="size-5" aria-hidden="true" />
            ) : (
              <MicOff className="size-5" aria-hidden="true" />
            )}
          </StageToggle>
          <StageToggle
            on={camera.enabled && !!camera.track}
            label={
              camera.enabled && camera.track ? "카메라 끄기" : "카메라 켜기"
            }
            disabled={camera.starting}
            onClick={() => onToggle("camera")}
          >
            {camera.enabled && camera.track ? (
              <Video className="size-5" aria-hidden="true" />
            ) : (
              <VideoOff className="size-5" aria-hidden="true" />
            )}
          </StageToggle>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <DeviceSelect
          kind="camera"
          label="카메라"
          device={camera}
          onSelect={onSelectDevice}
        />
        <DeviceSelect
          kind="microphone"
          label="마이크"
          device={microphone}
          onSelect={onSelectDevice}
        />
      </div>
      {microphone.error && <Notice tone="error">{microphone.error}</Notice>}
      {camera.error && camera.track && (
        <Notice tone="error">{camera.error}</Notice>
      )}
      {publisher.publishError !== undefined && (
        <Notice tone="error">
          카메라·마이크를 방송에 올리지 못했어요.{" "}
          {errorMessage(publisher.publishError)}
        </Notice>
      )}
    </div>
  );
}

function PreviewVideo({
  track,
  hidden,
}: {
  track: LocalVideoTrack;
  hidden: boolean;
}) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    track.attach(element);
    return () => {
      track.detach(element);
    };
  }, [track]);
  return (
    <video
      ref={ref}
      aria-label="내 카메라 미리보기"
      className={cn(
        "absolute inset-0 size-full object-contain",
        hidden && "invisible",
      )}
      autoPlay
      playsInline
      muted
    />
  );
}

function CameraMessage({
  camera,
  onRetry,
}: {
  camera: LocalDevice<"camera">;
  onRetry: () => void;
}) {
  if (camera.starting) {
    return (
      <Centered
        icon={<Loader2 className="size-10 animate-spin" aria-hidden="true" />}
        title="카메라를 켜는 중이에요"
      />
    );
  }
  if (camera.error) {
    return (
      <Centered
        icon={<VideoOff className="size-10" aria-hidden="true" />}
        title="카메라를 켜지 못했어요"
      >
        <span className="block">{camera.error}</span>
        <Button variant="outline" size="sm" className="mt-3" onClick={onRetry}>
          다시 시도
        </Button>
      </Centered>
    );
  }
  return (
    <Centered
      icon={<VideoOff className="size-10" aria-hidden="true" />}
      title="카메라가 꺼져 있어요"
    />
  );
}

/** 송출이 멈췄을 때 함께 보여 주는 자동 종료 안내 (25분부터는 IdleWarning이 남은 시간을 따로 알린다) */
const IDLE_NOTICE = `송출이 ${IDLE_AUTO_END_MINUTES}분 동안 없으면 방송이 자동으로 종료돼요.`;

function PublishBadge({
  live,
  phase,
}: {
  live: boolean;
  phase: PublisherState["phase"];
}) {
  if (!live) {
    return (
      <Badge className="h-7 bg-stage/70 px-2.5 font-semibold text-neutral-0">
        방송 전 미리보기
      </Badge>
    );
  }
  if (phase === "connected") {
    return (
      <Badge variant="live" className="h-7 px-2.5 tracking-wide">
        <span
          aria-hidden="true"
          className="size-1.5 rounded-full bg-neutral-0"
        />
        LIVE 송출 중
      </Badge>
    );
  }
  return (
    <Badge className="h-7 bg-stage/70 px-2.5 font-semibold text-neutral-0">
      송출 준비 중
    </Badge>
  );
}

/** 방송 중인데 송출이 되지 않을 때 영상 위에 띄우는 안내 */
function PublishOverlay({
  publisher,
}: {
  publisher: PublisherState & { retry: () => void };
}) {
  let content: ReactNode = null;
  switch (publisher.phase) {
    case "connecting":
      content = (
        <OverlayLine
          icon={<Loader2 className="size-4 animate-spin" aria-hidden="true" />}
        >
          방송 서버에 연결하는 중이에요
        </OverlayLine>
      );
      break;
    case "reconnecting":
      content = (
        <OverlayLine
          icon={
            <RefreshCw className="size-4 animate-spin" aria-hidden="true" />
          }
        >
          연결이 끊겨 다시 연결하는 중이에요
        </OverlayLine>
      );
      break;
    case "disconnected":
    case "error":
    case "replaced":
      content = (
        <OverlayLine
          icon={<VideoOff className="size-4" aria-hidden="true" />}
          action={
            <Button variant="outline" size="sm" onClick={publisher.retry}>
              {publisher.phase === "replaced" ? "이 창에서 송출" : "다시 연결"}
            </Button>
          }
        >
          {publisher.phase === "replaced"
            ? "다른 창에서 송출을 시작해 이 창의 송출이 멈췄어요."
            : publisher.phase === "error"
              ? `송출하지 못했어요. ${errorMessage(publisher.error)}`
              : "송출이 끊겼어요. 시청자에게 화면이 보이지 않아요."}{" "}
          {IDLE_NOTICE}
        </OverlayLine>
      );
      break;
  }
  if (!content) return null;
  return (
    <div className="absolute inset-x-4 top-14 flex justify-center">
      {content}
    </div>
  );
}

function OverlayLine({
  icon,
  action,
  children,
}: {
  icon: ReactNode;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div
      role="status"
      className="flex items-center gap-3 rounded-md bg-stage/80 px-4 py-2.5 text-label text-neutral-0"
    >
      {icon}
      <span>{children}</span>
      {action}
    </div>
  );
}

function StageToggle({
  on,
  label,
  disabled,
  onClick,
  children,
}: {
  on: boolean;
  label: string;
  disabled: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "flex size-11 items-center justify-center rounded-md text-neutral-0 transition-colors disabled:cursor-not-allowed disabled:opacity-60",
        on ? "bg-stage/70 hover:bg-stage" : "bg-brand-500 hover:bg-brand-600",
      )}
    >
      {children}
    </button>
  );
}

function DeviceSelect<K extends MediaKind>({
  kind,
  label,
  device,
  onSelect,
}: {
  kind: K;
  label: string;
  device: LocalDevice<K>;
  onSelect: (kind: MediaKind, deviceId: string) => void;
}) {
  const options = device.devices
    .filter((d) => d.deviceId)
    .map((d, i) => ({
      value: d.deviceId,
      label: d.label || `${label} ${i + 1}`,
    }));
  if (options.length === 0)
    options.push({
      value: "",
      label: device.starting ? "불러오는 중" : `사용할 수 있는 ${label} 없음`,
    });

  return (
    <Select
      label={label}
      options={options}
      value={device.deviceId ?? options[0]?.value ?? ""}
      disabled={device.devices.length === 0}
      onChange={(e) => onSelect(kind, e.target.value)}
    />
  );
}

function Centered({
  icon,
  title,
  children,
}: {
  icon: ReactNode;
  title: string;
  children?: ReactNode;
}) {
  return (
    <div
      role="status"
      className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-8 text-center"
    >
      {icon}
      <p className="text-body-lg font-semibold text-neutral-0">{title}</p>
      {children && <div className="text-body text-neutral-400">{children}</div>}
    </div>
  );
}
