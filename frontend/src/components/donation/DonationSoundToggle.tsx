import { Volume2, VolumeX } from 'lucide-react';
import { Button } from '@/components/ui';
import { playDonationChime, useStudioDonationSound } from '@/lib/donation-sound';

/**
 * 판매자 스튜디오: 선물 효과음 켜기/끄기. 이 브라우저에 기억하고, 작은 방송 창도 같은 설정을 쓴다.
 * 켤 때 한 번 들려줘 소리를 미리 확인할 수 있게 한다. (누른 순간이라 브라우저가 소리를 막지 않는다)
 */
export function DonationSoundToggle() {
  const [on, setOn] = useStudioDonationSound();
  return (
    <Button
      variant="ghost"
      size="sm"
      aria-pressed={on}
      onClick={() => {
        if (!on) playDonationChime(0);
        setOn(!on);
      }}
    >
      {on ? <Volume2 className="size-4" aria-hidden="true" /> : <VolumeX className="size-4" aria-hidden="true" />}
      {on ? '선물 효과음 켜짐' : '선물 효과음 꺼짐'}
    </Button>
  );
}
