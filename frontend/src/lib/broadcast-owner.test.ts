import { afterEach, describe, expect, it } from 'vitest';
import { readBroadcastOwner, writeBroadcastOwner } from './broadcast-owner';

describe('broadcast-owner', () => {
  afterEach(() => writeBroadcastOwner(undefined));

  it('이 탭이 송출하는 방송 번호를 적고 읽는다', () => {
    expect(readBroadcastOwner()).toBeUndefined();
    writeBroadcastOwner(12);
    expect(readBroadcastOwner()).toBe(12);
  });

  it('방송이 끝나면 지운다', () => {
    writeBroadcastOwner(12);
    writeBroadcastOwner(undefined);
    expect(readBroadcastOwner()).toBeUndefined();
  });
});
