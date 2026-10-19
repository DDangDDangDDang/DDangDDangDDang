package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCard;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamListService;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방송 검색 쿼리(LIKE + 이스케이프 + 판매자 닉네임 서브쿼리)가 실제 MySQL에서도 같은 결과를 내는지 확인한다.
 * H2와 MySQL은 문자열 비교·이스케이프 처리가 달라질 수 있어 운영과 같은 MySQL로 한 번 더 본다. Docker가 실행 중이어야 한다.
 */
class LiveStreamSearchMySqlTest extends MySqlIntegrationTest {

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LiveStreamListService listService;

    @Test
    @DisplayName("한글 검색어로 제목·설명·판매자 닉네임에서 찾고, %는 글자 그대로 찾는다")
    void searchOnMySql() {
        String token = "검색" + UUID.randomUUID().toString().substring(0, 6);
        Long byTitle = live(token + " 100% 정품 나이키", null, "판매자");
        Long byDescription = live("아무 제목", "오늘은 " + token + " 특가", "판매자");
        Long byNickname = live("또 다른 제목", null, token);
        live(token + " 1000원", null, "판매자");

        assertThat(listService.getList(LiveStreamStatus.LIVE, null, token, null, 20).content())
                .extracting(LiveStreamCard::liveStreamId)
                .contains(byTitle, byDescription, byNickname)
                .hasSize(4);
        assertThat(listService.getList(LiveStreamStatus.LIVE, null, token + " 100%", null, 20).content())
                .extracting(LiveStreamCard::liveStreamId)
                .containsExactly(byTitle);
    }

    private Long live(String title, String description, String nickname) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User seller = userRepository.save(User.create("search-" + suffix + "@test.com", "encoded", nickname + suffix));
        LiveStream stream = LiveStream.create(seller.getId(), title, description);
        stream.start(seller.getId());
        return liveStreamRepository.save(stream).getId();
    }
}
