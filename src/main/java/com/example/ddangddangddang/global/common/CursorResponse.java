package com.example.ddangddangddang.global.common;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 기반 페이지 응답. 포인트 내역, 채팅 메시지 등 "더 보기" 방식 목록에서 공통으로 쓴다.
 * 다음 페이지 요청 시 nextCursor를 cursor 파라미터로 넘긴다.
 */
public record CursorResponse<T>(List<T> content, Long nextCursor, boolean hasNext) {

    /**
     * size + 1개를 조회한 결과를 받아, 다음 페이지가 있는지 판단하고 size개만 잘라 응답을 만든다.
     */
    public static <E, T> CursorResponse<T> of(List<E> fetched, int size,
                                              Function<E, T> mapper, Function<E, Long> idExtractor) {
        boolean hasNext = fetched.size() > size;
        List<E> page = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? idExtractor.apply(page.get(page.size() - 1)) : null;
        return new CursorResponse<>(page.stream().map(mapper).toList(), nextCursor, hasNext);
    }
}