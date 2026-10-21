package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.dto.request.AuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 상품으로 경매 생성 요청이 동시에 여러 번 와도 "대기/진행 중 경매는 상품당 하나" 규칙이 지켜지는지 확인한다.
 * 실제 DB 락이 동작해야 하므로 @Transactional 없이 실행하고, 끝나면 직접 데이터를 지운다.
 */
class AuctionCreateConcurrencyTest extends MySqlIntegrationTest {

    private static final int THREAD_COUNT = 10;

    @Autowired private AuctionService auctionService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LiveStreamRepository liveStreamRepository;

    private User seller;
    private Product product;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(User.create("auction-seller@test.com", "pw", "auctionSeller", "01088887777"));
        Category category = categoryRepository.save(new Category(null, "전자기기", 1));
        product = productRepository.save(new Product(seller, category, "아이패드", "설명"));
    }

    @AfterEach
    void tearDown() {
        auctionRepository.deleteAllInBatch();
        liveStreamRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("일반 경매: 같은 상품으로 동시에 생성해도 하나만 만들어진다")
    void concurrentAuctionCreate() throws Exception {
        LocalDateTime startAt = LocalDateTime.now().plusHours(1);
        AuctionCreateRequest request = new AuctionCreateRequest(
                product.getId(), 10_000L, 1_000L, null, startAt, startAt.plusHours(1));

        Result result = runConcurrently(() -> auctionService.auctionCreate(seller.getId(), request));

        assertOnlyOneCreated(result);
    }

    @Test
    @DisplayName("라이브 경매: 같은 상품으로 동시에 생성해도 하나만 만들어진다 (상품 락보다 방송 조회가 먼저면 실패하던 경우)")
    void concurrentLiveAuctionCreate() throws Exception {
        LiveStream liveStream = LiveStream.create(seller.getId(), "라이브 경매 방송");
        liveStream.start(seller.getId());
        Long liveId = liveStreamRepository.save(liveStream).getId();
        LiveAuctionCreateRequest request = new LiveAuctionCreateRequest(
                product.getId(), 10_000L, 1_000L, null, 300);

        Result result = runConcurrently(() -> auctionService.createLiveAuction(seller.getId(), liveId, request));

        assertOnlyOneCreated(result);
    }

    private void assertOnlyOneCreated(Result result) {
        assertThat(result.success().get()).isEqualTo(1);
        assertThat(result.alreadyInAuction().get()).isEqualTo(THREAD_COUNT - 1);
        assertThat(result.unexpected().get()).isZero();
        assertThat(auctionRepository.existsByProductIdAndStatusIn(
                product.getId(), List.of(AuctionStatus.READY, AuctionStatus.ONGOING))).isTrue();
        assertThat(auctionRepository.count()).isEqualTo(1);
    }

    private Result runConcurrently(Callable<?> action) throws Exception {
        Result result = new Result(new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit((Callable<Void>) () -> {
                start.await();
                try {
                    action.call();
                    result.success().incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == AuctionErrorCode.ALREADY_IN_AUCTION) {
                        result.alreadyInAuction().incrementAndGet();
                    } else {
                        result.unexpected().incrementAndGet();
                    }
                } catch (Exception e) {
                    result.unexpected().incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();
        return result;
    }

    private record Result(AtomicInteger success, AtomicInteger alreadyInAuction, AtomicInteger unexpected) {
    }
}
