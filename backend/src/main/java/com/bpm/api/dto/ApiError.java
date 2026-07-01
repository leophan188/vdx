package com.bpm.api.dto;

import java.util.List;

/** Envelope lỗi chuẩn (Consistency Conventions): {code, message, details[], traceId}. */
public record ApiError(String code, String message, List<String> details, String traceId) {
}
