package com.example.ddangddangddang.global.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    /**
     * SMTP 발송은 1~2초 걸리므로 요청 스레드를 붙잡지 않도록 비동기로 보낸다.
     * 응답 시간이 "가입된 이메일이면 느리고, 아니면 빠른" 차이를 만들지 않게 하는 효과도 있다.
     */
    @Async
    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("메일 발송 실패: to={}", maskEmail(to), e);
        }
    }

    /**
     * user@example.com → u***@example.com
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return "****";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "****";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}