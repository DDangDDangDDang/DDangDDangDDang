package com.example.ddangddangddang.domain.shop.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationLotUsageRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointHistoryRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationWalletRepository;
import com.example.ddangddangddang.domain.donation.service.DonationPointService;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.shop.entity.AuctionBoost;
import com.example.ddangddangddang.domain.shop.exception.ShopErrorCode;
import com.example.ddangddangddang.domain.shop.repository.AuctionBoostRepository;
import com.example.ddangddangddang.domain.shop.repository.ShopLockRepository;
import com.example.ddangddangddang.domain.shop.repository.ShopPurchaseRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
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
 * 여러 판매자가 동시에 경매를 위로 올려도 동시에 올라가 있는 경매는 10개를 넘지 않는다.
 * 자리를 세고 저장하는 사이에 끼어들지 않도록 상점 잠금 행을 잡는지 실제 MySQL에서 확인한다. (DonationConcurrencyTest와 같은 방식)
 */
class AuctionBoostConcurrencyTest extends MySqlIntegrationTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private DonationPointService donationPointService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private AuctionBoostRepository boostRepository;

    @Autowired
    private ShopPurchaseRepository purchaseRepository;

    @Autowired
    private ShopLockRepository lockRepository;

    @Autowired
    private DonationWalletRepository walletRepository;

    @Autowired
    private DonationPointLotRepository lotRepository;

    @Autowired
    private DonationLotUsageRepository usageRepository;

    @Autowired
    private DonationPointHistoryRepository historyRepository;

    @AfterEach
    void tearDown() {
        boostRepository.deleteAllInBatch();
        purchaseRepository.deleteAllInBatch();
        lockRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        historyRepository.deleteAllInBatch();
        lotRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        // 하위 카테고리부터 지운다. (parent_id 외래키)
        categoryRepository.deleteAll(categoryRepository.findAll().stream().filter(c -> c.getParent() != null).toList());
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("판매자 13명이 동시에 경매를 올리면 10명만 올라가고, 나머지 3명은 SHOP_003이며 마일리지가 빠지지 않는다")
    void maxActive() throws Exception {
        int sellers = AuctionBoost.MAX_ACTIVE + 3;
        Category parent = categoryRepository.save(new Category(null, "동시성", 1));
        Category leaf = categoryRepository.save(new Category(parent, "카메라", 1));
        List<Long> sellerIds = new ArrayList<>();
        List<Long> auctionIds = new ArrayList<>();
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        for (int i = 0; i < sellers; i++) {
            User seller = userRepository.save(User.create("boost" + i + "@test.com", "encoded", "올리기" + i));
            Product product = productRepository.save(new Product(seller, leaf, "상품" + i, "테스트"));
            auctionIds.add(auctionRepository.save(new Auction(product, null, 10_000L, 1_000L, null, startAt, startAt.plusDays(1))).getId());
            sellerIds.add(seller.getId());
            donationPointService.earn(7_000_000L + i, seller.getId(), 0L, 100_000L);
        }

        AtomicInteger success = new AtomicInteger();
        List<Object> rejected = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(sellers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < sellers; i++) {
            Long sellerId = sellerIds.get(i);
            Long auctionId = auctionIds.get(i);
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    shopService.boostAuction(sellerId, auctionId);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    rejected.add(e.getErrorCode());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(success.get()).isEqualTo(AuctionBoost.MAX_ACTIVE);
        assertThat(rejected).hasSize(3).containsOnly(ShopErrorCode.BOOST_SLOTS_FULL);
        assertThat(boostRepository.findActive(LocalDateTime.now())).hasSize(AuctionBoost.MAX_ACTIVE);
        // 올린 10명은 1,000M이 빠지고, 못 올린 3명은 그대로
        long paid = sellerIds.stream().filter(id -> walletRepository.findByUserId(id).orElseThrow().getBalance() == 0).count();
        long kept = sellerIds.stream().filter(id -> walletRepository.findByUserId(id).orElseThrow().getBalance() == 1_000).count();
        assertThat(paid).isEqualTo(AuctionBoost.MAX_ACTIVE);
        assertThat(kept).isEqualTo(3);
    }
}
