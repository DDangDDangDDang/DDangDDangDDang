package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.AuctionFixture;
import com.example.ddangddangddang.domain.auction.dto.response.BidHistoryResponse;
import com.example.ddangddangddang.domain.auction.dto.response.MyBidResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.entity.Bid;
import com.example.ddangddangddang.domain.auction.entity.BidStatus;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.repository.BidRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.util.NicknameMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.ddangddangddang.domain.auction.AuctionFixture.AUCTION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BidQueryServiceTest {

    private static final Long ME = 2L;
    private static final Long OTHER = 3L;

    @InjectMocks
    private BidQueryService bidQueryService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private UserRepository userRepository;

    private Bid bid(long id, Long bidderId, long amount) {
        Bid bid = Bid.create(AUCTION_ID, bidderId, amount);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }

    private User user(Long id, String nickname) {
        User user = User.create(nickname + "@ddang.com", "pw", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("입찰 기록: 회원 번호 대신 가린 닉네임을 보여주고, 로그인한 사람의 입찰을 표시한다")
    void history() {
        given(auctionRepository.existsById(AUCTION_ID)).willReturn(true);
        given(bidRepository.findByCursor(eq(AUCTION_ID), any(), any()))
                .willReturn(List.of(bid(2, ME, 21_000L), bid(1, OTHER, 20_000L)));
        given(userRepository.findAllById(List.of(ME, OTHER)))
                .willReturn(List.of(user(ME, "땅땅이"), user(OTHER, "경매왕김씨")));

        CursorResponse<BidHistoryResponse> page = bidQueryService.getBids(AUCTION_ID, ME, null, 20);

        assertThat(page.content()).extracting(BidHistoryResponse::bidderNickname).containsExactly("땅*이", "경***씨");
        assertThat(page.content()).extracting(BidHistoryResponse::mine).containsExactly(true, false);
        assertThat(page.content().get(0).status()).isEqualTo(BidStatus.ACTIVE);
    }

    @Test
    @DisplayName("입찰 기록: 로그인하지 않으면 본인 입찰 표시가 없고, 회원을 못 찾으면 알 수 없음")
    void historyAsGuest() {
        given(auctionRepository.existsById(AUCTION_ID)).willReturn(true);
        given(bidRepository.findByCursor(eq(AUCTION_ID), any(), any())).willReturn(List.of(bid(1, ME, 20_000L)));
        given(userRepository.findAllById(List.of(ME))).willReturn(List.of());

        CursorResponse<BidHistoryResponse> page = bidQueryService.getBids(AUCTION_ID, null, null, 20);

        assertThat(page.content().get(0).mine()).isFalse();
        assertThat(page.content().get(0).bidderNickname()).isEqualTo(NicknameMasker.UNKNOWN);
    }

    @Test
    @DisplayName("입찰 기록: 없는 경매면 NOT_FOUND")
    void historyNotFound() {
        given(auctionRepository.existsById(AUCTION_ID)).willReturn(false);

        assertThatThrownBy(() -> bidQueryService.getBids(AUCTION_ID, null, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("내 입찰 상태: 진행 중이면 최고 입찰 중 / 밀림, 입찰 안 했으면 NONE")
    void myBidOngoing() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        AuctionFixture.bidBy(auction, ME, 21_000L);
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
        given(bidRepository.findMaxAmount(AUCTION_ID, ME)).willReturn(21_000L);
        given(bidRepository.findMaxAmount(AUCTION_ID, OTHER)).willReturn(20_000L);
        given(bidRepository.findMaxAmount(AUCTION_ID, 9L)).willReturn(null);

        assertThat(bidQueryService.getMyBid(AUCTION_ID, ME).state()).isEqualTo(MyBidResponse.State.LEADING);
        assertThat(bidQueryService.getMyBid(AUCTION_ID, OTHER).state()).isEqualTo(MyBidResponse.State.OUTBID);
        assertThat(bidQueryService.getMyBid(AUCTION_ID, OTHER).myHighestAmount()).isEqualTo(20_000L);
        assertThat(bidQueryService.getMyBid(AUCTION_ID, 9L).state()).isEqualTo(MyBidResponse.State.NONE);
    }

    @Test
    @DisplayName("내 입찰 상태: 끝난 경매면 낙찰 / 낙찰 실패. 입찰 없이 즉시구매한 사람도 낙찰")
    void myBidEnded() {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        AuctionFixture.bidBy(auction, ME, 21_000L);
        ReflectionTestUtils.setField(auction, "status", AuctionStatus.ENDED);
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
        given(bidRepository.findMaxAmount(AUCTION_ID, ME)).willReturn(null); // 즉시구매: 입찰 기록 없이 최고 입찰자가 됨
        given(bidRepository.findMaxAmount(AUCTION_ID, OTHER)).willReturn(20_000L);

        assertThat(bidQueryService.getMyBid(AUCTION_ID, ME).state()).isEqualTo(MyBidResponse.State.WON);
        assertThat(bidQueryService.getMyBid(AUCTION_ID, OTHER).state()).isEqualTo(MyBidResponse.State.LOST);
    }
}
