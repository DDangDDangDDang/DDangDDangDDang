package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.AccountNumberConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 계좌번호 암호화 전에 평문으로 저장된 환전 계좌번호를 서버가 뜰 때 암호화해 다시 저장한다.
 * 끝난 환전(완료·실패·취소) 행은 다시 저장될 일이 없어, 이렇게 하지 않으면 평문이 계속 남는다.
 *
 * 평문 행이 없으면 조회 한 번으로 끝난다. 여러 서버가 동시에 떠도 "아직 평문인 행만" 바꾸므로 두 번 암호화되지 않는다.
 * 실패해도 서버 시작을 막지 않는다. (평문도 그대로 읽히므로 다음 시작 때 다시 시도한다)
 *
 * 기본으로 꺼 둔다. 배포 중에는 컨버터가 없는 이전 태스크가 함께 돌고, 롤백하면 이전 버전이 암호문을 읽지 못하기 때문이다.
 * 이 버전 배포가 안정된 뒤 point.exchange.reencrypt-on-startup=true(환경변수 POINT_EXCHANGE_REENCRYPTONSTARTUP=true)로 켜고
 * 한 번 다시 시작한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "point.exchange", name = "reencrypt-on-startup", havingValue = "true")
@RequiredArgsConstructor
public class AccountNumberReencryptRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AccountNumberConverter converter;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id, account_number from point_exchanges where account_number not like 'enc:v1:%'");
            int updated = 0;
            for (Map<String, Object> row : rows) {
                String plain = (String) row.get("account_number");
                if (plain == null || AccountNumberConverter.isEncrypted(plain)) {
                    continue;
                }
                updated += jdbcTemplate.update(
                        "update point_exchanges set account_number = ? where id = ? and account_number = ?",
                        converter.convertToDatabaseColumn(plain), row.get("id"), plain);
            }
            if (updated > 0) {
                log.info("평문 환전 계좌번호 {}건을 암호화했습니다.", updated);
            }
        } catch (Exception e) {
            log.warn("평문 환전 계좌번호 암호화 실패. 다음 서버 시작 때 다시 시도합니다.", e);
        }
    }
}
