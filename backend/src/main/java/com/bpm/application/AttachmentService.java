package com.bpm.application;

import com.bpm.domain.attachment.Attachment;
import com.bpm.infrastructure.AttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lưu/đọc tệp đính kèm của biểu mẫu (trường "Tải file") trên ĐĨA server (bpm.attachment.dir).
 * DB chỉ giữ metadata + đường dẫn tương đối; formData chỉ giữ tham chiếu (id) dạng JSON.
 * Chấp nhận tài liệu văn phòng + PDF/ảnh/nén thông dụng theo allowlist ĐUÔI FILE, giới hạn 25MB.
 */
@Service
public class AttachmentService {

    public static final long MAX_SIZE = 25L * 1024 * 1024; // 25MB

    /** Đuôi file cho phép → không cho thực thi (exe/sh/...) hay script. */
    private static final Map<String, String> ALLOWED_EXT = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp")
    );

    private final AttachmentRepository repo;
    private final Path root;

    public AttachmentService(AttachmentRepository repo,
                             @Value("${bpm.attachment.dir:./data/attachments}") String dir) {
        this.repo = repo;
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được thư mục lưu tệp đính kèm: " + this.root, e);
        }
    }

    /** Lưu một tệp đính kèm. Ném IllegalArgumentException nếu không hợp lệ. */
    @Transactional
    public Attachment store(MultipartFile file, String actor) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp rỗng");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Tệp vượt quá 25MB");
        }
        String name = safeName(file.getOriginalFilename());
        String ext = extOf(name);
        String ct = ALLOWED_EXT.get(ext);
        if (ct == null) {
            throw new IllegalArgumentException("Định dạng không cho phép (chỉ PDF/Office/ảnh/nén, tối đa 25MB)");
        }

        String id = UUID.randomUUID().toString();
        String rel = id.substring(0, 2) + "/" + id + "." + ext;
        Path target = root.resolve(rel).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Đường dẫn lưu không hợp lệ");
        }
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không lưu được tệp: " + e.getMessage(), e);
        }
        return repo.save(new Attachment(id, name, ct, file.getSize(), rel, actor));
    }

    @Transactional(readOnly = true)
    public Attachment get(String id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tệp đính kèm"));
    }

    public Path pathOf(Attachment a) {
        Path p = root.resolve(a.getRelPath()).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Đường dẫn tệp không hợp lệ");
        }
        return p;
    }

    // ===== helpers =====

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1
                ? name.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "tep-dinh-kem";
        }
        String n = name.replaceAll("[\\r\\n\"\\\\/]+", "_").trim();
        return n.length() > 200 ? n.substring(0, 200) : n;
    }
}
