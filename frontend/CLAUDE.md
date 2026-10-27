# 땅땅땅 프론트엔드 작업 규칙

이 프론트엔드는 AI가 작성하고, 팀(백엔드 개발자)은 화면과 테스트 결과로 확인합니다.
사람이 코드를 한 줄씩 리뷰하지 않으므로, **아래 규칙과 자동 검사(`npm run check`)가 품질을 지킵니다.** 규칙을 어기는 코드는 만들지 않습니다.

## 제품 기본

- **라이브 커머스 경매 서비스.** 판매자가 방송을 하고, 시청자는 방송을 보며 채팅하고 경매에 입찰합니다.
- **방송과 경매는 별개입니다.** 방송 화면은 경매 정보를 보여주기만 하고, 경매 등록·입찰은 방송과 상관없이 가능합니다.
- **PC 웹 전용**입니다. 최소 폭 1280px 기준으로 만들고 모바일 화면은 만들지 않습니다.
- 화면 문구는 **한국어**입니다. 사용자가 알아듣는 말로 씁니다. (`id`, `role`, 에러 코드를 그대로 보여주지 않음)

## 기술 구성 (임의로 바꾸지 않음)

| 용도 | 사용 |
|---|---|
| 화면 | React 19 + TypeScript (strict) |
| 화면 이동 | React Router 7 (`src/router.tsx`) |
| 서버 데이터 | TanStack Query 5 (`useQuery`, `useMutation`) |
| API 호출 | `@/api`의 `api` + `call()` (명세에서 생성된 타입) |
| 스타일 | Tailwind CSS 4 + 디자인 토큰 (`src/styles/index.css`) |
| 아이콘 | lucide-react |
| 테스트 | Vitest + Testing Library |

새 라이브러리가 꼭 필요하면 이유를 PR 설명에 적습니다. 같은 일을 하는 라이브러리를 두 개 두지 않습니다.

## API 호출

- **API는 `@/api`에서만 가져와 호출합니다.** `fetch`를 직접 쓰면 린트 오류가 납니다.
- 경로·파라미터·응답 타입은 백엔드 명세(`docs/api/openapi.json`)에서 자동 생성됩니다. **타입을 직접 만들지 말고 생성된 타입을 씁니다.**
  ```ts
  import { api, call, type Schema } from '@/api';

  type Auction = Schema<'AuctionResponse'>;

  const auction = await call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId } } }));
  ```
- 화면에서는 TanStack Query로 감쌉니다.
  ```ts
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ['auction', auctionId],
    queryFn: () => call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId } } })),
  });
  ```
- **응답 타입의 필드는 대부분 `?`(선택)입니다.** 백엔드 명세가 필수 여부를 표시하지 않기 때문입니다. 값이 없을 때의 화면(빈 문자열, `-` 등)을 정해서 처리합니다. `!`로 억지로 없애지 않습니다.
- 204 응답(경매 취소, 상품 삭제 등)은 본문이 없어 `call()`이 `undefined`를 돌려줍니다.
- 인증: `call()`이 Access Token을 붙이고, 만료되면 자동으로 재발급해서 다시 보냅니다. 화면에서 토큰을 다루지 않습니다.
- 로그인 상태는 `useSession()`으로 읽습니다. 로그인·로그아웃은 `login()`, `logout()`을 씁니다.

### 백엔드 명세가 바뀌었을 때

`src/api/generated/`는 `npm run dev`·`build`·`typecheck`·`test`를 실행할 때마다 명세에서 자동으로 다시 만들어집니다. 커밋하지 않고(.gitignore), 직접 고치지도 않습니다. 따로 만들고 싶으면 `npm run gen:api`.
명세가 바뀐 뒤 `npm run typecheck`에서 나는 타입 오류가 곧 **명세가 바뀌어 고쳐야 할 화면**입니다.

### 실시간 메시지 (WebSocket/STOMP)

