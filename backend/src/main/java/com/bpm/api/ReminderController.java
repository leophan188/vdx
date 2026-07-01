package com.bpm.api;

import com.bpm.application.WorkflowService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Nhắc hạn việc quá hạn (Story 4.10) — chạy tay (ngoài lịch tự động) cho admin. */
@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final WorkflowService workflow;

    public ReminderController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    /** Quét + nhắc ngay. cooldownHours=0 để buộc nhắc lại (demo/kiểm thử). */
    @PostMapping("/run")
    public Map<String, Integer> run(@RequestParam(defaultValue = "12") int cooldownHours) {
        return Map.of("reminded", workflow.runOverdueReminders(cooldownHours));
    }
}
