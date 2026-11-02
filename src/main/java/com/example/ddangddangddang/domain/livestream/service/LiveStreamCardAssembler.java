package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.donation.service.PowerSellers;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCard;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 방송 목록을 화면용 카드로 바꾼다. 홈 화면과 방송 목록·검색이 같이 쓴다.
 * 판매자 닉네임은 한 번에 조회하고, 방송 중인 방송의 시청자 수는 Redis 집계 값을 붙인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveStreamCardAssembler {

    private final LiveViewerStatsRepository statsRepository;
    private final UserRepository userRepository;
    // 카드에 파워 판매자 배지를 붙이려고 도네이션 도메인을 읽기만 한다.
    private final PowerSellers powerSellers;
    private final FileStorage fileStorage;

    public List<LiveStreamCard> toCards(List<LiveStream> streams) {
        if (streams.isEmpty()) {
            return List.of();
        }
        // 시청자 수는 방송 중인 방송에만 있다. 방송 예정·종료만 있는 목록이면 Redis를 읽지 않는다.
        List<Long> liveIds = streams.stream()
                .filter(stream -> stream.getStatus() == LiveStreamStatus.LIVE)
                .map(LiveStream::getId)
                .toList();
        Map<Long, Integer> viewers = Map.of();
        Map<Long, Integer> increases = Map.of();
        if (!liveIds.isEmpty()) {
            try {
                viewers = statsRepository.getViewerCounts(liveIds);
                increases = statsRepository.getIncreases(liveIds);
            } catch (RuntimeException e) {
                log.warn("시청자 수를 읽지 못해 0으로 보여 줍니다.", e);
            }
        }
        Map<Long, String> nicknames = userRepository.findAllById(
                        streams.stream().map(LiveStream::getSellerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getNickname));

        Set<Long> power = powerSellers.among(streams.stream().map(LiveStream::getSellerId).distinct().toList());
        Map<Long, Integer> viewerCounts = viewers;
        Map<Long, Integer> viewerIncreases = increases;
        return streams.stream()
                .map(stream -> new LiveStreamCard(stream.getId(), stream.getTitle(), stream.getHashtagList(), stream.getStatus(), stream.getSellerId(),
                        nicknames.get(stream.getSellerId()), stream.getScheduledStartAt(), stream.getStartedAt(),
                        stream.getEndedAt(), viewerCounts.getOrDefault(stream.getId(), 0),
                        viewerIncreases.getOrDefault(stream.getId(), 0), stream.isRecorded(), power.contains(stream.getSellerId()),
                        stream.getThumbnailKey() == null ? null : fileStorage.publicUrl(stream.getThumbnailKey())))
                .toList();
    }
}
