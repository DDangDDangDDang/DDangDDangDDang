package com.example.ddangddangddang.global.loadtest;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 부하 테스트(k6)용 데이터를 서버 시작 시 만든다. loadtest 프로필에서만 등록되고, prod 프로필과 함께 켜면 등록되지 않는다.
 *
 * - 입찰자 회원 N명(loadtest-001@ddang.test …)과 판매자 1명을 만들고, 입찰자 포인트를 목표치까지 채운다. 이미 있으면 다시 쓴다.
 * - 시작할 때마다 진행 중(ONGOING) 경매를 새로 만든다. 첫 번째 경매는 모두가 몰려 입찰하는 경매로 쓴다.
 * - 판매자의 방송을 방송 중(LIVE)으로 하나 둔다. 채팅 부하 테스트에 쓴다. (LiveKit 송출 없이 상태만 LIVE)
 * - 회원별 Access Token을 만들어 JSON 파일로 남긴다. 로그인 API는 IP당 5분 30회로 막혀 있어 k6가 직접 로그인하지 않는다.
 *   토큰 유효 시간은 application-loadtest.yml의 jwt.access-token-validity를 따른다.
 *
 * 회원 비밀번호는 무작위라 화면 로그인은 할 수 없다. 테스트 데이터는 이메일이 loadtest- 로 시작하므로 나중에 골라 지울 수 있다.
 */
@Slf4j
@Component
@Profile("loadtest & !prod")
@RequiredArgsConstructor
public class LoadTestDataInitializer implements ApplicationRunner {

    static final String EMAIL_PREFIX = "loadtest-";
    static final String EMAIL_DOMAIN = "@ddang.test";

    private final UserRepository userRepository;
    private final PointWalletRepository pointWalletRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.loadtest.users:200}")
    private int userCount;

    @Value("${app.loadtest.points-per-user:100000000}")
    private long pointsPerUser;

    @Value("${app.loadtest.auctions:5}")
    private int auctionCount;

    @Value("${app.loadtest.auction-hours:6}")
    private int auctionHours;

    @Value("${app.loadtest.output:load-test/data/seed.json}")
    private String output;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        long started = System.currentTimeMillis();
        Map<String, Object> seed = transactionTemplate.execute(status -> seed());
        Path path = Path.of(output).toAbsolutePath();
        Files.createDirectories(path.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), seed);
        log.info("[부하 테스트] 데이터 준비 완료 ({}ms). 회원 {}명, 경매 {}개, 방송 id={} → {}",
                System.currentTimeMillis() - started, userCount, auctionCount, seed.get("liveStreamId"), path);
    }

    private Map<String, Object> seed() {
        // BCrypt는 느리므로 한 번만 만든다. 무작위 비밀번호라 이 계정으로는 로그인할 수 없다
        String encodedPassword = passwordEncoder.encode(UUID.randomUUID().toString());

        User seller = findOrCreate(EMAIL_PREFIX + "seller" + EMAIL_DOMAIN, "부하테스트판매자", encodedPassword);
        ensureWallet(seller.getId(), 0);

        List<Map<String, Object>> bidders = new ArrayList<>();
        for (int i = 1; i <= userCount; i++) {
            String suffix = "%03d".formatted(i);
            User user = findOrCreate(EMAIL_PREFIX + suffix + EMAIL_DOMAIN, "부하테스트" + suffix, encodedPassword);
            ensureWallet(user.getId(), pointsPerUser);
            bidders.add(Map.of("userId", user.getId(), "token", jwtProvider.createAccessToken(user)));
        }

        // 판매자는 끝나지 않은 방송을 하나만 가질 수 있다. 대기 중이면 시작하고, 없으면 새로 만든다
        LiveStream liveStream = liveStreamRepository.findAll().stream()
                .filter(stream -> seller.getId().equals(stream.getSellerId())
                        && stream.getStatus() != LiveStreamStatus.ENDED)
                .findFirst()
                .orElseGet(() -> liveStreamRepository.save(
                        LiveStream.create(seller.getId(), "부하 테스트 방송", "k6 채팅 부하 테스트용")));
        if (liveStream.getStatus() == LiveStreamStatus.SCHEDULED) {
            liveStream.start(seller.getId());
        }

        Category category = categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(c -> c.getParent() != null)
                .findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(null, "부하테스트", 9999)));

        LocalDateTime now = LocalDateTime.now();
        List<Long> auctionIds = new ArrayList<>();
        for (int i = 1; i <= auctionCount; i++) {
            Product product = productRepository.save(
                    new Product(seller, category, "부하테스트 상품 " + i, "k6 부하 테스트용 상품"));
            // 시작가 1,000 / 입찰 단위 100 / 즉시구매 없음
            Auction auction = new Auction(product, null, 1_000L, 100L, null, now, now.plusHours(auctionHours));
            auction.start();
            if (i == 1) {
                auction.showOn(liveStream);
            }
            auctionIds.add(auctionRepository.save(auction).getId());
        }

        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("createdAt", now.toString());
        seed.put("sellerId", seller.getId());
        seed.put("sellerToken", jwtProvider.createAccessToken(seller));
        seed.put("liveStreamId", liveStream.getId());
        seed.put("auctionIds", auctionIds);
        seed.put("users", bidders);
        return seed;
    }

    private User findOrCreate(String email, String nickname, String encodedPassword) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.create(email, encodedPassword, nickname)));
    }

    private void ensureWallet(Long userId, long targetBalance) {
        PointWallet wallet = pointWalletRepository.findByUserId(userId)
                .orElseGet(() -> pointWalletRepository.save(PointWallet.create(userId)));
        long shortage = targetBalance - wallet.getSpendableBalance();
        if (shortage > 0) {
            wallet.charge(shortage);
        }
    }
}
