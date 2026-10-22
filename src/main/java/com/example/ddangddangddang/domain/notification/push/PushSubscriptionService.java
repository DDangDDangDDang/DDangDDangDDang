package com.example.ddangddangddang.domain.notification.push;

import com.example.ddangddangddang.domain.notification.exception.NotificationErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** 브라우저 알림(웹 푸시) 구독 저장·삭제 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final VapidKeys vapidKeys;
    private final WebPushProperties properties;
    private final TransactionTemplate transactionTemplate;

    public String publicKey() {
        return vapidKeys.publicKey();
    }

    /** 이 브라우저의 구독을 저장한다. 이미 있는 주소면 지금 회원·키로 바꾼다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void subscribe(Long userId, PushSubscriptionRequest request) {
        String endpoint = request.endpoint().trim();
        validate(endpoint, request.p256dh(), request.auth());
        try {
            transactionTemplate.executeWithoutResult(status -> repository.findByEndpointHash(PushSubscription.hash(endpoint))
                    .ifPresentOrElse(
                            existing -> existing.renew(userId, request.p256dh(), request.auth()),
                            () -> repository.saveAndFlush(PushSubscription.create(userId, endpoint, request.p256dh(), request.auth()))));
        } catch (DataIntegrityViolationException e) {
            // 같은 브라우저의 구독 요청이 동시에 두 번 온 경우. 한쪽이 저장했으니 다시 한 번 바꾼다.
            transactionTemplate.executeWithoutResult(status -> repository.findByEndpointHash(PushSubscription.hash(endpoint))
                    .ifPresent(existing -> existing.renew(userId, request.p256dh(), request.auth())));
        }
    }

    /** 이 브라우저의 구독을 지운다. 없거나 남의 구독이면 아무것도 하지 않는다. */
    public void unsubscribe(Long userId, String endpoint) {
        repository.deleteMine(userId, PushSubscription.hash(endpoint.trim()));
    }

    /**
     * 서버가 이 주소로 요청을 보내므로, https이고 알려진 푸시 서비스 주소만 받는다. (내부망 주소로 요청을 보내게 하는 공격 방지)
     * 키는 암호화에 실제로 쓸 수 있는지(곡선 위의 점, 16바이트)까지 확인한다.
     */
    void validate(String endpoint, String p256dh, String auth) {
        if (!allowedEndpoint(endpoint)) {
            throw new BusinessException(NotificationErrorCode.INVALID_PUSH_SUBSCRIPTION);
        }
        try {
            WebPushCrypto.decodePublicKey(WebPushCrypto.fromBase64Url(p256dh));
            if (WebPushCrypto.fromBase64Url(auth).length != 16) {
                throw new BusinessException(NotificationErrorCode.INVALID_PUSH_SUBSCRIPTION);
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(NotificationErrorCode.INVALID_PUSH_SUBSCRIPTION);
        }
    }

    private boolean allowedEndpoint(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            // ".notify.windows.com"처럼 점으로 시작하면 그 하위 도메인, 아니면 그 호스트만
            return properties.allowedHosts().stream().anyMatch(allowed -> allowed.startsWith(".") ? host.endsWith(allowed) : host.equals(allowed));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
