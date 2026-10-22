package com.example.ddangddangddang.domain.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * 이 서버의 VAPID 키. 브라우저는 구독할 때 공개키(applicationServerKey)를 받고, 푸시는 개인키로 서명해 보낸다.
 * 설정에 키가 없으면 서버를 켤 때 새로 만든다. (로컬 개발용)
 */
@Slf4j
@Component
@EnableConfigurationProperties(WebPushProperties.class)
public class VapidKeys {

    private final ECPrivateKey privateKey;
    private final String publicKey;

    public VapidKeys(WebPushProperties properties) {
        if (isBlank(properties.publicKey()) || isBlank(properties.privateKey())) {
            if (properties.requireKeys()) {
                throw new IllegalStateException("WEB_PUSH_PUBLIC_KEY와 WEB_PUSH_PRIVATE_KEY가 비어 있습니다. 운영에서는 고정 VAPID 키가 필요합니다.");
            }
            KeyPair generated = WebPushCrypto.generateKeyPair();
            this.privateKey = (ECPrivateKey) generated.getPrivate();
            this.publicKey = WebPushCrypto.base64Url(WebPushCrypto.encodePublicKey((ECPublicKey) generated.getPublic()));
            log.warn("web-push.public-key/private-key가 없어 VAPID 키를 새로 만들었습니다. 서버를 다시 켜면 브라우저가 다시 구독합니다. 운영에서는 고정 키를 넣으세요.");
            return;
        }
        this.privateKey = WebPushCrypto.decodePrivateKey(WebPushCrypto.fromBase64Url(properties.privateKey()));
        // 형식이 틀린 키면 서버가 켜지지 않게 한 번 풀어 본다
        this.publicKey = WebPushCrypto.base64Url(WebPushCrypto.encodePublicKey(
                WebPushCrypto.decodePublicKey(WebPushCrypto.fromBase64Url(properties.publicKey()))));
    }

    /** base64url 공개키. 프론트가 pushManager.subscribe의 applicationServerKey로 쓴다. */
    public String publicKey() {
        return publicKey;
    }

    ECPrivateKey privateKey() {
        return privateKey;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
