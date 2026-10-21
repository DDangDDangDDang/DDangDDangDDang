package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LiveStreamRepository extends JpaRepository<LiveStream, Long> {

    boolean existsBySellerIdAndStatus(Long sellerId, LiveStreamStatus status);

    boolean existsByIdAndStatus(Long id, LiveStreamStatus status);

    // 판매자의 종료되지 않은 방송 (대기 중·방송 중). 판매자당 하나만 있다.
    Optional<LiveStream> findByActiveSellerId(Long sellerId);

    boolean existsByActiveSellerId(Long sellerId);

    // 시청자 수 집계 대상 (진행 중인 방송 전체)
    List<LiveStream> findAllByStatus(LiveStreamStatus status);

    // 홈 화면에서 시청자 수 정보가 아직 없을 때 채우는 용도. 최근 시작한 방송부터
    List<LiveStream> findByStatusOrderByStartedAtDesc(LiveStreamStatus status, Pageable pageable);

    // 홈 화면 방송 예정: 시작 예정 시간이 from 이후인 방송을 가까운 순으로 (시작 예정 시간을 정하지 않은 방송은 제외)
    List<LiveStream> findByStatusAndScheduledStartAtGreaterThanEqualOrderByScheduledStartAtAsc(
            LiveStreamStatus status, LocalDateTime from, Pageable pageable);

    /*
     * 방송 목록·검색. 상태마다 정렬 기준이 달라 쿼리를 나눈다.
     * - sellerId: 그 판매자의 방송만 (null이면 전체)
     * - pattern: 검색어 LIKE 패턴 ("%나이키%", 소문자, %·_·!는 !로 이스케이프). 제목·설명·해시태그·판매자 닉네임에서 찾는다. null이면 전체
     *   해시태그는 "#나이키 #운동화"로 저장하므로 "나이키"와 "#나이키" 둘 다 찾는다.
     * - cursorAt·cursorId: 이전 페이지 마지막 방송의 정렬 시간과 ID. 같은 시간이 여러 개여도 빠짐없이 넘기도록 ID로 한 번 더 정렬한다.
     */
    String SEARCH_CONDITION = """
            (:sellerId is null or s.sellerId = :sellerId)
            and (:pattern is null
                 or lower(s.title) like :pattern escape '!'
                 or lower(s.description) like :pattern escape '!'
                 or lower(s.hashtags) like :pattern escape '!'
                 or s.sellerId in (select u.id from User u where lower(u.nickname) like :pattern escape '!'))
            """;

    // 방송 중: 최근 시작한 순
    @Query("select s from LiveStream s where s.status = com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus.LIVE and "
            + SEARCH_CONDITION + """
            and (:cursorAt is null or s.startedAt < :cursorAt or (s.startedAt = :cursorAt and s.id < :cursorId))
            order by s.startedAt desc, s.id desc
            """)
    List<LiveStream> findLiveList(@Param("sellerId") Long sellerId, @Param("pattern") String pattern,
                                  @Param("cursorAt") LocalDateTime cursorAt, @Param("cursorId") Long cursorId,
                                  Pageable pageable);

    // 방송 예정: 시작 예정 시간이 가까운 순 (시작 예정 시간을 정하지 않은 방송은 제외)
    @Query("select s from LiveStream s where s.status = com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus.SCHEDULED"
            + " and s.scheduledStartAt is not null and " + SEARCH_CONDITION + """
            and (:cursorAt is null or s.scheduledStartAt > :cursorAt or (s.scheduledStartAt = :cursorAt and s.id > :cursorId))
            order by s.scheduledStartAt asc, s.id asc
            """)
    List<LiveStream> findScheduledList(@Param("sellerId") Long sellerId, @Param("pattern") String pattern,
                                       @Param("cursorAt") LocalDateTime cursorAt, @Param("cursorId") Long cursorId,
                                       Pageable pageable);

    // 종료: 최근 종료한 순
    @Query("select s from LiveStream s where s.status = com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus.ENDED and "
            + SEARCH_CONDITION + """
            and (:cursorAt is null or s.endedAt < :cursorAt or (s.endedAt = :cursorAt and s.id < :cursorId))
            order by s.endedAt desc, s.id desc
            """)
    List<LiveStream> findEndedList(@Param("sellerId") Long sellerId, @Param("pattern") String pattern,
                                   @Param("cursorAt") LocalDateTime cursorAt, @Param("cursorId") Long cursorId,
                                   Pageable pageable);
}
