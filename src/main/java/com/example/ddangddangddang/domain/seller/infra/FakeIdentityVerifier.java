package com.example.ddangddangddang.domain.seller.infra;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 로컬 개발·테스트용 가짜 본인인증. 어떤 ID를 보내도 설정한 이름·생년월일로 인증된 것으로 본다.
 * 포트원 본인인증 채널이 없는 로컬에서 판매자 신청 흐름을 확인하기 위한 것이라, 운영에서는 절대 켜지 않는다.
 *
 * seller.identity.provider=fake 일 때만 등록된다. CI는 본인인증 ID마다 다르게 만들어 계정끼리 겹치지 않는다.
 * seller.identity.fake.birth-date를 미성년자 생일로 바꾸면 미성년자 거절도 확인할 수 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "seller.identity", name = "provider", havingValue = "fake")
public class FakeIdentityVerifier implements IdentityVerifier {

    private final String name;
    private final LocalDate birthDate;

    public FakeIdentityVerifier(@Value("${seller.identity.fake.name:테스트판매자}") String name,
                                @Value("${seller.identity.fake.birth-date:1990-01-01}") LocalDate birthDate) {
        this.name = name;
        this.birthDate = birthDate;
    }

    @PostConstruct
    void warn() {
        log.warn("[FakeIdentityVerifier] 가짜 본인인증이 켜져 있습니다. 실제 명의 확인을 하지 않습니다. name={}, birthDate={}",
                name, birthDate);
    }

    @Override
    public VerifiedIdentity verify(String identityVerificationId) {
        return new VerifiedIdentity(name, birthDate, "fake-ci-" + identityVerificationId);
    }
}
