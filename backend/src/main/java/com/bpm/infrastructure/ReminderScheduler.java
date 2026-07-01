package com.bpm.infrastructure;

import com.bpm.application.WorkflowService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lịch quét việc quá hạn → nhắc hạn qua email/thông báo (Story 4.10/5.7). Chạy ở mọi profile TRỪ 'test'
 * (tránh ảnh hưởng kiểm thử); chu kỳ cấu hình qua {@code bpm.reminder.fixed-delay-ms}.
 */
@Component
@Profile("!test")
public class ReminderScheduler {

    private final WorkflowService workflow;

    public ReminderScheduler(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @Scheduled(initialDelayString = "${bpm.reminder.initial-delay-ms:120000}",
            fixedDelayString = "${bpm.reminder.fixed-delay-ms:3600000}")
    public void scan() {
        workflow.runOverdueReminders(12);
    }
}
