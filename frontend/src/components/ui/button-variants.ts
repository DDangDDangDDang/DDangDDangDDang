import { cva } from 'class-variance-authority';

/** 링크(<Link>)를 버튼 모양으로 만들 때도 쓴다. 버튼을 링크로 감싸지 않는다. */
export const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-md font-semibold transition-colors duration-150 ease-out disabled:cursor-not-allowed disabled:bg-neutral-100 disabled:text-neutral-500 disabled:border-transparent',
  {
    variants: {
      variant: {
        /** 화면의 주요 동작 하나 (입찰하기, 방송 시작 등) */
        primary: 'bg-brand-500 text-on-brand hover:bg-brand-600',
        /** 보조 동작 (전송, 확인 등) */
        secondary: 'bg-neutral-900 text-neutral-0 hover:bg-neutral-700',
        /** 테두리 버튼 (취소, +1,000 같은 빠른 선택) */
        outline: 'border border-neutral-200 bg-neutral-0 text-neutral-900 hover:bg-neutral-50',
        /** 배경 없는 버튼 (헤더 메뉴, 아이콘 버튼) */
        ghost: 'bg-transparent text-neutral-900 hover:bg-neutral-50',
        /** 카카오 로그인 버튼만 (카카오 디자인 가이드: 노란 바탕, 검은 글자) */
        kakao: 'bg-kakao text-kakao-label hover:bg-kakao-hover',
      },
      size: {
        sm: 'h-9 px-3 text-label',
        md: 'h-11 px-4 text-body',
        /** 입찰하기처럼 가장 중요한 버튼 */
        lg: 'h-13 px-5 text-body-lg',
        /** 아이콘만 있는 버튼. aria-label 필수 */
        icon: 'h-11 w-11',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

