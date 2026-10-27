package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateRequest;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 회원이 동시에 같은 닉네임으로 바꾸면 둘 다 "아직 없음"을 확인하고 통과할 수 있다.
 * 이때는 유니크 제약이 막고, 서비스가 그 예외를 DUPLICATE_NICKNAME으로 바꿔야 한다. (실제 MySQL)
 */
class UserNicknameConcurrencyTest extends MySqlIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("여러 회원이 동시에 같은 닉네임으로 바꾸면 한 명만 성공하고 나머지는 DUPLICATE_NICKNAME")
    void concurrentSameNickname() throws Exception {
        int threadCount = 5;
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            userIds.add(userRepository.save(User.create("user" + i + "@test.com", "encoded", "회원" + i)).getId());
        }

        AtomicInteger success = new AtomicInteger();
        List<Object> rejectedCodes = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (Long userId : userIds) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    userService.updateMyInfo(userId, new MyInfoUpdateRequest("인기닉네임"));
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    rejectedCodes.add(e.getErrorCode());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        long owners = userRepository.findAll().stream()
                .filter(user -> user.getNickname().equals("인기닉네임"))
                .count();
        assertThat(success.get()).isEqualTo(1);
        assertThat(rejectedCodes).hasSize(threadCount - 1)
                .allMatch(code -> code == UserErrorCode.DUPLICATE_NICKNAME);
        assertThat(owners).isEqualTo(1);
    }
}