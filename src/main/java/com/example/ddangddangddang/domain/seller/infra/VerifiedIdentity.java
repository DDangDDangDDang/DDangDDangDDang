package com.example.ddangddangddang.domain.seller.infra;

import java.time.LocalDate;

/**
 * 본인인증으로 확인된 명의자 정보.
 * ci: 사람마다 하나인 연계정보. 같은 사람이 계정을 여러 개 만들어도 같은 값이 나와 중복 인증을 막는 데 쓴다.
 */
public record VerifiedIdentity(String name, LocalDate birthDate, String ci) {
}
