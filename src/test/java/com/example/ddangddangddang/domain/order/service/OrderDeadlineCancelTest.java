package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.auction.dto.request.AuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.service.AuctionService;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 DB 락이 동작해야 하므로 @Transactional 없이 실행하고, 끝나면 직접 데이터를 지운다.
 */
class OrderDeadlineCancelTest extends MySqlIntegrationTest {

    private static final long PRICE = 500_000L;

    @Autowired private OrderDeadlineScheduler scheduler;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PointWalletRepository pointWalletRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;
    @Autowired private AuctionService auctionService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private User seller;
    private User buyer;
    private Product product;
    private Long auctionId;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(User.create("deadline-seller@test.com", "pw", "deadlineSeller", "01011112222"));
        buyer = userRepository.save(User.create("deadline-buyer@test.com", "pw", "deadlineBuyer", "01033334444"));
        pointWalletRepository.save(PointWallet.create(buyer.getId()));
        pointWalletRepository.save(PointWallet.create(seller.getId()));
        Category category = categoryRepository.save(new Category(null, "전자기기", 1));
        product = productRepository.save(new Product(seller, category, "아이패드", "설명"));
        auctionId = soldAuction();
    }

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAllInBatch();
        pointWalletRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("배송지 입력 기한이 지나면 낙찰취소되고 10%는 판매자에게, 90%는 구매자에게 간다")
    void addressOverdue_paysPenaltyToSellerAndRefundsRest() {
        Long orderId = addressOverdueOrder(auctionId, buyer.getId());

        scheduler.cancelOverdueOrders();

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.BUYER_NO_ADDRESS);
        assertThat(balanceOf(buyer.getId())).isEqualTo(450_000L);
        assertThat(balanceOf(seller.getId())).isEqualTo(50_000L);
        assertThat(countOf(PointHistoryType.WIN_CANCEL)).isEqualTo(1);
        assertThat(countOf(PointHistoryType.WIN_CANCEL_PENALTY)).isEqualTo(1);
        assertThat(countOf(PointHistoryType.ORDER_PENALTY)).isEqualTo(1);
    }

    @Test
    @DisplayName("위약금 도입 전 주문은 낙찰취소돼도 전액 환불한다")
    void addressOverdue_legacyOrder_refundsFullPrice() {
        Long orderId = addressOverdueOrder(auctionId, buyer.getId());
        Order legacy = orderRepository.findById(orderId).orElseThrow();
        ReflectionTestUtils.setField(legacy, "penaltyRateBps", 0);
        orderRepository.save(legacy);

        scheduler.cancelOverdueOrders();

        assertThat(balanceOf(buyer.getId())).isEqualTo(PRICE);
        assertThat(balanceOf(seller.getId())).isZero();
    }

    @Test
    @DisplayName("판매자 미발송 취소는 위약금 없이 전액 환불한다")
    void shipOverdue_noPenalty() {
        shipOverdueOrder();

        scheduler.cancelOverdueOrders();

        assertThat(balanceOf(seller.getId())).isZero();
        assertThat(countOf(PointHistoryType.ORDER_REFUND)).isEqualTo(1);
        assertThat(countOf(PointHistoryType.WIN_CANCEL)).isZero();
    }

    @Test
    @DisplayName("발송 기한이 지나면 제품 미발송으로 취소된다")
    void shipOverdue_cancelsWithSellerNotShipped() {
        Long orderId = shipOverdueOrder();

        scheduler.cancelOverdueOrders();

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.SELLER_NOT_SHIPPED);
        assertThat(balanceOf(buyer.getId())).isEqualTo(PRICE);
    }

    @Test
    @DisplayName("취소되면 상품이 판매 중으로 돌아와 다시 경매에 올릴 수 있다")
    void cancel_restoresProductToActive_andCanReopenAuction() {
        addressOverdueOrder(auctionId, buyer.getId());

        scheduler.cancelOverdueOrders();

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(Status.ACTIVE);
        LocalDateTime startAt = LocalDateTime.now().plusHours(1);
        assertThat(auctionService.auctionCreate(seller.getId(), new AuctionCreateRequest(
                product.getId(), 10_000L, 1_000L, null, startAt, startAt.plusHours(1)))).isNotNull();
    }

    @Test
    @DisplayName("판매자가 삭제한 상품은 주문이 취소돼도 되살아나지 않는다")
    void cancel_keepsDeletedProductDeleted() {
        Product deleted = productRepository.findById(product.getId()).orElseThrow();
        deleted.delete();
        productRepository.save(deleted);
        addressOverdueOrder(auctionId, buyer.getId());

        scheduler.cancelOverdueOrders();

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(Status.DELETED);
    }

    @Test
    @DisplayName("같은 주문을 동시에 취소해도 환불은 한 번만 된다")
    void cancelOverdue_twiceConcurrently_refundsOnce() throws Exception {
        Long orderId = addressOverdueOrder(auctionId, buyer.getId());

        runConcurrently(
                () -> orderService.cancelOverdue(orderId, OrderCancelReason.BUYER_NO_ADDRESS),
                () -> orderService.cancelOverdue(orderId, OrderCancelReason.BUYER_NO_ADDRESS),
                () -> orderService.cancelOverdue(orderId, OrderCancelReason.BUYER_NO_ADDRESS));

        assertThat(countOf(PointHistoryType.WIN_CANCEL)).isEqualTo(1);
        assertThat(balanceOf(buyer.getId())).isEqualTo(450_000L);
        assertThat(balanceOf(seller.getId())).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("기한 순간에 발송과 취소가 겹쳐도 결과는 한쪽뿐이다")
    void shipAndCancelConcurrently_endsInOneState() throws Exception {
        Order order = Order.create(auctionId, buyer.getId(), seller.getId(), "아이패드", null, PRICE);
        order.registerDeliveryAddress("홍길동", "01012345678", "서울시", null);
        ReflectionTestUtils.setField(order, "shipDeadlineAt", LocalDateTime.now().plusNanos(100_000_000));
        Long orderId = orderRepository.save(order).getId();

        runConcurrently(
                () -> {
                    try {
                        orderService.ship(seller.getId(), orderId, Courier.CJ, "1234567890");
                    } catch (BusinessException ignored) {
                        // 기한을 넘겼거나 이미 취소된 경우
                    }
                },
                () -> {
                    Thread.sleep(100);
                    return orderService.cancelOverdue(orderId, OrderCancelReason.SELLER_NOT_SHIPPED);
                });

        OrderStatus status = orderRepository.findById(orderId).orElseThrow().getStatus();
        assertThat(status).isIn(OrderStatus.ESCROW, OrderStatus.SHIPPING, OrderStatus.CANCELED);
        assertThat(refundCount()).isEqualTo(status == OrderStatus.CANCELED ? 1 : 0);
    }

    @Test
    @DisplayName("배송지를 입력한 주문은 배송지 기한이 지나 있어도 낙찰취소되지 않는다")
    void addressEnteredBeforeDeadline_notCancelledByAddressQuery() {
        Order order = Order.create(auctionId, buyer.getId(), seller.getId(), "아이패드", null, PRICE);
        order.registerDeliveryAddress("홍길동", "01012345678", "서울시", null);
        ReflectionTestUtils.setField(order, "addressDeadlineAt", LocalDateTime.now().minusMinutes(1));
        Long orderId = orderRepository.save(order).getId();

        scheduler.cancelOverdueOrders();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.ESCROW);
    }

    @Test
    @DisplayName("기한이 NULL인 기존 주문은 자동 취소되지 않는다")
    void nullDeadline_notCancelled() {
        Order order = Order.create(auctionId, buyer.getId(), seller.getId(), "아이패드", null, PRICE);
        ReflectionTestUtils.setField(order, "addressDeadlineAt", null);
        Long orderId = orderRepository.save(order).getId();

        scheduler.cancelOverdueOrders();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.ESCROW);
    }

    @Test
    @DisplayName("환불에 실패한 주문은 그대로 두고 다음 주문을 계속 취소한다")
    void missingBuyerWallet_failsOneOrder_othersStillCancelled() {
        Long failing = addressOverdueOrder(auctionId, 999_999L);
        Long succeeding = addressOverdueOrder(auctionId + 1_000_000, buyer.getId());

        scheduler.cancelOverdueOrders();

        assertThat(orderRepository.findById(failing).orElseThrow().getStatus()).isEqualTo(OrderStatus.ESCROW);
        assertThat(orderRepository.findById(succeeding).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("경매 이력에 주문 취소 사유가 함께 나온다")
    void productAuctionHistory_includesOrderCancelReason() {
        addressOverdueOrder(auctionId, buyer.getId());
        scheduler.cancelOverdueOrders();

        assertThat(auctionService.productAuctionHistory(seller.getId(), product.getId()))
                .singleElement()
                .satisfies(history -> assertThat(history.orderCancelReason()).isEqualTo(OrderCancelReason.BUYER_NO_ADDRESS));
    }

    @Test
    @DisplayName("취소되지 않은 주문의 경매 이력에는 취소 사유가 없다")
    void productAuctionHistory_noCancelledOrder_reasonNull() {
        orderRepository.save(Order.create(auctionId, buyer.getId(), seller.getId(), "아이패드", null, PRICE));

        assertThat(auctionService.productAuctionHistory(seller.getId(), product.getId()))
                .singleElement()
                .satisfies(history -> assertThat(history.orderCancelReason()).isNull());
    }

    @Test
    @DisplayName("한 번에 처리하는 수만큼 실패한 주문이 쌓여도 그 뒤의 주문은 취소된다")
    void failingOrdersFillBatch_laterOrdersStillCancelled() {
        ReflectionTestUtils.setField(scheduler, "batchSize", 2);
        Long failing1 = addressOverdueOrder(auctionId, 999_998L);
        Long failing2 = addressOverdueOrder(auctionId + 1_000_000, 999_999L);
        Long succeeding = addressOverdueOrder(auctionId + 2_000_000, buyer.getId());
        try {
            scheduler.cancelOverdueOrders();
        } finally {
            ReflectionTestUtils.setField(scheduler, "batchSize", 100);
        }

        assertThat(orderRepository.findById(failing1).orElseThrow().getStatus()).isEqualTo(OrderStatus.ESCROW);
        assertThat(orderRepository.findById(failing2).orElseThrow().getStatus()).isEqualTo(OrderStatus.ESCROW);
        assertThat(orderRepository.findById(succeeding).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    private Long soldAuction() {
        LocalDateTime startAt = LocalDateTime.now().plusHours(1);
        Long id = auctionService.auctionCreate(seller.getId(), new AuctionCreateRequest(
                product.getId(), 10_000L, 1_000L, null, startAt, startAt.plusHours(1))).id();
        Auction auction = auctionRepository.findById(id).orElseThrow();
        ReflectionTestUtils.setField(auction, "status", AuctionStatus.ENDED);
        auctionRepository.save(auction);
        Product sold = productRepository.findById(product.getId()).orElseThrow();
        sold.sold();
        productRepository.save(sold);
        return id;
    }

    private Long addressOverdueOrder(Long auction, Long buyerId) {
        Order order = Order.create(auction, buyerId, seller.getId(), "아이패드", null, PRICE);
        ReflectionTestUtils.setField(order, "addressDeadlineAt", LocalDateTime.now().minusMinutes(1));
        return orderRepository.save(order).getId();
    }

    private Long shipOverdueOrder() {
        Order order = Order.create(auctionId, buyer.getId(), seller.getId(), "아이패드", null, PRICE);
        order.registerDeliveryAddress("홍길동", "01012345678", "서울시", null);
        ReflectionTestUtils.setField(order, "shipDeadlineAt", LocalDateTime.now().minusMinutes(1));
        return orderRepository.save(order).getId();
    }

    private long balanceOf(Long userId) {
        return pointWalletRepository.findByUserId(userId).orElseThrow().getBalance();
    }

    private long refundCount() {
        return countOf(PointHistoryType.ORDER_REFUND);
    }

    private long countOf(PointHistoryType type) {
        return pointHistoryRepository.findAll().stream().filter(history -> history.getType() == type).count();
    }

    private void runConcurrently(Runnable first, java.util.concurrent.Callable<?> second) throws Exception {
        runAll(List.<java.util.concurrent.Callable<Object>>of(asCallable(first), second::call));
    }

    private void runConcurrently(Runnable first, Runnable second, Runnable third) throws Exception {
        runAll(List.of(asCallable(first), asCallable(second), asCallable(third)));
    }

    private java.util.concurrent.Callable<Object> asCallable(Runnable task) {
        return () -> {
            task.run();
            return null;
        };
    }

    private void runAll(List<java.util.concurrent.Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (java.util.concurrent.Callable<Object> task : tasks) {
            futures.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
    }
}
