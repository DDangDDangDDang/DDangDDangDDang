import type { BidTier } from './auction-effects';
import { audioContext } from './donation-sound';

/*
 * 입찰·낙찰 효과음. 음원 파일 없이 Web Audio로 만든다. (저작권·파일 받기 없음)
 * - 입찰: 동전이 짤랑거리는 소리. 많이 올려 입찰할수록 동전이 많이 떨어지고, 30% 이상은 "챙", 50% 이상은 팡파르가 붙는다.
 * - 낙찰: 경매 망치가 땅·땅·땅 세 번 내리치고 축하 화음이 울린다.
 * 브라우저가 소리를 막고 있으면(화면을 아직 누르지 않음) 조용히 넘어간다.
 */

/** 동전 하나가 부딪히는 소리: 높은 금속음 두 개(배음이 정수배가 아니라 쇳소리가 난다)가 아주 빨리 사라진다. */
function clink(ctx: AudioContext, at: number, pitch: number, volume = 0.12) {
  for (const [ratio, gainScale, length] of [
    [1, 1, 0.16],
    [2.76, 0.5, 0.1],
    [5.4, 0.25, 0.06],
  ] as const) {
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(pitch * ratio, at);
    gain.gain.setValueAtTime(0, at);
    gain.gain.linearRampToValueAtTime(volume * gainScale, at + 0.003);
    gain.gain.exponentialRampToValueAtTime(0.0001, at + length);
    oscillator.connect(gain).connect(ctx.destination);
    oscillator.start(at);
    oscillator.stop(at + length + 0.02);
  }
}

/** 짧게 울리고 사라지는 음 하나 */
function tone(ctx: AudioContext, at: number, frequency: number, length: number, volume: number, type: OscillatorType = 'triangle') {
  const oscillator = ctx.createOscillator();
  const gain = ctx.createGain();
  oscillator.type = type;
  oscillator.frequency.setValueAtTime(frequency, at);
  gain.gain.setValueAtTime(0, at);
  gain.gain.linearRampToValueAtTime(volume, at + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, at + length);
  oscillator.connect(gain).connect(ctx.destination);
  oscillator.start(at);
  oscillator.stop(at + length + 0.05);
}

// 단계별 동전 수와 떨어지는 시간(초). 동전마다 음높이를 조금씩 달리해 여러 개가 부딪히는 소리를 낸다.
const COINS: Record<BidTier, { count: number; spread: number }> = {
  normal: { count: 2, spread: 0.1 },
  up10: { count: 4, spread: 0.3 },
  up30: { count: 7, spread: 0.45 },
  up50: { count: 12, spread: 0.7 },
};
const COIN_PITCHES = [2350, 2900, 2600, 3200, 2450, 3050, 2750];

const C6 = 1046.5;
const E6 = 1318.5;
const G6 = 1568;
const C7 = 2093;

/** 입찰 단계에 맞는 동전 소리를 한 번 낸다. */
export function playBidCoins(tier: BidTier) {
  try {
    const ctx = audioContext();
    if (!ctx) return;
    if (ctx.state === 'suspended') void ctx.resume().catch(() => undefined);
    const start = ctx.currentTime + 0.02;
    const { count, spread } = COINS[tier];
    for (let i = 0; i < count; i += 1) {
      // 처음에는 촘촘히, 뒤로 갈수록 드문드문 떨어진다.
      const at = start + spread * Math.sqrt(i / Math.max(1, count - 1));
      clink(ctx, at, COIN_PITCHES[i % COIN_PITCHES.length] ?? 2600);
    }
    const end = start + spread;
    if (tier === 'up30' || tier === 'up50') {
      // 동전이 다 떨어진 뒤 "챙"
      clink(ctx, end + 0.08, 4200, 0.14);
      tone(ctx, end + 0.08, C7, 0.5, 0.06, 'sine');
    }
    if (tier === 'up50') {
      // 빠르게 오르는 팡파르
      [C6, E6, G6, C7].forEach((frequency, i) => tone(ctx, end + 0.2 + i * 0.08, frequency, i === 3 ? 0.8 : 0.3, 0.1));
    }
  } catch {
    // 소리는 덤이라 실패해도 이펙트는 그대로 보여 준다.
  }
}

/** 망치가 나무 받침을 내리치는 시각(초). 낙찰 애니메이션(gavel-strike, 380ms × 3, 60% 지점에서 닿음)과 맞춘다. */
export const GAVEL_HITS = [0.23, 0.61, 0.99];

/** 망치 한 번: 낮게 "쿵" 떨어지는 음 + 나무가 "딱" 부딪히는 짧은 잡음 */
function gavelHit(ctx: AudioContext, at: number, volume: number) {
  const thud = ctx.createOscillator();
  const thudGain = ctx.createGain();
  thud.type = 'sine';
  thud.frequency.setValueAtTime(190, at);
  thud.frequency.exponentialRampToValueAtTime(55, at + 0.14);
  thudGain.gain.setValueAtTime(0, at);
  thudGain.gain.linearRampToValueAtTime(volume, at + 0.004);
  thudGain.gain.exponentialRampToValueAtTime(0.0001, at + 0.3);
  thud.connect(thudGain).connect(ctx.destination);
  thud.start(at);
  thud.stop(at + 0.35);

  const length = Math.floor(ctx.sampleRate * 0.04);
  const buffer = ctx.createBuffer(1, length, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < length; i += 1) data[i] = (Math.random() * 2 - 1) * (1 - i / length);
  const noise = ctx.createBufferSource();
  noise.buffer = buffer;
  const band = ctx.createBiquadFilter();
  band.type = 'bandpass';
  band.frequency.setValueAtTime(1600, at);
  band.Q.setValueAtTime(1.2, at);
  const noiseGain = ctx.createGain();
  noiseGain.gain.setValueAtTime(volume * 0.9, at);
  noise.connect(band).connect(noiseGain).connect(ctx.destination);
  noise.start(at);
}

/** 낙찰: 땅·땅·땅 (마지막이 가장 세다) 뒤에 축하 화음 */
export function playGavel() {
  try {
    const ctx = audioContext();
    if (!ctx) return;
    if (ctx.state === 'suspended') void ctx.resume().catch(() => undefined);
    const start = ctx.currentTime + 0.02;
    GAVEL_HITS.forEach((hit, i) => gavelHit(ctx, start + hit, i === GAVEL_HITS.length - 1 ? 0.55 : 0.4));
    const last = start + (GAVEL_HITS[GAVEL_HITS.length - 1] ?? 1);
    [C6, E6, G6].forEach((frequency) => tone(ctx, last + 0.18, frequency, 1.1, 0.07));
    tone(ctx, last + 0.3, C7, 1.2, 0.06);
  } catch {
    // 소리는 덤이라 실패해도 이펙트는 그대로 보여 준다.
  }
}
