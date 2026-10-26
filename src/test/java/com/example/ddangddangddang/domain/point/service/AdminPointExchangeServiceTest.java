package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.AdminPointExchangeResponse;
import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointExchangeRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminPointExchangeServiceTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long EXCHANGE_ID = 100L;

    @InjectMocks
    private AdminPointExchangeService adminPointExchangeService;

    @Mock
    private PointExchangeRepository pointExchangeRepository;

    @Mock
    private PointExchangeLedger pointExchangeLedger;

    @Mock
    private UserRepository userRepository;

    private PointExchange pending() {
        PointExchange exchange = PointExchange.create(1L, 10_000L, "point-exchange-test", "088", "110-123-456789", "홍길동");
        ReflectionTestUtils.setField(exchange, "id", EXCHANGE_ID);
        given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));
        return exchange;
    }

    @Test
    @DisplayName("송금 시작: 관리자에게는 계좌번호를 가리지 않고 보여준다")
    void start() {
        pending();

        AdminPointExchangeResponse response = adminPointExchangeService.startPayout(ADMIN_ID, EXCHANGE_ID);

        assertThat(response.status()).isEqualTo(PointExchangeType.PROCESSING);
        assertThat(response.accountNumber()).isEqualTo("110123456789");
        assertThat(response.handledBy()).isEqualTo(ADMIN_ID);
        verify(pointExchangeLedger, never()).refund(any());
    }

    @Test
    @DisplayName("목록: 신청한 회원의 닉네임·이메일을 함께 보여주고, 회원을 못 찾으면 비워 둔다")
    void listWithUser() {
        PointExchange mine = PointExchange.create(1L, 10_000L, "p-1", "088", "110-123-456789", "홍길동");
        PointExchange unknown = PointExchange.create(2L, 5_000L, "p-2", "004", "123-456-789012", "김철수");
        ReflectionTestUtils.setField(mine, "id", 1L);
        ReflectionTestUtils.setField(unknown, "id", 2L);
        User user = User.create("seller@ddang.com", "encoded", "판매왕");
        ReflectionTestUtils.setField(user, "id", 1L);
        given(pointExchangeRepository.findForAdmin(any(), any(), any())).willReturn(List.of(mine, unknown));
        given(userRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(user));

        var page = adminPointExchangeService.getExchanges(PointExchangeType.PENDING, null, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).nickname()).isEqualTo("판매왕");
        assertThat(page.content().get(0).email()).isEqualTo("seller@ddang.com");
        assertThat(page.content().get(1).nickname()).isNull();
    }

    @Test
    @DisplayName("지급 완료: 포인트는 이미 차감돼 있어 지갑을 건드리지 않는다")
    void complete() {
        PointExchange exchange = pending();
        exchange.startManualPayout(ADMIN_ID, LocalDateTime.now());

        AdminPointExchangeResponse response = adminPointExchangeService.completePayout(ADMIN_ID, EXCHANGE_ID);

        assertThat(response.status()).isEqualTo(PointExchangeType.COMPLETE);
        verify(pointExchangeLedger, never()).refund(any());
        verify(pointExchangeLedger, never()).deduct(any());
    }

    @Test
    @DisplayName("반려: FAILED로 바꾸고 포인트를 환불한다")
    void reject() {
        PointExchange exchange = pending();

        AdminPointExchangeResponse response = adminPointExchangeService.reject(ADMIN_ID, EXCHANGE_ID, "예금주 불일치");

        assertThat(response.status()).isEqualTo(PointExchangeType.FAILED);
        assertThat(response.failReason()).isEqualTo("예금주 불일치");
        verify(pointExchangeLedger).refund(exchange);
    }

    @Test
    @DisplayName("반려: 이미 지급 완료된 건이면 환불하지 않는다")
    void rejectCompleted() {
        PointExchange exchange = pending();
        exchange.startManualPayout(ADMIN_ID, LocalDateTime.now());
        exchange.completeManualPayout(ADMIN_ID);

        assertThatThrownBy(() -> adminPointExchangeService.reject(ADMIN_ID, EXCHANGE_ID, "실수"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
        verify(pointExchangeLedger, never()).refund(any());
    }

    @Test
    @DisplayName("없는 신청이면 EXCHANGE_NOT_FOUND")
    void notFound() {
        given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminPointExchangeService.startPayout(ADMIN_ID, EXCHANGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.EXCHANGE_NOT_FOUND);
    }
}
