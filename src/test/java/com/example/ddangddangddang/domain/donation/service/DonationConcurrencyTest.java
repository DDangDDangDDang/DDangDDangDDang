package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.dto.DonationRequest;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.repository.DonationLotUsageRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointHistoryRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationWalletRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선물이 동시에 들어올 때: 한 달 한도를 넘지 않고, 같은 요청 키는 한 번만, 서로에게 동시에 선물해도 데드락이 나지 않는다.
 * 실제 DB 락이 동작해야 하므로 MySQL에서 @Transactional 없이 실행하고, 끝나면 직접 지운다.
 */
class DonationConcurrencyTest extends MySqlIntegrationTest {

    private static final Long GIVER = 1L;
    private static final Long SELLER = 2L;
    private static final Long OTHER_SELLER = 3L;

    @Autowired
    private DonationService donationService;

    @Autowired
    private DonationPointService donationPointService;

    @Autowired
    private DonationWalletRepository walletRepository;

    @Autowired
    private DonationPointLotRepository lotRepository;

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private DonationLotUsageRepository usageRepository;

    @Autowired
    private DonationPointHistoryRepository historyRepository;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    private long orderSeq = 1_000;

    @AfterEach
    void tearDown() {
        usageRepository.deleteAllInBatch();
        donationRepository.deleteAllInBatch();
        historyRepository.deleteAllInBatch();
        lotRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        liveStreamRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("한 판매자에게 1,000P 선물이 동시에 10번 와도 한 달 한도(5,000P)까지만 선물된다")
    void monthlyLimit() throws Exception {
        Long live = liveStream(SELLER);
        earn(GIVER, OTHER_SELLER, 8);

        AtomicInteger success = new AtomicInteger();
        List<Integer> rejected = run(10, () -> {
            donationService.gift(live, GIVER, "시청자", new DonationRequest(1_000L, null, UUID.randomUUID().toString()));
            success.incrementAndGet();
            return null;
        });

        assertThat(success.get()).isEqualTo(5);
        assertThat(rejected).hasSize(5);
        assertThat(walletRepository.findByUserId(GIVER).orElseThrow().getGiftableBalance()).isEqualTo(3_000);
        assertThat(walletRepository.findByUserId(SELLER).orElseThrow().getReceivedBalance()).isEqualTo(5_000);
        // 지갑 잔액은 적립 묶음의 남은 양 합계와 같다.
        assertThat(lotRepository.findAll().stream().filter(l -> l.getUserId().equals(GIVER)).mapToLong(DonationPointLot::getRemaining).sum())
                .isEqualTo(3_000);
    }

    @Test
    @DisplayName("같은 요청 키로 동시에 10번 보내도 한 번만 선물된다")
    void sameRequestKey() throws Exception {
        Long live = liveStream(SELLER);
        earn(GIVER, OTHER_SELLER, 2);
        String key = UUID.randomUUID().toString();

        List<Integer> rejected = run(10, () ->
                donationService.gift(live, GIVER, "시청자", new DonationRequest(500L, null, key)));

        assertThat(rejected).isEmpty();
        assertThat(donationRepository.count()).isEqualTo(1);
        assertThat(walletRepository.findByUserId(GIVER).orElseThrow().getGiftableBalance()).isEqualTo(1_500);
    }

    @Test
    @DisplayName("방송 중인 두 판매자가 서로에게 동시에 선물해도 데드락 없이 모두 처리된다")
    void crossGift() throws Exception {
        Long sellerLive = liveStream(SELLER);
        Long otherLive = liveStream(OTHER_SELLER);
        earn(SELLER, GIVER, 5);
        earn(OTHER_SELLER, GIVER, 5);

        AtomicInteger turn = new AtomicInteger();
        List<Integer> rejected = run(10, () -> {
            boolean even = turn.getAndIncrement() % 2 == 0;
            Long target = even ? otherLive : sellerLive;
            Long giver = even ? SELLER : OTHER_SELLER;
            return donationService.gift(target, giver, "판매자", new DonationRequest(100L, null, UUID.randomUUID().toString()));
        });

        assertThat(rejected).isEmpty();
        assertThat(walletRepository.findByUserId(SELLER).orElseThrow().getReceivedBalance()).isEqualTo(500);
        assertThat(walletRepository.findByUserId(OTHER_SELLER).orElseThrow().getReceivedBalance()).isEqualTo(500);
    }

    /** 다른 판매자와의 거래로 1,000P씩 count번 적립 */
    private void earn(Long userId, Long fromSeller, int count) {
        for (int i = 0; i < count; i++) {
            donationPointService.earn(orderSeq++, userId, fromSeller, 100_000L);
        }
    }

    private Long liveStream(Long sellerId) {
        LiveStream stream = LiveStream.create(sellerId, "방송 " + sellerId);
        stream.start(sellerId);
        return liveStreamRepository.save(stream).getId();
    }

    /** 작업을 동시에 count번 돌린다. @return 거부된(BusinessException) 횟수만큼의 목록 */
    private List<Integer> run(int count, Callable<?> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    task.call();
                    return 0;
                } catch (BusinessException e) {
                    return 1;
                }
            }));
        }
        start.countDown();
        List<Integer> rejected = new ArrayList<>();
        for (Future<Integer> future : futures) {
            if (future.get() == 1) {
                rejected.add(1);
            }
        }
        executor.shutdown();
        return rejected;
    }
}
