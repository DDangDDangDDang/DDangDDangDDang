package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.repository.BidRepository;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
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
import org.springframework.test.util.ReflectionTestUtils;

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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 MySQL에서 입찰 동시성을 확인한다. 락이 실제로 동작해야 하므로 @Transactional 없이 실행하고 끝나면 직접 지운다.
 *
 * 매 테스트 공통으로 확인하는 불변식:
 * - 포인트 총량 보존: 모든 지갑의 (사용 가능 + 홀딩) 합은 처음과 같다 (즉시구매 차감분 제외)
 * - 홀딩은 최고 입찰자에게만, 정확히 현재가만큼 있다
 * - 경매의 입찰 수 = 저장된 입찰 기록 수 = 성공한 요청 수
 */
class AuctionBidConcurrencyTest extends MySqlIntegrationTest {

    private static final long INITIAL_BALANCE = 10_000_000L;
    private static final long BIDDER_ID_BASE = 100_000L;

    @Autowired private BidFacade bidFacade;
    @Autowired private BuyNowService buyNowService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private BidRepository bidRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PointWalletRepository pointWalletRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;

    private User seller;
    private Category category;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(User.create("seller@test.com", "pw", "seller", "01099990000"));
        category = categoryRepository.save(new Category(null, "전자기기", 1));
    }

    @AfterEach
    void tearDown() {
        bidRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        pointHistoryRepository.deleteAllInBatch();
        pointWalletRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("100명이 동시에 서로 다른 금액으로 입찰해도 최고가·홀딩·입찰 수가 정확히 맞는다")
    void hundredConcurrentBids() throws Exception {
        Long auctionId = createOngoingAuction(null);
        int bidderCount = 100;
        List<Long> bidders = createBidders(bidderCount);

        AtomicInteger success = new AtomicInteger();
        AtomicLong maxSuccessAmount = new AtomicLong();
        runConcurrently(bidderCount, i -> {
            long amount = 10_000L + (i + 1) * 1_000L;
            try {
                bidFacade.placeBid(bidders.get(i), auctionId, amount);
                success.incrementAndGet();
                maxSuccessAmount.accumulateAndGet(amount, Math::max);
            } catch (BusinessException ignored) {
                // 더 높은 입찰에 밀린 경우(BID_TOO_LOW)나 재시도 초과(BID_CONFLICT)는 정상적인 거절
            }
        });

        Auction auction = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(success.get()).isPositive();
        assertThat(auction.getCurrentPrice()).isEqualTo(maxSuccessAmount.get());
        assertThat(auction.getBidCount()).isEqualTo(success.get());
        assertThat(bidRepository.countByAuctionId(auctionId)).isEqualTo(success.get());
        assertHoldOnlyOnWinner(auction, bidders);
        assertTotalPointsPreserved(bidders, 0L);
    }

    @Test
    @DisplayName("두 사람이 두 경매에서 서로를 동시에 역전해도(엇갈린 입찰) 데드락 없이 모두 처리된다")
    void crossedBidsNoDeadlock() throws Exception {
        Long auctionX = createOngoingAuction(null);
        Long auctionY = createOngoingAuction(null);
        List<Long> users = createBidders(2);
        Long a = users.get(0);
        Long b = users.get(1);

        // 시작 상태: X는 A가, Y는 B가 최고 입찰자
        bidFacade.placeBid(a, auctionX, 10_000L);
        bidFacade.placeBid(b, auctionY, 10_000L);

        // 라운드마다 X에서는 B가 A를, Y에서는 A가 B를 동시에 역전한다.
        // 각 입찰은 두 사람의 지갑을 모두 잠그는데, 순서를 정하지 않으면 서로 상대 지갑을 기다리는 데드락이 난다.
        AtomicInteger failures = new AtomicInteger();
        for (int round = 1; round <= 20; round++) {
            long amount = 10_000L + round * 1_000L;
            Long xBidder = round % 2 == 1 ? b : a;
            Long yBidder = round % 2 == 1 ? a : b;
            runConcurrently(2, i -> {
                try {
                    if (i == 0) {
                        bidFacade.placeBid(xBidder, auctionX, amount);
                    } else {
                        bidFacade.placeBid(yBidder, auctionY, amount);
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        assertThat(failures.get()).isZero();
        Auction x = auctionRepository.findById(auctionX).orElseThrow();
        Auction y = auctionRepository.findById(auctionY).orElseThrow();
        assertThat(x.getBidCount()).isEqualTo(21);
        assertThat(y.getBidCount()).isEqualTo(21);
        assertTotalPointsPreserved(users, 0L);
    }

    @Test
    @DisplayName("입찰이 몰리는 중에 즉시구매가 들어오면 즉시구매는 한 번만 성공하고, 이후 입찰은 모두 거절된다")
    void buyNowDuringBids() throws Exception {
        long buyNowPrice = 500_000L;
        Long auctionId = createOngoingAuction(buyNowPrice);
        int bidderCount = 30;
        List<Long> users = createBidders(bidderCount + 1);
        Long buyer = users.get(bidderCount);

        AtomicInteger buyNowSuccess = new AtomicInteger();
        runConcurrently(bidderCount + 1, i -> {
            try {
                if (i == bidderCount) {
                    buyNowService.buyNow(buyer, auctionId);
                    buyNowSuccess.incrementAndGet();
                } else {
                    bidFacade.placeBid(users.get(i), auctionId, 10_000L + (i + 1) * 1_000L);
                }
            } catch (BusinessException ignored) {
            }
        });

        Auction auction = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(buyNowSuccess.get()).isEqualTo(1);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(auction.getHighestBidderId()).isEqualTo(buyer);
        assertThat(orderRepository.existsByAuctionId(auctionId)).isTrue();

        // 즉시구매 후에는 누구에게도 홀딩이 남아 있으면 안 된다
        for (Long userId : users) {
            assertThat(wallet(userId).getHeldBalance()).isZero();
        }
        assertThat(wallet(buyer).getBalance()).isEqualTo(INITIAL_BALANCE - buyNowPrice);
        assertTotalPointsPreserved(users, buyNowPrice);
    }

    @Test
    @DisplayName("낙찰로 마감되면 스케줄러가 낙찰자 포인트 차감, 상품 판매 완료, 주문 생성을 한 번에 처리한다")
    void settleOnClose() throws Exception {
        Long auctionId = createOngoingAuction(null);
        List<Long> users = createBidders(2);
        bidFacade.placeBid(users.get(0), auctionId, 10_000L);
        bidFacade.placeBid(users.get(1), auctionId, 20_000L);

        // 종료 시각을 지난 것으로 바꿔서 스케줄러(1초 주기)가 마감하게 한다
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();
        ReflectionTestUtils.setField(auction, "endAt", LocalDateTime.now().minusSeconds(1));
        auctionRepository.save(auction);

        Auction closed = waitUntilClosed(auctionId);

        assertThat(closed.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(closed.getHighestBidderId()).isEqualTo(users.get(1));
        assertThat(orderRepository.existsByAuctionId(auctionId)).isTrue();
        assertThat(productRepository.findById(closed.getProduct().getId()).orElseThrow().getStatus())
                .isEqualTo(Status.SOLD);
        // 낙찰자: 홀딩 20,000이 차감됨. 밀린 입찰자: 홀딩이 모두 풀림
        assertThat(wallet(users.get(1)).getHeldBalance()).isZero();
        assertThat(wallet(users.get(1)).getBalance()).isEqualTo(INITIAL_BALANCE - 20_000L);
        assertThat(wallet(users.get(0)).getHeldBalance()).isZero();
        assertThat(wallet(users.get(0)).getBalance()).isEqualTo(INITIAL_BALANCE);
    }

    private Auction waitUntilClosed(Long auctionId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Auction auction = auctionRepository.findById(auctionId).orElseThrow();
            if (auction.getStatus() != AuctionStatus.ONGOING) {
                return auction;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("10초 안에 마감되지 않았습니다");
    }

    // ===== helpers =====

    private Long createOngoingAuction(Long buyNowPrice) {
        Product product = productRepository.save(new Product(seller, category, "아이패드", "설명"));
        LocalDateTime now = LocalDateTime.now();
        Auction auction = new Auction(product, null, 10_000L, 1_000L, buyNowPrice, now.minusMinutes(1), now.plusHours(1));
        auction.start();
        return auctionRepository.save(auction).getId();
    }

    private List<Long> createBidders(int count) {
        long base = BIDDER_ID_BASE + pointWalletRepository.count() * 1_000L;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long userId = base + i;
            PointWallet wallet = PointWallet.create(userId);
            wallet.charge(INITIAL_BALANCE);
            pointWalletRepository.save(wallet);
            ids.add(userId);
        }
        return ids;
    }

    private PointWallet wallet(Long userId) {
        return pointWalletRepository.findByUserId(userId).orElseThrow();
    }

    private void assertHoldOnlyOnWinner(Auction auction, List<Long> bidders) {
        for (Long userId : bidders) {
            PointWallet wallet = wallet(userId);
            if (userId.equals(auction.getHighestBidderId())) {
                assertThat(wallet.getHeldBalance()).isEqualTo(auction.getCurrentPrice());
            } else {
                assertThat(wallet.getHeldBalance()).isZero();
                assertThat(wallet.getBalance()).isEqualTo(INITIAL_BALANCE);
            }
        }
    }

    private void assertTotalPointsPreserved(List<Long> users, long spent) {
        long total = users.stream()
                .map(this::wallet)
                .mapToLong(w -> w.getBalance() + w.getHeldBalance())
                .sum();
        assertThat(total).isEqualTo(INITIAL_BALANCE * users.size() - spent);
    }

    private void runConcurrently(int threadCount, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            futures.add(executor.submit((Callable<Void>) () -> {
                start.await();
                task.run(index);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();
    }

    @FunctionalInterface
    private interface IndexedTask {
        void run(int index) throws Exception;
    }
}
