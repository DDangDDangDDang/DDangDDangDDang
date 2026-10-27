package com.example.ddangddangddang.global.message;

/**
 * 메일 발송. 운영은 SMTP, 로컬·테스트는 로그 출력으로 바꿔 끼운다.
 */
public interface EmailSender {

    void send(String to, String subject, String text);
}