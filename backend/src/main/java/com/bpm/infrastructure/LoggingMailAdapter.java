package com.bpm.infrastructure;

import com.bpm.application.MailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter email mặc định (Story 4.10): GHI LOG thay vì gửi thật (chưa cấu hình SMTP GĐ1).
 * Khi có máy chủ thư: thêm adapter JavaMailSender + @Primary, không đổi nơi gọi.
 */
@Component
public class LoggingMailAdapter implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailAdapter.class);

    @Override
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.info("[MAIL] (bỏ qua — người nhận chưa có email) subject='{}'", subject);
            return;
        }
        log.info("[MAIL] → {} | {}\n{}", to, subject, body);
    }
}
