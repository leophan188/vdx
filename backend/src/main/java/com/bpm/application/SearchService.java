package com.bpm.application;

import com.bpm.api.dto.SearchDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.project.Project;
import com.bpm.domain.social.Post;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.PostRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tìm kiếm toàn cục (quick-jump topbar). Quét 3 nguồn (nhân sự / dự án / tài khoản),
 * giới hạn ~6 mỗi loại, KHÔNG phân biệt dấu (chuẩn hoá NFD + bỏ dấu + đ→d + lowercase),
 * so khớp contains. Mỗi nhóm chỉ trả khi tài khoản có chức năng tương ứng
 * (ROLE_ADMIN hoặc FEAT_HR/FEAT_PROJECT/FEAT_ACCOUNTS). q rỗng/&lt;2 ký tự → tất cả rỗng.
 */
@Service
public class SearchService {

    /** Số kết quả tối đa mỗi nhóm. */
    private static final int LIMIT = 6;
    private static final int MIN_LEN = 2;

    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;
    private final UserAccountRepository userAccountRepository;
    private final PostRepository postRepository;

    public SearchService(EmployeeRepository employeeRepository,
                         ProjectRepository projectRepository,
                         UserAccountRepository userAccountRepository,
                         PostRepository postRepository) {
        this.employeeRepository = employeeRepository;
        this.projectRepository = projectRepository;
        this.userAccountRepository = userAccountRepository;
        this.postRepository = postRepository;
    }

    public SearchDto search(String rawQuery, Authentication auth) {
        String q = normalize(rawQuery);
        if (q.length() < MIN_LEN) {
            return new SearchDto(List.of(), List.of(), List.of(), List.of());
        }

        Set<String> authorities = auth == null ? Set.of()
                : auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
        boolean admin = authorities.contains("ROLE_ADMIN");

        List<SearchDto.Item> employees = (admin || authorities.contains("FEAT_HR"))
                ? searchEmployees(q) : List.of();
        List<SearchDto.Item> projects = (admin || authorities.contains("FEAT_PROJECT"))
                ? searchProjects(q) : List.of();
        List<SearchDto.Item> accounts = (admin || authorities.contains("FEAT_ACCOUNTS"))
                ? searchAccounts(q) : List.of();
        List<SearchDto.Item> posts = (admin || authorities.contains("FEAT_SOCIAL"))
                ? searchPosts(q) : List.of();

        return new SearchDto(employees, projects, accounts, posts);
    }

    /** Tìm BÀI VIẾT MXH (không tính bài ẩn): khớp nội dung hoặc tên tác giả. */
    private List<SearchDto.Item> searchPosts(String q) {
        List<SearchDto.Item> out = new ArrayList<>();
        for (Post p : postRepository.findAll()) {
            if (p.isHidden()) continue;
            if (matches(q, p.getBody()) || matches(q, p.getAuthorName())) {
                String body = p.getBody() == null ? "" : p.getBody().replaceAll("\\s*#\\S+\\s*$", "").replaceAll("\\s+", " ").trim();
                String name = body.length() > 60 ? body.substring(0, 60).trim() + "…" : body;
                out.add(new SearchDto.Item(p.getId(), null, name.isEmpty() ? "(bài viết)" : name, p.getAuthorName()));
                if (out.size() >= LIMIT) break;
            }
        }
        return out;
    }

    private List<SearchDto.Item> searchEmployees(String q) {
        List<SearchDto.Item> out = new ArrayList<>();
        for (Employee e : employeeRepository.findAll()) {
            if (matches(q, e.getFullName()) || matches(q, e.getEmpCode())) {
                out.add(new SearchDto.Item(e.getId(), e.getEmpCode(), e.getFullName(), sub(e)));
                if (out.size() >= LIMIT) break;
            }
        }
        return out;
    }

    private List<SearchDto.Item> searchProjects(String q) {
        List<SearchDto.Item> out = new ArrayList<>();
        for (Project p : projectRepository.findAll()) {
            if (matches(q, p.getName()) || matches(q, p.getCode())) {
                String status = p.getStatus() == null ? null : p.getStatus().name();
                out.add(new SearchDto.Item(p.getId(), p.getCode(), p.getName(), status));
                if (out.size() >= LIMIT) break;
            }
        }
        return out;
    }

    private List<SearchDto.Item> searchAccounts(String q) {
        List<SearchDto.Item> out = new ArrayList<>();
        for (UserAccount u : userAccountRepository.findAll()) {
            if (matches(q, u.getUsername()) || matches(q, u.getFullName())) {
                out.add(new SearchDto.Item(u.getId(), u.getUsername(), u.getFullName(), u.getUsername()));
                if (out.size() >= LIMIT) break;
            }
        }
        return out;
    }

    /** Dòng phụ nhân sự: vị trí + mã bộ phận (bỏ phần rỗng). */
    private static String sub(Employee e) {
        StringBuilder sb = new StringBuilder();
        if (e.getJobPosition() != null && !e.getJobPosition().isBlank()) {
            sb.append(e.getJobPosition().trim());
        }
        if (e.getDeptCode() != null && !e.getDeptCode().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(e.getDeptCode().trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** So khớp contains đã chuẩn hoá bỏ dấu (đầu vào q ĐÃ chuẩn hoá). */
    private static boolean matches(String q, String field) {
        if (field == null) return false;
        return normalize(field).contains(q);
    }

    /** Chuẩn hoá: NFD bỏ dấu, đ/Đ→d, lowercase, trim. */
    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return n.toLowerCase().trim();
    }
}
