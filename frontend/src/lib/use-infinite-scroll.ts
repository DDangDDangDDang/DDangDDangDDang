import { useEffect, useRef } from 'react';

/**
 * 목록 끝에 둔 요소(sentinel)가 화면에 보이면 다음 페이지를 불러온다.
 *
 *   const sentinelRef = useInfiniteScroll(query.hasNextPage, query.isFetchingNextPage, query.fetchNextPage);
 *   <div ref={sentinelRef} />
 */
export function useInfiniteScroll(hasNext: boolean, isFetching: boolean, fetchNext: () => unknown) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const target = ref.current;
    if (!target || !hasNext) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting) && !isFetching) fetchNext();
      },
      { rootMargin: '200px' },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [hasNext, isFetching, fetchNext]);

  return ref;
}
