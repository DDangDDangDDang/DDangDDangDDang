import type { RemoteTrack } from "livekit-client";
import {
  CalendarClock,
  Eye,
  Loader2,
  Maximize,
  RefreshCw,
  Video,
  VideoOff,
  Volume2,
  VolumeX,
} from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { errorMessage, type Schema } from "@/api";
import { Badge, Button } from "@/components/ui";
import { formatDateTime, formatNumber } from "@/lib/format";
import { IDLE_AUTO_END_MINUTES } from "@/lib/media";
import type { LiveVideoState } from "./use-live-video";

type Props = {
  liveStream: Schema<"LiveStreamResponse">;
  video: LiveVideoState & {
    retry: () => void;
    startAudio: () => Promise<void> | undefined;
  };
  /** 판매자가 영상 위에 띄운 경매. 영상 안에 그려 전체 화면에서도 보인다. */
  overlay?: ReactNode;
};

/** 방송 영상 영역: 영상 + LIVE·시청자 수 배지 + 소리·전체 화면 버튼. 방송 상태별 안내도 이 자리에 보여준다. */
export function LiveVideo({ liveStream, video, overlay }: Props) {
  const stageRef = useRef<HTMLDivElement>(null);
  // 브라우저는 사용자가 누르기 전에는 소리를 재생하지 못하게 하므로 음소거로 시작한다.
  const [muted, setMuted] = useState(true);
  const isLive = liveStream.status === "LIVE";
  const playing = isLive && video.phase === "connected" && video.videoTrack;

  const toggleSound = () => {
    if (muted) void video.startAudio();
    setMuted(!muted);
  };

  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void stageRef.current?.requestFullscreen();
  };

  return (
    <div
      ref={stageRef}
      className="relative flex aspect-video items-center justify-center overflow-hidden rounded-lg bg-stage text-neutral-400"
    >
      {video.videoTrack && <TrackVideo track={video.videoTrack} />}
      {video.audioTracks.map((track) => (
        <TrackAudio key={track.sid} track={track} muted={muted} />
      ))}

      {!playing && <StageMessage liveStream={liveStream} video={video} />}

      {isLive && (
        <div className="absolute top-4 left-4 flex gap-2">
          <Badge variant="live" className="h-7 px-2.5 tracking-wide">
            <span
              aria-hidden="true"
              className="size-1.5 rounded-full bg-neutral-0"
            />
            LIVE
          </Badge>
          {video.viewerCount !== undefined && (
            <Badge className="h-7 bg-stage/70 px-2.5 font-normal text-neutral-0">
              <Eye className="size-3.5" aria-hidden="true" />
              <span className="num">{formatNumber(video.viewerCount)}</span>명
              시청
            </Badge>
          )}
        </div>
      )}

      {isLive && overlay}

      {playing && (
        <div className="absolute right-4 bottom-4 flex gap-2">
          <StageButton
            label={muted ? "소리 켜기" : "소리 끄기"}
            onClick={toggleSound}
          >
            {muted ? (
              <VolumeX className="size-5" aria-hidden="true" />
            ) : (
              <Volume2 className="size-5" aria-hidden="true" />
            )}
          </StageButton>
          <StageButton label="전체 화면" onClick={toggleFullscreen}>
            <Maximize className="size-5" aria-hidden="true" />
          </StageButton>
        </div>
      )}
    </div>
  );
}

function StageButton({
  label,
  onClick,
  children,
}: {
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className="flex size-11 items-center justify-center rounded-md bg-stage/70 text-neutral-0 transition-colors hover:bg-stage"
    >
      {children}
    </button>
  );
}

/** 영상이 나오지 않을 때 가운데에 보여주는 안내 */
function StageMessage({ liveStream, video }: Props) {
  if (liveStream.status === "SCHEDULED") {
    return (
      <Centered
        icon={<CalendarClock className="size-10" aria-hidden="true" />}
        title="아직 방송 전이에요"
      >
        {liveStream.scheduledStartAt
          ? `${formatDateTime(liveStream.scheduledStartAt)}에 시작할 예정이에요.`
          : "판매자가 방송을 시작하면 바로 볼 수 있어요."}
      </Centered>
    );
  }
  if (liveStream.status === "ENDED") {
    return (
      <Centered
        icon={<VideoOff className="size-10" aria-hidden="true" />}
        title="방송이 끝났어요"
      />
    );
  }

  switch (video.phase) {
    case "error":
      return (
        <Centered
          icon={<VideoOff className="size-10" aria-hidden="true" />}
          title="방송에 연결하지 못했어요"
        >
          <span className="block">{errorMessage(video.error)}</span>
          <Button
            variant="outline"
            size="sm"
            className="mt-3"
            onClick={video.retry}
          >
            다시 연결
          </Button>
        </Centered>
      );
    case "disconnected":
      return (
        <Centered
          icon={<VideoOff className="size-10" aria-hidden="true" />}
          title="방송 연결이 끊겼어요"
        >
          <Button
            variant="outline"
            size="sm"
            className="mt-3"
            onClick={video.retry}
          >
            다시 연결
          </Button>
        </Centered>
      );
    case "reconnecting":
      return (
        <Centered
          icon={
            <RefreshCw className="size-10 animate-spin" aria-hidden="true" />
          }
          title="연결이 끊겨 다시 연결하는 중이에요"
        />
      );
    case "connected":
      // 판매자가 송출을 멈춘 상태(카메라·마이크를 끄거나 연결이 끊김). 계속 없으면 서버가 방송을 끝낸다.
      return (
        <Centered
          icon={<Video className="size-10" aria-hidden="true" />}
          title="판매자 화면을 기다리고 있어요"
        >
          판매자 송출이 {IDLE_AUTO_END_MINUTES}분 동안 없으면 방송이 자동으로
          종료돼요.
        </Centered>
      );
    default:
      return (
        <Centered
          icon={<Loader2 className="size-10 animate-spin" aria-hidden="true" />}
          title="방송에 연결하는 중이에요"
        />
      );
  }
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

function TrackVideo({ track }: { track: RemoteTrack }) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    track.attach(element);
    return () => {
      track.detach(element);
    };
  }, [track]);
  // 소리는 별도 오디오 트랙으로 재생하므로 영상은 항상 음소거한다. (자동 재생 허용)
  return (
    <video
      ref={ref}
      className="absolute inset-0 size-full object-contain"
      autoPlay
      playsInline
      muted
    />
  );
}

function TrackAudio({ track, muted }: { track: RemoteTrack; muted: boolean }) {
  const ref = useRef<HTMLAudioElement>(null);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    track.attach(element);
    return () => {
      track.detach(element);
    };
  }, [track]);
  // attach()가 요소의 muted 값을 바꿀 수 있으므로, 연결한 뒤와 음소거를 바꿀 때마다 화면의 값을 다시 적용한다.
  useEffect(() => {
    if (ref.current) ref.current.muted = muted;
  }, [track, muted]);
  // 처음 그릴 때부터 음소거 상태로 둔다. (attach()가 연결하면서 바로 재생을 시작하므로)
  return <audio ref={ref} autoPlay muted={muted} />;
}
