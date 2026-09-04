package com.bpm.infrastructure.erp;

import com.bpm.domain.erp.ErpConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Đọc cây phòng ban ERP — dùng để chốt đơn vị mà hệ thống này lấy dữ liệu. */
@Component
public class OdooOrgClient {

    private final OdooAttendanceClient rpc;

    public OdooOrgClient(OdooAttendanceClient rpc) {
        this.rpc = rpc;
    }

    /**
     * @param completeName đường dẫn đầy đủ trên cây ("VMO / Khối Kinh doanh / VMO DX") — thứ duy nhất
     *                     phân biệt được hai đơn vị trùng tên ở hai nhánh khác nhau
     */
    public record Department(long id, String name, String completeName, int employeeCount) {
    }

    /** Tìm phòng ban theo tên (khớp một phần, không phân biệt hoa thường). */
    public List<Department> findDepartments(ErpConfig cfg, String name) {
        List<Object> domain = List.of(List.of("complete_name", "ilike", name == null ? "" : name.trim()));
        Map<Long, Map<String, JsonNode>> rows = rpc.searchReadTolerant(cfg, "hr.department", domain,
                List.of("name", "complete_name"), List.of(List.of("total_employee")), 50);
        List<Department> out = new ArrayList<>();
        for (Map.Entry<Long, Map<String, JsonNode>> e : rows.entrySet()) {
            Map<String, JsonNode> v = e.getValue();
            out.add(new Department(e.getKey(), text(v, "name"), text(v, "complete_name"),
                    v.containsKey("total_employee") ? v.get("total_employee").asInt(0) : 0));
        }
        out.sort((a, b) -> a.completeName().compareToIgnoreCase(b.completeName()));
        return out;
    }

    private static String text(Map<String, JsonNode> v, String field) {
        JsonNode n = v.get(field);
        return n == null || n.isNull() || n.isBoolean() ? "" : n.asText();
    }
}
