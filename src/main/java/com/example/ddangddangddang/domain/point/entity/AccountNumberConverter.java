package com.example.ddangddangddang.domain.point.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 환전 계좌번호를 DB에 암호화해서 저장한다. (AES-256-GCM)
 * 저장 형식: "enc:v1:" + Base64(IV 12바이트 + 암호문·인증 태그). 같은 계좌번호도 저장할 때마다 다른 값이 된다.
 *
 * 키는 point.exchange.account-encryption-key (Base64로 쓴 32바이트). 운영은 Secrets Manager의 POINT_ACCOUNT_ENCRYPTION_KEY로 넣는다.
 * 키를 바꾸면 이미 저장한 계좌번호를 읽을 수 없으므로 바꾸지 않는다.
 * 암호화를 넣기 전에 저장된 평문 계좌번호는 접두사가 없으므로 그대로 읽는다. (다음에 그 행을 저장할 때 암호화된다)
 *
 * Spring Boot는 Hibernate가 컨버터를 스프링 빈으로 만들게 하므로 설정값을 주입받을 수 있다.
 */
@Component
@Converter
public class AccountNumberConverter implements AttributeConverter<String, String> {

    static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    // 로컬 개발·테스트용 키. 운영 설정(application-prod.yml)은 기본값 없이 환경변수를 요구한다.
    private static final String DEV_KEY = "ZGRhbmctbG9jYWwtYWNjb3VudC1rZXktMzJieXRlcyE=";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AccountNumberConverter(@Value("${point.exchange.account-encryption-key:" + DEV_KEY + "}") String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("point.exchange.account-encryption-key는 Base64로 쓴 32바이트(AES-256) 키여야 합니다.");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(accountNumber.getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("계좌번호를 암호화하지 못했습니다.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored; // 암호화 도입 전 평문
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(bytes, IV_LENGTH, bytes.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("계좌번호를 복호화하지 못했습니다. 암호화 키가 바뀌었는지 확인하세요.", e);
        }
    }
}
