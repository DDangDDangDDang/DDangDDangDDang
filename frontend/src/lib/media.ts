/*
 * 카메라·마이크 관련 계산 로직. (판매자 송출 화면과 작은 방송 창이 같이 쓴다)
 */

/** 서버가 방송 중 송출이 없으면 자동으로 끝내는 시간 (백엔드 LiveStreamIdleProperties 기본값 30분). 화면 안내 문구에 쓴다. */
export const IDLE_AUTO_END_MINUTES = 30;

export type MediaKind = "camera" | "microphone";

/** 카메라·마이크를 켜지 못했을 때 보여줄 문구 (getUserMedia 오류 이름 기준) */
export function mediaErrorMessage(kind: MediaKind, error: unknown): string {
  const device = kind === "camera" ? "카메라" : "마이크";
  const name = error instanceof Error ? error.name : "";
  switch (name) {
    case "NotAllowedError":
    case "SecurityError":
      return `${device} 사용이 막혀 있어요. 주소창 왼쪽의 사이트 설정에서 ${device}를 허용한 뒤 다시 시도해 주세요.`;
    case "NotFoundError":
    case "OverconstrainedError":
      return `연결된 ${device}를 찾지 못했어요. ${device}를 연결한 뒤 다시 시도해 주세요.`;
    case "NotReadableError":
    case "AbortError":
      return `다른 프로그램이 ${device}를 쓰고 있어요. 그 프로그램을 닫고 다시 시도해 주세요.`;
    default:
      return `${device}를 켜지 못했어요. 다시 시도해 주세요.`;
  }
}
