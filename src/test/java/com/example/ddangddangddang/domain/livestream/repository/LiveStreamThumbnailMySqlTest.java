package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방송 썸네일 조건부 갱신(이전 키가 같을 때만, 버전은 그대로)이 실제 MySQL에서 동작하는지 확인한다. Docker가 실행 중이어야 한다.
 */
class LiveStreamThumbnailMySqlTest extends MySqlIntegrationTest {

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("이전 키가 같을 때만 바꾸고(처음은 null), 다른 요청이 먼저 바꿨으면 바꾸지 않는다. 버전은 올리지 않는다")
    void updateThumbnailKeyOnMySql() {
        LiveStream stream = LiveStream.create(9001L, "썸네일 방송", null);
        stream.start(9001L);
        Long id = liveStreamRepository.save(stream).getId();
        Long version = liveStreamRepository.findById(id).orElseThrow().getVersion();

        assertThat(update(id, "live-thumbnails/a.jpg", null)).isEqualTo(1);
        assertThat(update(id, "live-thumbnails/b.jpg", "live-thumbnails/a.jpg")).isEqualTo(1);
        // 늦게 온 요청은 아직 a라고 알고 있다.
        assertThat(update(id, "live-thumbnails/c.jpg", "live-thumbnails/a.jpg")).isZero();
        assertThat(update(id, "live-thumbnails/d.jpg", null)).isZero();

        LiveStream saved = liveStreamRepository.findById(id).orElseThrow();
        assertThat(saved.getThumbnailKey()).isEqualTo("live-thumbnails/b.jpg");
        assertThat(saved.getVersion()).isEqualTo(version);
    }

    private int update(Long id, String key, String previousKey) {
        Integer updated = transactionTemplate.execute(status -> liveStreamRepository.updateThumbnailKey(id, key, previousKey));
        return updated == null ? 0 : updated;
    }
}
