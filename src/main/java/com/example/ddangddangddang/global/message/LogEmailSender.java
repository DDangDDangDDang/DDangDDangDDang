package com.example.ddangddangddang.global.message;

import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 개발용. 메일을 보내지 않고 내용을 로그로 출력한다.
 * 인증번호가 로그에 남으므로 운영에서는 쓰면 안 된다. (app.mail.enabled=true로 SMTP 사용)
 */
@Slf4j
public class LogEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String text) {
        log.info("[메일 발송 생략] to={}, subject={}, text={}", to, subject, text);
    }
}