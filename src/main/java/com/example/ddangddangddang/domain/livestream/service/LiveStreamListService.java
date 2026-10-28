package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCard;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 방송 목록과 검색. 상태별로 하나씩 보여 준다.
 * - LIVE(기본): 최근 시작한 순
 * - SCHEDULED: 시작 예정 시간이 가까운 순 (시작 예정 시간을 정하지 않은 방송은 제외)
 * - ENDED: 최근 종료한 순
 *
 * 검색어가 있으면 제목·설명·판매자 닉네임에 검색어가 들어간 방송만 보여 준다. (대소문자 구분 없음)
 * 지금은 LIKE '%검색어%'로 찾는다. 상태로 먼저 좁혀서 당분간 충분히 빠르고, 방송이 많아지면 이 클래스와
 * LiveStreamRepository의 검색 조건만 MySQL FULLTEXT 등으로 바꾸면 된다. (API는 그대로)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStreamListService {

    static final int MAX_SIZE = 50;
    static final int MAX_KEYWORD_LENGTH = 50;
    private static final char LIKE_ESCAPE = '!';

    private final LiveStreamRepository liveStreamRepository;
    private final LiveStreamCardAssembler cardAssembler;

    /**
     * @param status  null이면 LIVE
     * @param cursor  이전 페이지 응답의 nextCursor (마지막 방송 ID). null이면 처음부터
     */
    public CursorResponse<LiveStreamCard> getList(LiveStreamStatus status, Long sellerId, String keyword, Long cursor, int size) {
        LiveStreamStatus target = status == null ? LiveStreamStatus.LIVE : status;
        int pageSize = Math.max(1, Math.min(size, MAX_SIZE));
        String pattern = likePattern(keyword);
        LocalDateTime cursorAt = cursor == null ? null : cursorTime(cursor, target);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<LiveStream> fetched = switch (target) {
            case LIVE -> liveStreamRepository.findLiveList(sellerId, pattern, cursorAt, cursor, pageable);
            case SCHEDULED -> liveStreamRepository.findScheduledList(sellerId, pattern, cursorAt, cursor, pageable);
            case ENDED -> liveStreamRepository.findEndedList(sellerId, pattern, cursorAt, cursor, pageable);
        };

        boolean hasNext = fetched.size() > pageSize;
        List<LiveStream> page = hasNext ? fetched.subList(0, pageSize) : fetched;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new CursorResponse<>(cardAssembler.toCards(page), nextCursor, hasNext);
    }

    /**
     * 검색어를 LIKE 패턴으로 바꾼다. 비어 있으면 null(검색 안 함).
     * 검색어 안의 %, _는 와일드카드가 아니라 글자로 찾도록 이스케이프한다. ("100%" 검색이 모든 방송과 맞지 않게)
     */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String stripped = keyword.strip();
        if (stripped.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "검색어는 " + MAX_KEYWORD_LENGTH + "자 이하로 입력해 주세요.");
        }
        String escaped = stripped.toLowerCase(Locale.ROOT)
                .replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + String.valueOf(LIKE_ESCAPE))
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }

    /**
     * 커서(이전 페이지 마지막 방송)의 정렬 시간. 그 방송이 없거나 이 목록에 한 번도 있지 않았으면 잘못된 커서다.
     *
     * 커서 방송의 "지금 상태"가 요청한 status와 같은지는 보지 않는다. 목록을 넘기는 사이 방송 상태가 바뀔 수 있기 때문이다.
     * (방송 중 목록 1페이지의 마지막 방송이 그 사이 종료되거나, 방송 예정 방송이 시작됨) 지금 상태로 막으면 정상적인 "더 보기"가 실패한다.
     * 대신 그 상태의 정렬 시간이 있는지로 "이 목록에 있었던 방송"인지 확인한다.
     * - 시작 시간이 있으면 방송 중 목록에 있었고, 시작 예정 시간이 있으면 방송 예정 목록에, 종료 시간이 있으면 종료 목록에 있었다.
     * 정렬 시간은 상태가 바뀌어도 그대로 남으므로, 그 시간과 ID 기준으로 다음 페이지를 이어서 가져온다.
     */
    private LocalDateTime cursorTime(Long cursor, LiveStreamStatus status) {
        LiveStream last = liveStreamRepository.findById(cursor)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT, "잘못된 커서입니다."));
        LocalDateTime time = switch (status) {
            case LIVE -> last.getStartedAt();
            case SCHEDULED -> last.getScheduledStartAt();
            case ENDED -> last.getEndedAt();
        };
        if (time == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "잘못된 커서입니다.");
        }
        return time;
    }
}
