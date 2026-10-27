package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.dto.response.BidHistoryResponse;
import com.example.ddangddangddang.domain.auction.dto.response.MyBidResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.entity.Bid;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.repository.BidRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.util.NicknameMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 입찰 조회: 경매의 입찰 기록(누구나), 이 경매에서 내 입찰 상태(로그인).
 * 입찰 처리(BidService)와 나눈 이유: 조회는 회원 정보가 필요하고, 입찰 처리 트랜잭션은 짧게 유지하고 싶어서다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;

    /** 입찰 기록 (최신순). viewerId는 로그인했으면 회원 번호, 아니면 null (본인 입찰 표시용) */
    public CursorResponse<BidHistoryResponse> getBids(Long auctionId, Long viewerId, Long cursor, int size) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new BusinessException(AuctionErrorCode.NOT_FOUND);
        }
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<Bid> fetched = bidRepository.findByCursor(auctionId, cursor, PageRequest.of(0, pageSize + 1));

        // 입찰자 닉네임을 한 번에 불러온다 (입찰마다 조회하지 않게)
        Map<Long, String> nicknames = userRepository.findAllById(
                        fetched.stream().map(Bid::getBidderId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return CursorResponse.of(fetched, pageSize,
                bid -> BidHistoryResponse.of(bid, NicknameMasker.mask(nicknames.get(bid.getBidderId())), viewerId),
                Bid::getId);
    }

    /**
     * 이 경매에서 내 입찰 상태. 경매의 최고 입찰자와 내 입찰 기록으로 판단한다.
     * (입찰 기록의 상태 컬럼이 아니라 경매를 기준으로 삼아, 상태 도입 전에 끝난 경매도 맞게 보여준다)
     */
    public MyBidResponse getMyBid(Long auctionId, Long userId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.NOT_FOUND));
        Long myHighest = bidRepository.findMaxAmount(auctionId, userId);
        boolean highest = userId.equals(auction.getHighestBidderId());

        MyBidResponse.State state;
        if (myHighest == null && !highest) {
            state = MyBidResponse.State.NONE;
        } else if (auction.getStatus() == AuctionStatus.ONGOING) {
            state = highest ? MyBidResponse.State.LEADING : MyBidResponse.State.OUTBID;
        } else if (auction.getStatus() == AuctionStatus.ENDED) {
            state = highest ? MyBidResponse.State.WON : MyBidResponse.State.LOST;
        } else {
            state = MyBidResponse.State.LOST;
        }
        return new MyBidResponse(auctionId, state, myHighest);
    }
}
