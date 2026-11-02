import { useState } from 'react';
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  EmptyState,
  ErrorState,
  Input,
  Skeleton,
  Textarea,
} from '@/components/ui';
import { formatMileage, formatPoint, formatRemaining } from '@/lib/format';
import type { ChatMessage } from '@/api';
import { AuctionEffects } from '@/components/auction/AuctionEffects';
import { DonationEffects } from '@/components/donation/DonationEffects';

/** 공통 컴포넌트와 디자인 토큰을 한눈에 확인하는 개발용 화면 (/dev/components) */
export function DevComponentsPage() {
  const [amount, setAmount] = useState('153000');

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-title-lg font-bold">공통 컴포넌트</h1>

      <DonationEffectPreview />
      <AuctionEffectPreview />

      <Card>
        <CardHeader>
          <CardTitle>버튼</CardTitle>
        </CardHeader>
        <CardBody className="flex flex-wrap items-center gap-3">
          <Button>주요 버튼</Button>
          <Button variant="secondary">보조 버튼</Button>
          <Button variant="outline">테두리 버튼</Button>
          <Button variant="ghost">배경 없는 버튼</Button>
          <Button disabled>비활성</Button>
          <Button loading>요청 중</Button>
          <Button size="lg">
            <span className="num">{formatPoint(153000)}</span> 입찰하기
          </Button>
        </CardBody>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>배지</CardTitle>
        </CardHeader>
        <CardBody className="flex flex-wrap items-center gap-3">
          <Badge variant="live">LIVE</Badge>
          <Badge variant="info">내가 최고</Badge>
          <Badge variant="warning">상위 입찰 있음</Badge>
          <Badge variant="neutral">낙찰</Badge>
          <Badge variant="outline">대기</Badge>
        </CardBody>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>입력</CardTitle>
        </CardHeader>
        <CardBody className="grid max-w-[720px] grid-cols-2 gap-4">
          <Input
            label="입찰 금액"
            inputMode="numeric"
            suffix="P"
            value={amount}
            onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, ''))}
            hint="최소 153,000 P · 입찰 단위 1,000 P"
          />
          <Input label="닉네임" placeholder="2~10자" error="이미 사용 중인 닉네임이에요." />
          <Textarea label="방송 설명" placeholder="시청자에게 보여줄 설명" hint="1000자 이하" />
        </CardBody>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>숫자 글꼴</CardTitle>
        </CardHeader>
        <CardBody className="flex items-baseline gap-6">
          <span className="num text-price-lg font-bold">{formatPoint(152000)}</span>
          <span className="num text-body-lg font-bold text-brand-500">{formatRemaining(48)}</span>
        </CardBody>
      </Card>

      <div className="grid grid-cols-3 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>불러오는 중</CardTitle>
          </CardHeader>
          <CardBody className="flex flex-col gap-2">
            <Skeleton className="h-5 w-2/3" />
            <Skeleton className="h-5 w-1/2" />
            <Skeleton className="h-24" />
          </CardBody>
        </Card>
        <Card>
          <EmptyState title="아직 방송이 없어요" description="방송이 시작되면 여기에 표시됩니다." />
        </Card>
        <Card>
          <ErrorState message="방송 목록을 불러오지 못했어요." onRetry={() => undefined} />
        </Card>
      </div>
    </div>
  );
}

/** 입찰·낙찰 이펙트 미리 보기: 직전 현재가 대비 단계별(10% 미만·10%·30%·50%) 입찰과 낙찰의 애니메이션·효과음 확인 */
function AuctionEffectPreview() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const previous = 10_000;
  const send = (type: 'BID' | 'AUCTION_SOLD', amount: number) =>
    setMessages((list) => [
      ...list,
      {
        messageId: 'auction-preview-' + list.length,
        type,
        liveStreamId: 0,
        senderId: 0,
        nickname: '미리보기',
        content: null,
        sentAt: new Date().toISOString(),
        amount,
        previousAmount: type === 'BID' ? previous : null,
        auctionId: 0,
        productName: '라이카 M6',
      },
    ]);
  return (
    <Card>
      <CardHeader>
        <CardTitle>입찰·낙찰 이펙트</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-3">
        <div className="flex flex-wrap gap-2">
          {[
            { label: '5% 올려 입찰', amount: 10_500 },
            { label: '10% 올려 입찰', amount: 11_000 },
            { label: '30% 올려 입찰', amount: 13_000 },
            { label: '50% 올려 입찰', amount: 15_000 },
          ].map(({ label, amount }) => (
            <Button key={label} variant="outline" onClick={() => send('BID', amount)}>
              {label}
            </Button>
          ))}
          <Button onClick={() => send('AUCTION_SOLD', 15_000)}>낙찰</Button>
        </div>
        <div className="relative aspect-video rounded-lg bg-stage">
          <AuctionEffects messages={messages} sound className="absolute inset-0" />
        </div>
      </CardBody>
    </Card>
  );
}

/** 도네이션 선물 이펙트 미리 보기: 금액 단계별(1,000P 미만·이상·3,000P 이상) 별·효과음 확인 */
function DonationEffectPreview() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const send = (amount: number) =>
    setMessages((list) => [
      ...list,
      {
        messageId: 'preview-' + list.length,
        type: 'DONATION',
        liveStreamId: 0,
        senderId: 0,
        nickname: '미리보기',
        content: amount >= 3_000 ? '오늘 방송 최고예요!' : null,
        sentAt: new Date().toISOString(),
        amount,
      },
    ]);
  return (
    <Card>
      <CardHeader>
        <CardTitle>도네이션 선물 이펙트</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-3">
        <div className="flex gap-2">
          {[300, 1_000, 3_000].map((amount) => (
            <Button key={amount} variant="outline" onClick={() => send(amount)}>
              <span className="num">{formatMileage(amount)}</span> 선물
            </Button>
          ))}
        </div>
        <div className="relative flex aspect-video items-center justify-center rounded-lg bg-stage">
          <DonationEffects messages={messages} sound className="absolute top-4 right-4" />
        </div>
      </CardBody>
    </Card>
  );
}
