package com.example.ddangddangddang.domain.seller.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HexFormat;

/**
 * 판매자 신청 전에 받는 휴대폰 본인인증 결과. 회원당 하나이고, 한 사람(CI)은 한 계정에서만 인증할 수 있다.
 * 실명·생년월일은 판매자만 받는다. (구매자는 실명이 필요 없다)
 * CI는 그대로 두지 않고 SHA-256 해시로만 저장한다. 중복 확인에는 해시끼리 비교하면 충분하다.
 * 실서비스에서는 실명·생년월일도 암호화 저장이 필요하다.
 */
@Entity
@Table(
        name = "seller_identities",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_seller_identity_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_seller_identity_ci", columnNames = "ci_hash")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerIdentity extends BaseTimeEntity {

    /** 판매자가 될 수 있는 나이 (민법상 성년, 만 19세) */
    public static final int ADULT_AGE = 19;
    public static final int NAME_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "real_name", nullable = false, length = NAME_MAX_LENGTH)
    private String realName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "ci_hash", nullable = false, length = 64)
    private String ciHash;

    // 마지막으로 인증한 포트원 본인인증 ID (문의 대응용)
    @Column(name = "identity_verification_id", nullable = false, length = 100)
    private String identityVerificationId;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    private SellerIdentity(Long userId, String realName, LocalDate birthDate, String ciHash,
                           String identityVerificationId, LocalDateTime verifiedAt) {
        this.userId = userId;
        this.realName = realName;
        this.birthDate = birthDate;
        this.ciHash = ciHash;
        this.identityVerificationId = identityVerificationId;
        this.verifiedAt = verifiedAt;
    }

    public static SellerIdentity create(Long userId, String realName, LocalDate birthDate, String ciHash,
                                        String identityVerificationId, LocalDateTime verifiedAt) {
        return new SellerIdentity(userId, realName, birthDate, ciHash, identityVerificationId, verifiedAt);
    }

    /** 같은 사람이 다시 인증한 경우 (개명 등). 이름·생년월일을 새 결과로 바꾼다. */
    public void reverify(String realName, LocalDate birthDate, String identityVerificationId, LocalDateTime verifiedAt) {
        this.realName = realName;
        this.birthDate = birthDate;
        this.identityVerificationId = identityVerificationId;
        this.verifiedAt = verifiedAt;
    }

    public boolean isSamePerson(String ciHash) {
        return this.ciHash.equals(ciHash);
    }

    /** 정산 계좌 예금주가 본인인증 실명과 같은지. 띄어쓰기는 무시한다. (은행마다 예금주 표기의 띄어쓰기가 다르다) */
    public boolean isAccountHolder(String accountHolder) {
        return accountHolder != null && withoutSpaces(realName).equals(withoutSpaces(accountHolder));
    }

    private static String withoutSpaces(String value) {
        return value.replaceAll("\\s", "");
    }

    public static boolean isAdult(LocalDate birthDate, LocalDate today) {
        return Period.between(birthDate, today).getYears() >= ADULT_AGE;
    }

    public static String hashCi(String ci) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ci.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }
}
