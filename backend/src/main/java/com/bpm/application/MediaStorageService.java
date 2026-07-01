package com.bpm.application;

import com.bpm.domain.social.PostMedia;
import com.bpm.infrastructure.PostMediaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu/đọc media bài bảng tin trên ĐĨA server (NFR-03, NFR-09). Tương tự cơ chế lưu tài liệu GĐ1.
 * Kiểm loại file an toàn: chỉ ảnh/video qua allowlist content-type + đối chiếu MAGIC BYTES (chống đổi đuôi),
 * giới hạn dung lượng (ảnh ≤10MB, video ≤100MB), từ chối khác. Đọc theo stream/transferTo tránh OOM.
 */
@Service
public class MediaStorageService {

    public static final long MAX_IMAGE = 10L * 1024 * 1024;   // 10MB
    public static final long MAX_VIDEO = 100L * 1024 * 1024;  // 100MB

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");

    private final PostMediaRepository repo;
    private final Path root;

    public MediaStorageService(PostMediaRepository repo,
                               @Value("${bpm.media.dir:./data/media}") String mediaDir) {
        this.repo = repo;
        this.root = Path.of(mediaDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được thư mục lưu media: " + this.root, e);
        }
    }

    /** Lưu một file media (chưa gắn bài). Trả descriptor đã lưu. Ném IllegalArgumentException nếu không hợp lệ. */
    @Transactional
    public PostMedia store(MultipartFile file, String actor) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File rỗng");
        }
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        long size = file.getSize();

        String kind;
        if (IMAGE_TYPES.contains(ct)) {
            kind = "IMAGE";
            if (size > MAX_IMAGE) {
                throw new IllegalArgumentException("Ảnh vượt quá 10MB");
            }
        } else if (VIDEO_TYPES.contains(ct)) {
            kind = "VIDEO";
            if (size > MAX_VIDEO) {
                throw new IllegalArgumentException("Video vượt quá 100MB");
            }
        } else {
            throw new IllegalArgumentException("Loại file không cho phép (chỉ ảnh JPEG/PNG/GIF/WEBP hoặc video MP4/WEBM/MOV)");
        }

        byte[] head = readHead(file, 16);
        if (!sniffMatches(head, ct)) {
            throw new IllegalArgumentException("Nội dung file không khớp loại khai báo (nghi ngờ giả mạo)");
        }

        String id = UUID.randomUUID().toString();
        String dir = id.substring(0, 2);
        String ext = extOf(ct);
        String rel = dir + "/" + id + ext;
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
            throw new IllegalStateException("Không lưu được media: " + e.getMessage(), e);
        }
        return repo.save(new PostMedia(id, kind, safeName(file.getOriginalFilename()), ct, size, rel, actor));
    }

    @Transactional(readOnly = true)
    public PostMedia get(String id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy media"));
    }

    public Path pathOf(PostMedia m) {
        Path p = root.resolve(m.getRelPath()).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Đường dẫn media không hợp lệ");
        }
        return p;
    }

    // ===== helpers =====

    private static byte[] readHead(MultipartFile file, int n) {
        try (var in = file.getInputStream()) {
            byte[] buf = new byte[n];
            int read = in.readNBytes(buf, 0, n);
            if (read < n) {
                byte[] trimmed = new byte[Math.max(read, 0)];
                System.arraycopy(buf, 0, trimmed, 0, Math.max(read, 0));
                return trimmed;
            }
            return buf;
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được nội dung file");
        }
    }

    /** Đối chiếu magic bytes thô để chặn đổi đuôi. (Không thay thế quét virus đầy đủ, nhưng chặn case phổ biến.) */
    private static boolean sniffMatches(byte[] b, String ct) {
        switch (ct) {
            case "image/jpeg":
                return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "image/png":
                return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/gif":
                return b.length >= 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
            case "image/webp":
                return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                        && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            case "video/mp4":
            case "video/quicktime":
                // ftyp box ở offset 4
                return b.length >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
            case "video/webm":
                return b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                        && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
            default:
                return false;
        }
    }

    private static String extOf(String ct) {
        return switch (ct) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            default -> ".bin";
        };
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "media";
        }
        String n = name.replaceAll("[\\r\\n\"\\\\/]+", "_");
        return n.length() > 200 ? n.substring(0, 200) : n;
    }
}
