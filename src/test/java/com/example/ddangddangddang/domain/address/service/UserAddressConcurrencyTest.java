package com.example.ddangddangddang.domain.address.service;

import com.example.ddangddangddang.domain.address.dto.request.UserAddressRequest;
import com.example.ddangddangddang.domain.address.entity.UserAddress;
import com.example.ddangddangddang.domain.address.repository.UserAddressRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배송지를 동시에 추가해도 최대 개수(10개)를 넘지 않고, 첫 배송지가 동시에 들어와도 기본 배송지는 하나다. (실제 MySQL)
 * 회원 행을 잠그지 않으면 요청마다 같은 개수를 읽어 둘 다 통과한다.
 */
class UserAddressConcurrencyTest extends MySqlIntegrationTest {

    @Autowired
    private UserAddressService userAddressService;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userAddressRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동시에 15개를 추가하면 10개만 저장되고 나머지는 거절된다")
    void limitUnderConcurrency() throws Exception {
        Long userId = userRepository.save(User.create("addr@test.com", "encoded", "배송회원")).getId();

        AtomicInteger rejected = new AtomicInteger();
        runConcurrently(15, () -> {
            try {
                userAddressService.createAddress(userId, request());
            } catch (BusinessException e) {
                rejected.incrementAndGet();
            }
            return null;
        });

        assertThat(userAddressRepository.countByUserId(userId)).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("배송지가 없을 때 동시에 추가해도 기본 배송지는 하나다")
    void singleDefaultUnderConcurrency() throws Exception {
        Long userId = userRepository.save(User.create("first@test.com", "encoded", "첫배송")).getId();

        runConcurrently(5, () -> {
            userAddressService.createAddress(userId, request());
            return null;
        });

        List<UserAddress> addresses = userAddressRepository.findByUserIdOrderByIsDefaultDescIdDesc(userId);
        assertThat(addresses).hasSize(5);
        assertThat(addresses).filteredOn(UserAddress::isDefault).hasSize(1);
    }

    private static UserAddressRequest request() {
        return new UserAddressRequest("집", "홍길동", "01012345678", "서울시 어딘가 1", null);
    }

    private static void runConcurrently(int threadCount, Callable<Void> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();
    }
}