채팅, 방송 정보 변경, 판매자 검토 알림, 1:1 채팅은 STOMP로 받습니다. 경로와 메시지 형식은 `docs/api/README.md`의 "실시간 메시지"를 따릅니다. 이 부분은 자동 생성되지 않으니 문서의 필드 이름을 정확히 씁니다.

- 연결은 `@/api`의 `connectRealtime`, 방송 하나의 채팅·정보 변경·검토 알림은 `@/lib/use-live-realtime`을 씁니다.
- 1:1 채팅(`/user/queue/dm`)은 앱 전체에 연결 하나(`DmRealtime`, AppLayout)만 두고, 화면은 `@/lib/dm-events`의 `useDmEvent`로 받습니다. 화면마다 따로 연결하지 않습니다.
- 판매자 송출(카메라·마이크, LiveKit, 내 방송 채팅·검토 알림)은 앱 전체에 하나(`BroadcastProvider`, AppLayout)만 두어 방송 중 다른 화면으로 가도 이어집니다. 화면은 `@/components/broadcast/broadcast-context`의 `useBroadcast()`로 씁니다. 송출은 스튜디오를 연 탭만 맡습니다. (`@/lib/broadcast-owner`)

## 오류 처리

- 실패는 `ApiError`(`code`, `status`, `message`)로 옵니다.
- **사용자에게 보여줄 문구는 `errorMessage(error)`로 만듭니다.** 서버 메시지 대신 다른 문구를 쓰고 싶으면 `src/api/errors.ts`의 `USER_MESSAGES`에 코드별로 추가합니다.
- 특정 오류에 따라 동작이 달라지면 `isErrorCode(error, 'AUCTION_008')`처럼 코드로 구분합니다. 메시지 문자열로 비교하지 않습니다. 코드 목록: `src/api/generated/error-codes.ts`
- 입력 검증 오류는 해당 입력 아래(`<Input error=...>`)에, 그 밖의 오류는 화면 안의 안내 영역에 보여줍니다. `alert()`은 쓰지 않습니다.

## 화면 구성

- 화면 구성과 배치는 **디자인 캔버스**를 따릅니다.
  - 와이어프레임: https://claude.ai/artifact/6gBg5EYNxsRtYQWFax82Bj
  - 고해상도 시안(방송 시청, 디자인 토큰, 입찰 카드 상태): https://claude.ai/artifact/T53y6GadRqugVpnNRJWmDb
- 새 화면은 `src/pages/<이름>Page.tsx`로 만들고 `src/router.tsx`에 경로를 추가합니다.
- 한 화면에서만 쓰는 부품은 `src/pages/<화면>/` 아래, 여러 화면에서 쓰는 부품은 `src/components/`에 둡니다.
- **모든 목록·상세 화면은 세 가지 상태를 그립니다.**
  1. 불러오는 중: `Skeleton` (스피너만 덩그러니 두지 않음)
  2. 데이터 없음: `EmptyState` (무엇이 여기에 나오는지, 어떻게 채우는지)
  3. 실패: `ErrorState` + 다시 시도 버튼

## 디자인 토큰과 공통 컴포넌트

- **색·글꼴·크기·모서리·그림자는 토큰만 씁니다.** Tailwind 기본 색은 지워 두어 `bg-red-500` 같은 클래스는 만들어지지 않습니다. `#d63f0e` 같은 값을 직접 쓰지 않습니다.
  - 색: `brand-*`(주요 동작, LIVE, 마감 임박), `neutral-*`(배경·선·글자), `info-*`(나의 상태: 최고 입찰 중 등), `stage`(영상 배경)
  - 글자: `text-caption` 12 · `text-label` 13 · `text-body` 14 · `text-body-lg` 16 · `text-title-md` 18 · `text-title-lg` 24 · `text-price-lg` 28 · `text-display` 36
  - 모서리: `rounded-sm` 배지 · `rounded-md` 버튼·입력 · `rounded-lg` 카드·영상
  - **금액·남은 시간·인원 같은 숫자에는 `num` 클래스**를 붙입니다. (숫자 전용 글꼴 + 고정폭 숫자)
