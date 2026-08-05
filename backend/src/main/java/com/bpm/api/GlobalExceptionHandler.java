package com.bpm.api;

import com.bpm.api.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Map ngoại lệ sang envelope lỗi chuẩn {code, message, details[], traceId}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    /** MariaDB/MySQL: "Data too long for column 'description' at row 1". */
    private static final Pattern TOO_LONG = Pattern.compile("Data too long for column '([a-z_]+)'");

    private static String traceId() {
        return UUID.randomUUID().toString();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        // Thông điệp chung — không lộ tài khoản tồn tại hay không (AC-2).
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("AUTH_FAILED", "Tên đăng nhập hoặc mật khẩu không đúng", List.of(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("BAD_REQUEST", ex.getMessage(), List.of(), traceId()));
    }

    /** Tên cột kỹ thuật → tên người dùng nhìn thấy trên form. */
    private static String fieldLabel(String column) {
        return switch (column) {
            case "description" -> "Mô tả";
            case "title" -> "Tiêu đề";
            case "screen" -> "Màn hình liên quan";
            case "environment" -> "Môi trường";
            case "note" -> "Ghi chú";
            default -> column;
        };
    }

    /**
     * Vi phạm ràng buộc DB (quá dài, trùng khoá, thiếu giá trị bắt buộc).
     *
     * Không có handler này thì mọi vi phạm rơi thành 500 với thân rỗng — người dùng chỉ thấy
     * "Không tạo được" không kèm lý do, bấm lại chục lần vẫn thế. Đã xảy ra thật: mô tả lỗi
     * vượt giới hạn cột, tester bấm Lưu 13 lần mà không biết phải sửa gì.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        String raw = ex.getMostSpecificCause().getMessage();
        String msg = "Dữ liệu không lưu được do vi phạm ràng buộc của cơ sở dữ liệu";
        if (raw != null) {
            Matcher m = TOO_LONG.matcher(raw);
            if (m.find()) {
                msg = "Nội dung ô \"" + fieldLabel(m.group(1)) + "\" quá dài so với giới hạn cho phép — "
                        + "hãy rút ngắn bớt rồi lưu lại";
            } else if (raw.contains("Duplicate entry")) {
                msg = "Dữ liệu bị trùng với bản ghi đã có";
            } else if (raw.contains("cannot be null")) {
                msg = "Còn trường bắt buộc chưa nhập";
            }
        }
        // Ghi log nguyên văn để lập trình viên truy được, nhưng KHÔNG trả chuỗi SQL ra cho người dùng.
        log.warn("Vi phạm ràng buộc DB: {}", raw);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("DATA_INVALID", msg, List.of(), traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details, traceId()));
    }
}
