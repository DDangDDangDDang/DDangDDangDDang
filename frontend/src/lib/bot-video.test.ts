import { describe, expect, it } from 'vitest';
import { botVideoPosition, liveBotVideoProblem } from './bot-video';

describe('botVideoPosition', () => {
  const startedAt = '2026-11-03T10:00:00';
  const started = new Date(startedAt).getTime();

  it('방송 시작부터 흐른 시간을 영상 길이로 나눈 나머지 위치에서 튼다', () => {
    expect(botVideoPosition(startedAt, started + 30_000, 60)).toBe(30);
    expect(botVideoPosition(startedAt, started + 150_000, 60)).toBe(30);
  });

  it('같은 시각이면 언제 들어와도 같은 위치다', () => {
    const now = started + 1_234_567;
    expect(botVideoPosition(startedAt, now, 95)).toBe(botVideoPosition(startedAt, now, 95));
  });

  it('길이를 모르거나 시작 시각이 없거나 미래면 처음부터 튼다', () => {
    expect(botVideoPosition(startedAt, started + 30_000, NaN)).toBe(0);
    expect(botVideoPosition(startedAt, started + 30_000, 0)).toBe(0);
    expect(botVideoPosition(undefined, started, 60)).toBe(0);
    expect(botVideoPosition('잘못된 값', started, 60)).toBe(0);
    expect(botVideoPosition(startedAt, started - 5_000, 60)).toBe(0);
  });
});

describe('liveBotVideoProblem', () => {
  it('MP4·WEBM이고 500MB 이하면 올릴 수 있다', () => {
    expect(liveBotVideoProblem({ type: 'video/mp4', size: 1000 })).toBeUndefined();
    expect(liveBotVideoProblem({ type: 'video/webm', size: 1000 })).toBeUndefined();
  });

  it('다른 형식, 너무 큰 파일, 빈 파일은 막는다', () => {
    expect(liveBotVideoProblem({ type: 'video/quicktime', size: 1000 })).toBeDefined();
    expect(liveBotVideoProblem({ type: 'video/mp4', size: 500 * 1024 * 1024 + 1 })).toBeDefined();
    expect(liveBotVideoProblem({ type: 'video/mp4', size: 0 })).toBeDefined();
  });
});
