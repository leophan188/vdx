package com.bpm.api.dto;

import com.bpm.domain.audit.AuditEvent;

import java.time.Instant;

public class AuditEventDto {

    public record Response(String id, String action, String objectType, String objectId,
                           String actor, String detail, Instant createdAt) {
        public static Response from(AuditEvent e) {
            return new Response(e.getId(), e.getAction(), e.getObjectType(), e.getObjectId(),
                    e.getActor(), e.getDetail(), e.getCreatedAt());
        }
    }
}
