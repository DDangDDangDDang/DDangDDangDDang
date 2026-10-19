package com.example.ddangddangddang.global.message;

import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 개발용. 문자를 보내지 않고 내용을 로그로 출력한다.
 * 인증번호가 로그에 남으므로 운영에서는 쓰면 안 된다. (app.sms.enabled=true로 SOLAPI 사용)
 */
@Slf4j
public class LogSmsSender implements SmsSender {

    @Override
    public void send(String phoneNumber, String text) {
        log.info("[문자 발송 생략] to={}, text={}", phoneNumber, text);
    }
}