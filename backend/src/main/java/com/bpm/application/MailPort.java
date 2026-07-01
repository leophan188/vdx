package com.bpm.application;

/**
 * Cổng gửi email (Story 4.10). GĐ1 dùng adapter ghi log (chưa cần SMTP); thay bằng JavaMailSender khi có máy chủ thư.
 */
public interface MailPort {
    void send(String to, String subject, String body);
}
