package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 판매자당 종료되지 않은 방송 하나 규칙을 실제 MySQL에서 확인한다.
 * - db/backfill/live_streams_active_seller.sql: 규칙 도입 전 데이터(active_seller_id가 비어 있음)를 규칙에 맞추는지
 * - 유니크 제약 위반을 제약 이름으로 판별하는지 (MySQL은 제약 이름을 "live_streams.uk_..." 형태로 알려 준다)
 * Docker가 실행 중이어야 한다.
 */
class LiveStreamActiveSellerMySqlTest extends MySqlIntegrationTest {

    private static final long SELLER_LIVE_AND_SCHEDULED = 7001L;
    private static final long SELLER_TWO_SCHEDULED = 7002L;
    private static final long SELLER_ONE_SCHEDULED = 7003L;
    private static final long SELLER_ONLY_ENDED = 7004L;
    private static final long SELLER_CONCURRENT = 7005L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LiveStreamService liveStreamService;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("delete from live_streams where seller_id between 7000 and 7999");
    }

    @Test
    @DisplayName("backfill: 판매자마다 방송 중인 것(없으면 최신)만 남기고 나머지를 종료하며, 남은 방송에 active_seller_id를 채운다. 두 번 실행해도 같다")
    void backfill() throws Exception {
        long scheduledOfA = legacy(SELLER_LIVE_AND_SCHEDULED, "SCHEDULED");
        long liveOfA = legacy(SELLER_LIVE_AND_SCHEDULED, "LIVE");
        long olderOfB = legacy(SELLER_TWO_SCHEDULED, "SCHEDULED");
        long newerOfB = legacy(SELLER_TWO_SCHEDULED, "SCHEDULED");
        long onlyOfC = legacy(SELLER_ONE_SCHEDULED, "SCHEDULED");
        long endedOfD = legacy(SELLER_ONLY_ENDED, "ENDED");

        runBackfill();
        runBackfill();

        assertThat(row(scheduledOfA)).containsEntry("status", "ENDED").containsEntry("active_seller_id", null);
        assertThat(row(scheduledOfA).get("ended_at")).isNotNull();
        assertThat(row(liveOfA)).containsEntry("status", "LIVE").containsEntry("active_seller_id", SELLER_LIVE_AND_SCHEDULED);
        assertThat(row(olderOfB)).containsEntry("status", "ENDED").containsEntry("active_seller_id", null);
        assertThat(row(newerOfB)).containsEntry("status", "SCHEDULED").containsEntry("active_seller_id", SELLER_TWO_SCHEDULED);
        assertThat(row(onlyOfC)).containsEntry("status", "SCHEDULED").containsEntry("active_seller_id", SELLER_ONE_SCHEDULED);
        assertThat(row(endedOfD)).containsEntry("status", "ENDED").containsEntry("active_seller_id", null);
    }

    @Test
    @DisplayName("backfill 뒤에는 기존 방송이 있던 판매자도 새 방송을 만들 수 없다 (LIVESTREAM_010)")
    void ruleAppliesAfterBackfill() throws Exception {
        legacy(SELLER_ONE_SCHEDULED, "SCHEDULED");
        runBackfill();

        assertThatThrownBy(() -> liveStreamService.create(SELLER_ONE_SCHEDULED, new LiveStreamCreateRequest("새 방송", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().getCode())
                .isEqualTo("LIVESTREAM_010");
    }

    @Test
    @DisplayName("MySQL에서 동시에 만들어도 하나만 성공하고, 유니크 위반은 제약 이름으로 판별되어 LIVESTREAM_010이 된다")
    void concurrentCreateOnMySql() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            results.add(executor.submit(() -> {
                ready.await();
                try {
                    liveStreamService.create(SELLER_CONCURRENT, new LiveStreamCreateRequest("동시 방송 " + n, null));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getErrorCode().getCode();
                }
            }));
        }
        ready.countDown();

        List<String> codes = new ArrayList<>();
        for (Future<String> result : results) {
            codes.add(result.get());
        }
        executor.shutdown();
        assertThat(codes).containsOnlyOnce("OK");
        assertThat(codes).filteredOn(code -> !code.equals("OK")).containsOnly("LIVESTREAM_010");
    }

    // 규칙 도입 전에 만들어진 방송처럼 active_seller_id 없이 넣는다.
    private long legacy(long sellerId, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into live_streams (seller_id, title, room_name, status, active_seller_id, version, created_at)
                    values (?, '기존 방송', ?, ?, null, 0, now(6))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, sellerId);
            statement.setString(2, "live-" + UUID.randomUUID());
            statement.setString(3, status);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("select status, active_seller_id, ended_at from live_streams where id = ?", id);
    }

    private void runBackfill() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/backfill/live_streams_active_seller.sql"));
        }
    }
}
