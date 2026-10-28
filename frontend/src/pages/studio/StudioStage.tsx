import type { LocalVideoTrack } from "livekit-client";
import { Clapperboard, FileVideo, Loader2, Mic, MicOff, RefreshCw, Video, VideoOff } from "lucide-react";
import { useEffect, useId, useRef, type ReactNode } from "react";
import { errorMessage } from "@/api";
import { Badge, Button, Notice, Select } from "@/components/ui";
import { cn } from "@/lib/cn";
import { IDLE_AUTO_END_MINUTES, type MediaKind } from "@/lib/media";
import type { LocalDevice } from "@/components/broadcast/use-local-media";
import type { PublisherState } from "@/components/broadcast/use-publisher";
import { RECORDED_VIDEO_ACCEPT } from "@/components/broadcast/use-recorded-video";
import type { Broadcast } from "@/components/broadcast/broadcast-context";
import { RecordedBadge } from "@/components/live/RecordedBadge";

type Props = {
  live: boolean;
  camera: LocalDevice<"camera">;
  microphone: LocalDevice<"microphone">;
  /** 영상 소스: 카메라·마이크 또는 녹화 영상 파일 */
  source: Broadcast["source"];
  publisher: PublisherState & { retry: () => void };
  onToggle: (kind: MediaKind) => void;
  onSelectDevice: (kind: MediaKind, deviceId: string) => void;
  /** 영상 위 UI(경매 카드·선물·입찰·낙찰 안내). 시청자에게 보이는 모습 그대로 미리보기 위에 그린다. */
  overlay?: ReactNode;
  /** overlay를 그릴지 (기본: 방송 중일 때만. 화면 배치 편집 중이면 방송 전에도 그린다) */
  showOverlay?: boolean;
};

/**
 * 내 카메라(또는 녹화 영상) 미리보기 + 송출 상태 + 마이크·카메라 켜기/끄기 + 장치 선택.
 * 녹화 영상을 고르면 영상 파일을 반복 재생해 카메라 대신 송출하고, 장치 선택 대신 파일 고르기를 보여 준다.
 */
