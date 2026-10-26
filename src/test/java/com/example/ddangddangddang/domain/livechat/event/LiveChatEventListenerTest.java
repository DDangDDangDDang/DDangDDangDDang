package com.example.ddangddangddang.domain.livechat.event;

import com.example.ddangddangddang.domain.auction.event.LiveAuctionEvent;
import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.dto.ChatMessageType;
import com.example.ddangddangddang.domain.livechat.service.LiveChatService;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LiveChatEventListenerTest {

    private static final Long LIVE_STREAM_ID = 1L;
    private static final Long AUCTION_ID = 10L;
    private static final Long USER_ID = 7L;

    @Mock
    private LiveChatService liveChatService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LiveChatEventListener listener;

    @Test
    @DisplayName("방송에 올라간 경매에 입찰하면 입찰자 닉네임·금액·직전 현재가를 담아 입찰 안내를 보낸다")
    void bid() {
        givenNickname("짱구");

        listener.handleLiveAuction(LiveAuctionEvent.bid(LIVE_STREAM_ID, AUCTION_ID, "나이키 운동화", 11_000L, LocalDateTime.now(), USER_ID, 10_000L));

        then(liveChatService).should().sendBidMessage(LIVE_STREAM_ID, USER_ID, "짱구", AUCTION_ID, "나이키 운동화", 11_000L, 10_000L);
        then(liveChatService).should(never()).sendSystemMessage(any(), anyString());
    }

    @Test
    @DisplayName("낙찰되면 낙찰자 닉네임과 낙찰가로 낙찰 안내를 보낸다. 회원을 찾지 못해도 안내는 보낸다")
    void sold() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        listener.handleLiveAuction(new LiveAuctionEvent(LIVE_STREAM_ID, AUCTION_ID, LiveAuctionEvent.Type.SOLD, "나이키 운동화", 15_000L,
                LocalDateTime.now(), USER_ID, null));

        then(liveChatService).should().sendAuctionSoldMessage(LIVE_STREAM_ID, USER_ID, LiveChatEventListener.UNKNOWN_NICKNAME, AUCTION_ID,
                "나이키 운동화", 15_000L);
    }

    @Test
    @DisplayName("취소·유찰 같은 변경은 안내하지 않는다")
    void changed() {
        listener.handleLiveAuction(new LiveAuctionEvent(LIVE_STREAM_ID, AUCTION_ID, LiveAuctionEvent.Type.CHANGED, "나이키 운동화", 15_000L,
                LocalDateTime.now(), null, null));

        then(liveChatService).shouldHaveNoInteractions();
        then(userRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("입찰·낙찰 메시지의 내용은 채팅에 그대로 보여 줄 안내 문장이다")
    void messageContent() {
        ChatMessageResponse bid = ChatMessageResponse.bid(LIVE_STREAM_ID, USER_ID, "짱구", AUCTION_ID, "나이키 운동화", 11_000L, 10_000L);
        ChatMessageResponse sold = ChatMessageResponse.auctionSold(LIVE_STREAM_ID, USER_ID, "짱구", AUCTION_ID, "나이키 운동화", 15_000L);

        assertThat(bid.type()).isEqualTo(ChatMessageType.BID);
        assertThat(bid.content()).isEqualTo("짱구님이 ‘나이키 운동화’ 상품을 11,000P에 입찰했습니다.");
        assertThat(bid.previousAmount()).isEqualTo(10_000L);
        assertThat(sold.type()).isEqualTo(ChatMessageType.AUCTION_SOLD);
        assertThat(sold.content()).isEqualTo("‘나이키 운동화’ 상품이 짱구님에게 15,000P에 낙찰되었습니다.");
        assertThat(sold.previousAmount()).isNull();
    }

    private void givenNickname(String nickname) {
        User user = mock(User.class);
        given(user.getNickname()).willReturn(nickname);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }
}
