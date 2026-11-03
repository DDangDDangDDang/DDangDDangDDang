package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 직접 올린 썸네일을 바꾸는 동안 자동 캡처가 끼어들어도 자동 캡처 키를 옛 값으로 되돌리지 않는지 실제 MySQL에서 확인한다. Docker가 실행 중이어야 한다.
 */
class LiveStreamCustomThumbnailMySqlTest extends MySqlIntegrationTest {

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("방송을 읽은 뒤 자동 캡처가 키를 바꿔도, 직접 올린 썸네일을 저장할 때 자동 캡처 키는 새 값 그대로 남는다")
    void customThumbnailKeepsConcurrentAutoCapture() {
        LiveStream stream = LiveStream.create(9002L, "썸네일 방송", null);
        stream.start(9002L);
        Long id = liveStreamRepository.save(stream).getId();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        inner.executeWithoutResult(status -> liveStreamRepository.updateThumbnailKey(id, "live-thumbnails/a.jpg", null));

        outer.executeWithoutResult(status -> {
            LiveStream loaded = liveStreamRepository.findById(id).orElseThrow();
            // 판매자 요청이 방송을 읽은 뒤, 1분마다 도는 자동 캡처가 먼저 커밋된다.
            inner.executeWithoutResult(s -> liveStreamRepository.updateThumbnailKey(id, "live-thumbnails/b.jpg", "live-thumbnails/a.jpg"));
            loaded.changeCustomThumbnail(9002L, "live-thumbnails/custom/c.png");
            liveStreamRepository.saveAndFlush(loaded);
        });

        LiveStream saved = liveStreamRepository.findById(id).orElseThrow();
        assertThat(saved.getCustomThumbnailKey()).isEqualTo("live-thumbnails/custom/c.png");
        assertThat(saved.getThumbnailKey()).isEqualTo("live-thumbnails/b.jpg");
    }
}
