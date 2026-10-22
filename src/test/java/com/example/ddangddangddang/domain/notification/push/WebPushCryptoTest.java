package com.example.ddangddangddang.domain.notification.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static com.example.ddangddangddang.domain.notification.push.WebPushCrypto.base64Url;
import static com.example.ddangddangddang.domain.notification.push.WebPushCrypto.fromBase64Url;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushCryptoTest {

    @Test
    @DisplayName("RFC 8291 5장 예시와 같은 키·salt로 암호화하면 예시와 같은 본문이 나온다")
    void encryptMatchesRfc8291Example() {
        byte[] asPrivate = fromBase64Url("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] asPublic = fromBase64Url("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        KeyPair serverKeys = new KeyPair(WebPushCrypto.decodePublicKey(asPublic), WebPushCrypto.decodePrivateKey(asPrivate));

        byte[] body = WebPushCrypto.encrypt(
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                fromBase64Url("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                fromBase64Url("BTBZMqHH6r4Tts7J_aSIgg"),
                serverKeys,
                fromBase64Url("DGv6ra1nlYgDCS1FRnbzlw"));

        assertThat(base64Url(body)).isEqualTo("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    @DisplayName("VAPID 토큰은 ES256(R||S 64바이트)으로 서명되고, 공개키로 검증된다")
    void vapidTokenIsVerifiable() throws Exception {
        KeyPair keys = WebPushCrypto.generateKeyPair();
        String token = WebPushCrypto.vapidToken("https://fcm.googleapis.com", "mailto:admin@ddang.test", 1_800_000_000L, (ECPrivateKey) keys.getPrivate());

        String[] parts = token.split("\\.");
        assertThat(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1800000000,\"sub\":\"mailto:admin@ddang.test\"}");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(signature).hasSize(64);
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(keys.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    @DisplayName("공개키·개인키는 65·32바이트로 오가고, 형식이 틀리거나 곡선 밖의 점이면 거절한다")
    void keyEncoding() {
        KeyPair keys = WebPushCrypto.generateKeyPair();
        byte[] publicKey = WebPushCrypto.encodePublicKey((ECPublicKey) keys.getPublic());
        byte[] privateKey = WebPushCrypto.encodePrivateKey((ECPrivateKey) keys.getPrivate());

        assertThat(publicKey).hasSize(65);
        assertThat(WebPushCrypto.decodePublicKey(publicKey).getW()).isEqualTo(((ECPublicKey) keys.getPublic()).getW());
        assertThat(WebPushCrypto.decodePrivateKey(privateKey).getS()).isEqualTo(((ECPrivateKey) keys.getPrivate()).getS());
        assertThatThrownBy(() -> WebPushCrypto.decodePublicKey(new byte[64])).isInstanceOf(IllegalArgumentException.class);
        byte[] offCurve = publicKey.clone();
        offCurve[64] ^= 1;
        assertThatThrownBy(() -> WebPushCrypto.decodePublicKey(offCurve)).isInstanceOf(IllegalArgumentException.class);
    }
}