export function StudioStage({
  live,
  camera,
  microphone,
  source,
  publisher,
  onToggle,
  onSelectDevice,
  overlay,
  showOverlay = live,
}: Props) {
  const recordedMode = source.kind === "recorded";
  const showVideo = camera.track && camera.enabled;
  const soundLabel = recordedMode ? "영상 소리" : "마이크";
  const pictureLabel = recordedMode ? "영상 화면" : "카메라";

  return (
    <div className="flex flex-col gap-3">
      <SourcePicker source={source} />
      <div className="relative flex aspect-video items-center justify-center overflow-hidden rounded-lg bg-stage text-neutral-400">
        {camera.track && (
          <PreviewVideo
            track={camera.track}
            hidden={!camera.enabled}
            label={recordedMode ? "녹화 영상 미리보기" : "내 카메라 미리보기"}
          />
        )}
        {!showVideo &&
          (recordedMode ? (
            <RecordedMessage source={source.recorded} hasVideo={!!camera.track} />
          ) : (
            <CameraMessage camera={camera} onRetry={() => onToggle("camera")} />
          ))}

        <div className="absolute top-4 left-4 z-10 flex gap-2">
          <PublishBadge live={live} phase={publisher.phase} />
          {recordedMode && camera.track && <RecordedBadge className="h-7 px-2.5" />}
        </div>
        {live && <PublishOverlay publisher={publisher} />}
        {/* 연결은 됐지만 카메라·마이크를 모두 꺼서 아무것도 송출하지 않는 경우 */}
        {live &&
          publisher.phase === "connected" &&
          !(camera.enabled && camera.track) &&
          !(microphone.enabled && microphone.track) && (
            <div className="pointer-events-none absolute inset-x-4 top-14 z-10 flex justify-center *:pointer-events-auto">
              <OverlayLine
                icon={<VideoOff className="size-4" aria-hidden="true" />}
              >
                {pictureLabel}와 {soundLabel}가 모두 꺼져 송출이 멈췄어요. {IDLE_NOTICE}
              </OverlayLine>
            </div>
          )}
        {showOverlay && overlay}

        <div className="absolute bottom-4 left-4 z-10 flex gap-2">
          <StageToggle
            on={microphone.enabled && !!microphone.track}
            label={
              microphone.enabled && microphone.track
                ? `${soundLabel} 끄기`
                : `${soundLabel} 켜기`
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
              camera.enabled && camera.track ? `${pictureLabel} 끄기` : `${pictureLabel} 켜기`
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

      {recordedMode ? (
        <RecordedFileField source={source.recorded} />
      ) : (
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
      )}
      {microphone.error && <Notice tone="error">{microphone.error}</Notice>}
      {camera.error && camera.track && (
        <Notice tone="error">{camera.error}</Notice>
      )}
      {publisher.publishError !== undefined && (
        <Notice tone="error">
          {recordedMode ? "녹화 영상을" : "카메라·마이크를"} 방송에 올리지 못했어요.{" "}
          {errorMessage(publisher.publishError)}
        </Notice>
      )}
    </div>
  );
}

/** 송출할 영상 고르기: 카메라 또는 녹화 영상. 방송 중에도 바꿀 수 있다. */
function SourcePicker({ source }: { source: Broadcast["source"] }) {
  const options = [
    { kind: "camera", label: "카메라", icon: <Video className="size-4" aria-hidden="true" /> },
    { kind: "recorded", label: "녹화 영상", icon: <Clapperboard className="size-4" aria-hidden="true" /> },
  ] as const;
  return (
    <div role="radiogroup" aria-label="송출할 영상" className="flex gap-2">
      {options.map((option) => {
        const selected = source.kind === option.kind;
        return (
          <button
            key={option.kind}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => source.setKind(option.kind)}
            className={cn(
              "flex h-10 items-center gap-2 rounded-md border px-4 text-label font-semibold transition-colors",
              selected
                ? "border-brand-500 bg-brand-50 text-brand-700"
                : "border-neutral-200 text-neutral-700 hover:bg-neutral-25",
            )}
          >
            {option.icon}
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

/** 녹화 영상 파일 고르기. 고른 파일은 서버에 올리지 않고 이 브라우저에서 반복 재생해 송출한다. */
function RecordedFileField({ source }: { source: Broadcast["source"]["recorded"] }) {
  const inputId = useId();
  const hintId = useId();
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <label
          htmlFor={inputId}
          className="flex h-10 cursor-pointer items-center gap-2 rounded-md border border-neutral-200 px-4 text-label font-semibold text-neutral-700 hover:bg-neutral-25 focus-within:ring-2 focus-within:ring-brand-500"
        >
          <FileVideo className="size-4" aria-hidden="true" />
          {source.fileName ? "다른 영상 고르기" : "영상 파일 고르기"}
          <input
            id={inputId}
            type="file"
            accept={RECORDED_VIDEO_ACCEPT}
            aria-describedby={hintId}
            className="sr-only"
            onChange={(e) => {
              const file = e.target.files?.[0];
              // 같은 파일을 다시 골라도 바뀌도록 비워 둔다.
              e.target.value = "";
              if (file) void source.select(file);
            }}
          />
        </label>
        {source.fileName && (
          <span className="min-w-0 truncate text-body text-neutral-700" title={source.fileName}>
            {source.fileName}
          </span>
        )}
      </div>
      <p id={hintId} className="text-caption text-neutral-500">
        영상이 끝나면 처음부터 다시 재생돼요. 파일은 서버에 올리지 않고 이 창에서 재생해 송출하므로, 방송 중에는 이 창을 닫지 마세요.
        시청자와 구독자에게는 &lsquo;녹화 영상&rsquo;으로 표시돼요.
      </p>
      {source.error && <Notice tone="error">{source.error}</Notice>}
    </div>
  );
}

function RecordedMessage({
  source,
  hasVideo,
}: {
  source: Broadcast["source"]["recorded"];
  hasVideo: boolean;
}) {
  if (source.loading) {
    return (
      <Centered
        icon={<Loader2 className="size-10 animate-spin" aria-hidden="true" />}
        title="영상을 불러오는 중이에요"
      />
    );
  }
  if (hasVideo) {
    return (
      <Centered
        icon={<VideoOff className="size-10" aria-hidden="true" />}
        title="영상 화면이 꺼져 있어요"
      />
    );
  }
  return (
    <Centered
      icon={<FileVideo className="size-10" aria-hidden="true" />}
      title="송출할 영상 파일을 골라 주세요"
    >
      아래 [영상 파일 고르기]로 mp4·webm 파일을 고르면 반복 재생해 송출해요.
    </Centered>
  );
}

function PreviewVideo({
  track,
  hidden,
  label,
}: {
  track: LocalVideoTrack;
  hidden: boolean;
  label: string;
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
      aria-label={label}
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
      <Badge className="h-7 bg-stage/70 px-2.5 font-semibold text-white">
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
    <Badge className="h-7 bg-stage/70 px-2.5 font-semibold text-white">
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
    <div className="pointer-events-none absolute inset-x-4 top-14 z-10 flex justify-center *:pointer-events-auto">
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
      className="flex items-center gap-3 rounded-md bg-stage/80 px-4 py-2.5 text-label text-white"
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
        "flex size-11 items-center justify-center rounded-md text-white transition-colors disabled:cursor-not-allowed disabled:opacity-60",
        on ? "bg-stage/70 hover:bg-stage" : "bg-brand-800 hover:bg-brand-900",
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
      <p className="text-body-lg font-semibold text-white">{title}</p>
      {children && <div className="text-body text-neutral-400">{children}</div>}
    </div>
  );
}
