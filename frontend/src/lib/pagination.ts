/** 보여줄 페이지 번호 수 */
const WINDOW = 5;

/** 지금 페이지를 가운데에 두고 보여줄 페이지 번호(0부터)를 구한다. */
export function pageWindow(page: number, totalPages: number, size = WINDOW): number[] {
  if (totalPages <= 0) return [];
  const start = Math.max(0, Math.min(page - Math.floor(size / 2), totalPages - size));
  return Array.from({ length: Math.min(size, totalPages) }, (_, i) => start + i);
}
