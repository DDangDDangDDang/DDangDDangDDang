import { clsx, type ClassValue } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

// 커스텀 글자 크기 토큰(text-body 등)을 색 클래스로 오인해 지우지 않도록 알려 준다.
const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      'font-size': [{ text: ['caption', 'label', 'body', 'body-lg', 'title-md', 'title-lg', 'price-lg', 'display'] }],
    },
  },
});

/** 조건부 클래스를 합치고, 겹치는 Tailwind 클래스는 뒤의 것을 남긴다. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
