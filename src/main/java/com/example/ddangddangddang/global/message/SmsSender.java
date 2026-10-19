package com.example.ddangddangddang.global.message;

/**
 * 문자 발송. 운영은 SOLAPI, 로컬·테스트는 로그 출력으로 바꿔 끼운다.
 */
public interface SmsSender {

    void send(String phoneNumber, String text);
}