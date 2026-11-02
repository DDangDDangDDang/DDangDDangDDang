import { describe, expect, it } from 'vitest';
import { donationTier } from './donation-sound';

describe('donationTier', () => {
  it('1,000M 미만은 small, 1,000M 이상은 medium, 3,000M 이상은 large', () => {
    expect(donationTier(100)).toBe('small');
    expect(donationTier(999)).toBe('small');
    expect(donationTier(1_000)).toBe('medium');
    expect(donationTier(2_900)).toBe('medium');
    expect(donationTier(3_000)).toBe('large');
    expect(donationTier(5_000)).toBe('large');
  });
});
