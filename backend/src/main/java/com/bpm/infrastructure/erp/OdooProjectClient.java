package com.bpm.infrastructure.erp;

import com.bpm.domain.erp.ErpConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc dự án và phân bổ nhân sự (allocation) của MỘT đơn vị trên cây tổ chức ERP.
 *
 * Bản Odoo của công ty tuỳ biến khá sâu: {@code project.project} có hơn trăm trường, trong đó vài
 * trường tính động sẽ NÉM LỖI khi đọc hàng loạt qua RPC (đọc kèm {@code code} hay {@code project_state}
 * là cả lời gọi hỏng, không chỉ riêng trường đó). Nên phần đọc ở đây thử theo từng NHÓM trường và bỏ
 * qua nhóm nào lỗi — lấy được ít thông tin vẫn hơn là cả danh sách dự án không hiện ra.
 */
@Component
public class OdooProjectClient {

    private static final Logger log = LoggerFactory.getLogger(OdooProjectClient.class);

    /** Nhóm trường đọc thêm, thử lần lượt; nhóm nào ERP từ chối thì bỏ qua. */
    private static final List<List<String>> OPTIONAL_FIELD_GROUPS = List.of(
            List.of("code"),
            List.of("project_state"),
            List.of("start_date", "end_date"),
            List.of("partner_id"),
            List.of("du"));

    private final OdooAttendanceClient rpc;

    public OdooProjectClient(OdooAttendanceClient rpc) {
        this.rpc = rpc;
    }

    /** Một dự án bên ERP, chỉ gồm phần dùng được để chọn và đồng bộ. */
    public record ErpProject(long erpId, String name, String code, String state,
                             LocalDate startDate, LocalDate endDate, String customer, String unit) {
    }

    /**
     * Dự án thuộc đơn vị {@code departmentId} và mọi đơn vị con.
     * Dùng {@code child_of} thay vì tự đi theo parent_id: cây phòng ban bên ERP có nhánh mà trường
     * parent_id đọc ra rỗng (PDX.1, PDX.2), tự duyệt cây sẽ bỏ sót 93 trên 104 người của VMO DX.
     */
    public List<ErpProject> fetchProjects(ErpConfig cfg, long departmentId) {
        List<Object> domain = List.of(List.of("du", "child_of", departmentId));
        Map<Long, Map<String, JsonNode>> rows = rpc.searchReadTolerant(cfg, "project.project", domain,
                List.of("name"), OPTIONAL_FIELD_GROUPS, 2000);

        List<ErpProject> out = new ArrayList<>();
        for (Map.Entry<Long, Map<String, JsonNode>> e : rows.entrySet()) {
            Map<String, JsonNode> v = e.getValue();
            out.add(new ErpProject(e.getKey(), text(v, "name"), text(v, "code"), text(v, "project_state"),
                    date(v, "start_date"), date(v, "end_date"),
                    many2oneName(v, "partner_id"), many2oneName(v, "du")));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        log.info("[erp] đọc {} dự án của đơn vị {}", out.size(), departmentId);
        return out;
    }

    private static String text(Map<String, JsonNode> v, String field) {
        JsonNode n = v.get(field);
        return n == null || n.isNull() || n.isBoolean() ? null : n.asText();
    }

    private static LocalDate date(Map<String, JsonNode> v, String field) {
        String s = text(v, field);
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Odoo trả many2one dạng [id, "Tên"]; lấy phần tên. */
    private static String many2oneName(Map<String, JsonNode> v, String field) {
        JsonNode n = v.get(field);
        return n != null && n.isArray() && n.size() > 1 ? n.get(1).asText() : null;
    }
}
