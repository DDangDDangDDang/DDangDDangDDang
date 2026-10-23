import { useCallback, useSyncExternalStore } from 'react';

/*
 * 도네이션 선물 효과음. 음원 파일 없이 Web Audio로 짧은 "띵동" 소리를 만든다. (저작권·파일 받기 없음)
 * 브라우저는 사용자가 화면을 누르기 전에는 소리를 막으므로, 막혀 있으면 조용히 넘어간다.
 */

/**
 * 선물 크기 단계. 효과음과 별 이펙트가 단계마다 다르다.
 * - small: 1,000P 미만 (최소 선물이 100P라 100P 미만은 없다)
 * - medium: 1,000P 이상
 * - large: 3,000P 이상
 */
export type DonationTier = 'small' | 'medium' | 'large';
export const MEDIUM_DONATION = 1_000;
export const LARGE_DONATION = 3_000;

export function donationTier(amount: number): DonationTier {
  if (amount >= LARGE_DONATION) return 'large';
  if (amount >= MEDIUM_DONATION) return 'medium';
  return 'small';
}

type Note = { frequency: number; at: number; length: number; type?: OscillatorType; volume?: number };

// 단계별 소리 (초 단위 시작 시각). 음이 높고 많을수록 화려하다.
const C6 = 1046.5;
const E6 = 1318.5;
const G6 = 1568;
const C7 = 2093;
const E7 = 2637;
const G7 = 3136;
const TIER_NOTES: Record<DonationTier, Note[]> = {
  // 띵동: 두 음
  small: [
    { frequency: E6, at: 0, length: 0.35 },
    { frequency: C6, at: 0.12, length: 0.45 },
  ],
  // 도미솔도 오르는 소리
  medium: [
    { frequency: C6, at: 0, length: 0.4 },
    { frequency: E6, at: 0.1, length: 0.4 },
    { frequency: G6, at: 0.2, length: 0.4 },
    { frequency: C7, at: 0.3, length: 0.6 },
  ],
  // 빠르게 오른 뒤 화음과 반짝이는 높은 음
  large: [
    { frequency: C6, at: 0, length: 0.3, type: 'triangle' },
    { frequency: E6, at: 0.07, length: 0.3, type: 'triangle' },
    { frequency: G6, at: 0.14, length: 0.3, type: 'triangle' },
    { frequency: C7, at: 0.21, length: 0.9, type: 'triangle' },
    { frequency: E7, at: 0.21, length: 0.9, volume: 0.1 },
    { frequency: G7, at: 0.21, length: 0.9, volume: 0.08 },
    { frequency: C7 * 2, at: 0.45, length: 0.25, volume: 0.06 },
    { frequency: G7, at: 0.55, length: 0.25, volume: 0.06 },
    { frequency: C7 * 2, at: 0.65, length: 0.4, volume: 0.06 },
  ],
};

let context: AudioContext | undefined;

function audioContext(): AudioContext | undefined {
  if (typeof window === 'undefined' || typeof window.AudioContext === 'undefined') return undefined;
  context ??= new window.AudioContext();
  return context;
}

/** 선물 크기에 맞는 효과음을 한 번 낸다. 소리를 낼 수 없으면(브라우저가 막음, 지원 안 함) 아무것도 하지 않는다. */
export function playDonationChime(amount: number) {
  try {
    const ctx = audioContext();
    if (!ctx) return;
    if (ctx.state === 'suspended') void ctx.resume().catch(() => undefined);
    const start = ctx.currentTime + 0.02;
    for (const note of TIER_NOTES[donationTier(amount)]) {
      const at = start + note.at;
      const oscillator = ctx.createOscillator();
      const gain = ctx.createGain();
      oscillator.type = note.type ?? 'sine';
      oscillator.frequency.setValueAtTime(note.frequency, at);
      // 짧게 울리고 부드럽게 사라진다.
      gain.gain.setValueAtTime(0, at);
      gain.gain.linearRampToValueAtTime(note.volume ?? 0.16, at + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.0001, at + note.length);
      oscillator.connect(gain).connect(ctx.destination);
      oscillator.start(at);
      oscillator.stop(at + note.length + 0.05);
    }
  } catch {
    // 소리는 덤이라 실패해도 이펙트는 그대로 보여 준다.
  }
}

/*
 * 판매자 스튜디오의 "선물 효과음" 켜기/끄기. 이 브라우저에 기억한다. (기본 켜짐)
 * 같은 탭의 스튜디오와 작은 방송 창이 같은 값을 쓰도록 바뀌면 알린다.
 */
const SOUND_KEY = 'ddang.donation-sound';
const listeners = new Set<() => void>();

function readSoundOn(): boolean {
  try {
    return localStorage.getItem(SOUND_KEY) !== 'off';
  } catch {
    return true;
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useStudioDonationSound(): [boolean, (on: boolean) => void] {
  const on = useSyncExternalStore(subscribe, readSoundOn, () => true);
  const setOn = useCallback((next: boolean) => {
    try {
      if (next) localStorage.removeItem(SOUND_KEY);
      else localStorage.setItem(SOUND_KEY, 'off');
    } catch {
      // 저장할 수 없는 브라우저(사생활 보호 모드 등)면 이번 화면에서만 바뀌지 않는다.
    }
    listeners.forEach((listener) => listener());
  }, []);
  return [on, setOn];
}
