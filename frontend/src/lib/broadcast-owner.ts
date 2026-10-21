/*
 * 송출을 맡은 탭 표시. 판매자가 스튜디오를 연 탭만 송출한다.
 * 스튜디오에서 새 탭으로 연 화면(시청 화면 열기, 경매 상세)까지 송출을 시작하면 같은 방송을 서로 빼앗으므로 막는다.
 * sessionStorage는 탭마다 따로라서 새로고침해도 남고, 새 탭에는 없다. 저장이 막힌 환경에서는 이 탭의 메모리에만 둔다.
 */
const KEY = 'ddang-broadcast-owner';
let memory: number | undefined;

export function readBroadcastOwner(): number | undefined {
  try {
    const value = Number(sessionStorage.getItem(KEY));
    return Number.isInteger(value) && value > 0 ? value : memory;
  } catch {
    return memory;
  }
}

export function writeBroadcastOwner(liveStreamId: number | undefined) {
  memory = liveStreamId;
  try {
    if (liveStreamId === undefined) sessionStorage.removeItem(KEY);
    else sessionStorage.setItem(KEY, String(liveStreamId));
  } catch {
    // 저장하지 못해도 이 탭의 메모리에는 남는다.
  }
}