- 새 토큰이 필요하면 `src/styles/index.css`의 `@theme`에 추가하고 이유를 주석으로 남깁니다.
- **공통 컴포넌트를 먼저 씁니다.** `@/components/ui`: `Button`, `Input`, `Textarea`, `Select`, `Badge`, `Card`, `Notice`(한 줄 안내·오류), `ConfirmDialog`(되돌릴 수 없는 동작 확인), `Dialog`(화면을 옮기지 않고 입력받는 창), `Skeleton`, `EmptyState`, `ErrorState`, `Pagination`(번호로 넘기는 목록)
  - 인증번호(휴대폰·이메일) 입력은 `@/components/auth/VerificationField` (5분 타이머, 1분 재요청 대기 포함)
  - 로그인해야 하는 화면은 `src/router.tsx`에서 `RequireAuth` 아래에 둡니다.
  - 목록 무한 스크롤은 TanStack `useInfiniteQuery` + `@/lib/use-infinite-scroll`. 서버가 페이지 번호(`page`·`totalPages`)로 주는 목록은 `Pagination`
  - 경매 표시는 `@/components/auction` (`AuctionCard`, `AuctionPhaseBadge`, `AuctionTime`, 조건 입력 `AuctionConditionFields`), 계산은 `@/lib/auction`
  - 서버 시각 ↔ `datetime-local` 입력 변환은 `@/lib/datetime-input`, 매초 바뀌는 표시는 `@/lib/use-now`
  - 서버 enum 값(주문 상태, 포인트 종류, 택배사, 경매·상품 상태)은 `@/lib/labels`의 표로 화면 문구를 만듭니다.
  - 링크를 버튼 모양으로: `<Link className={buttonVariants({ variant: 'outline' })}>` (버튼을 링크로 감싸지 않음)
  - 비슷한 컴포넌트를 새로 만들기 전에 기존 것에 `variant`를 추가할 수 있는지 봅니다.
- 전체 모양은 `/dev/components` 화면에서 확인할 수 있습니다.
- 금액·시간 표시는 `@/lib/format`의 함수를 씁니다. (`formatPoint`, `formatRemaining`, `formatDateTime`)

## 접근성 (화면에 그리는 순간부터 지킴)

- 누르는 것은 `<button>`, 이동은 `<Link>`/`<a href>`입니다. `div`에 `onClick`을 달지 않습니다.
- 아이콘만 있는 버튼에는 `aria-label`을 붙입니다. 장식 아이콘에는 `aria-hidden="true"`를 붙입니다.
- 모든 입력에는 라벨이 있습니다. (`<Input label=...>`, 좁으면 `hideLabel`)
- 상태를 색으로만 구분하지 않습니다. (배지에 글자를 함께)
- 실시간으로 바뀌는 영역(채팅, 입찰 알림)에는 `aria-live="polite"`를 둡니다.

## 테스트와 검사

PR을 올리기 전에 반드시 통과시킵니다. CI도 같은 검사를 합니다.

```bash
npm run check   # 타입 검사 + 린트 + 단위 테스트
npm run build
```

- 계산·변환 로직(금액, 시간, 상태 판단)과 API 처리에는 단위 테스트를 씁니다. (`*.test.ts(x)`)
- 화면의 주요 흐름(로그인, 방송 시청, 입찰)은 E2E 테스트로 확인합니다. (Playwright, 다음 단계에서 추가)

## 로컬 실행

```bash
npm install
npm run dev   # http://localhost:5173
```

백엔드(8080)를 같이 띄워야 API가 동작합니다. `/api`, `/ws` 요청은 Vite가 백엔드로 넘깁니다. 다른 포트면 `frontend/.env.local`에 `BACKEND_URL=http://localhost:8081`을 적습니다.
