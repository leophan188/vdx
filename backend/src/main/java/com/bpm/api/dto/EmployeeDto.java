package com.bpm.api.dto;

import com.bpm.domain.hr.Employee;
import com.bpm.domain.hr.EmployeeImportLog;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** DTO màn Quản lý nhân sự + Import từ file (Epic 1 GĐ2). */
public final class EmployeeDto {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private EmployeeDto() {
    }

    /**
     * Bản ghi nhân sự đầy đủ 14 trường + metadata + liên thông tổ chức (Epic 1 GĐ2).
     * {@code orgUnitName/positionTitle/roleNames} chỉ điền khi gọi {@link #of(Employee, OrgInfo)} (màn chi tiết).
     */
    public record EmployeeResponse(
            String id, String empCode, String status, String fullName, String jobPosition, String title,
            String deptCode, String unit, String joinDate, String birthDate, String leaveDate,
            String phone, String contractType,
            String bankAccount, String bankName, String level, boolean external, String userAccountId,
            String orgUnitId, String orgUnitName, String positionId, String positionTitle, List<String> roleNames,
            String updatedAt, String updatedBy,
            List<String> projects, int totalEffort) {

        /** Bản gọn (list / kết quả sửa) — không resolve tên cây/vai trò. */
        public static EmployeeResponse of(Employee e) {
            return of(e, null);
        }

        /** Bản đầy đủ (chi tiết) — kèm tên cây OrgUnit, tiêu đề vị trí, tên vai trò. */
        public static EmployeeResponse of(Employee e, OrgInfo org) {
            return of(e, org, List.of(), 0);
        }

        /** Bản kèm dự án đang join + tổng % effort (cho danh sách nhân sự). */
        public static EmployeeResponse of(Employee e, OrgInfo org, List<String> projects, int totalEffort) {
            return new EmployeeResponse(
                    e.getId(), e.getEmpCode(), e.getStatus(), e.getFullName(), e.getJobPosition(), e.getTitle(),
                    e.getDeptCode(), e.getUnit(),
                    e.getJoinDate() == null ? null : e.getJoinDate().format(DMY),
                    e.getBirthDate() == null ? null : e.getBirthDate().format(DMY),
                    e.getLeaveDate() == null ? null : e.getLeaveDate().format(DMY),
                    e.getPhone(), e.getContractType(), e.getBankAccount(), e.getBankName(), e.getLevel(),
                    e.isExternal(), e.getUserAccountId(),
                    e.getOrgUnitId(), org == null ? null : org.orgUnitName(),
                    e.getPositionId(), org == null ? null : org.positionTitle(),
                    org == null ? List.of() : org.roleNames(),
                    e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString(), e.getUpdatedBy(),
                    projects == null ? List.of() : projects, totalEffort);
        }
    }

    /** Thông tin liên thông đã resolve cho màn chi tiết (tên cây OrgUnit, tiêu đề vị trí, tên vai trò). */
    public record OrgInfo(String orgUnitName, String positionTitle, List<String> roleNames) {
    }

    /**
     * Tạo mới nhân sự THỦ CÔNG (thuê ngoài/mượn) — KHÔNG qua import. empCode bắt buộc & duy nhất.
     * Nhân sự tạo qua đây luôn được đánh dấu external=true (tự cập nhật tay, không bị import/sync đụng vào).
     */
    public record CreateRequest(
            String empCode, String status, String fullName, String jobPosition, String title, String deptCode,
            String unit, String joinDate, String birthDate, String phone, String contractType,
            String bankAccount, String bankName, String level) {
    }

    /** Sửa tay thông tin nhân sự (ngày dạng dd/MM/yyyy chuỗi). empCode KHÔNG sửa (khoá nghiệp vụ). */
    public record UpdateRequest(
            String status, String fullName, String jobPosition, String title, String deptCode, String unit,
            String joinDate, String birthDate, String phone, String contractType,
            String bankAccount, String bankName, String level) {
    }

    /** Một dòng trong bảng xem trước import — ĐỦ 14 cột để hiển thị toàn bộ. action ∈ ADD|UPDATE|LOCK|HANDOVER|ERROR. */
    public record PreviewRow(
            String action, String empCode, String status, String fullName, String jobPosition, String title,
            String deptCode, String unit, String joinDate, String birthDate, String phone, String contractType,
            String bankAccount, String bankName, String level, String message) {
    }

    /**
     * Kết quả xem trước import — đã phân nhóm + dòng lỗi.
     * {@code newOrgUnits/newPositions/newRoles}: số đơn vị/vị trí/vai trò SẼ tạo mới khi áp dụng (tính trên bản sao).
     */
    public record PreviewResponse(
            List<PreviewRow> add, List<PreviewRow> update, List<PreviewRow> lock,
            List<PreviewRow> handover, List<PreviewRow> errors, int totalRead,
            int newOrgUnits, int newPositions, int newRoles) {
    }

    /** Kết quả áp dụng import. */
    public record ApplyResponse(
            int added, int updated, int locked, int handover, int errors, List<PreviewRow> handoverDetail) {
    }

    /** Một bản ghi nhật ký import. */
    public record LogResponse(
            String id, String runAt, String runBy, String fileName,
            int added, int updated, int locked, int handover, int errors, String note) {

        public static LogResponse from(EmployeeImportLog l) {
            return new LogResponse(l.getId(), l.getRunAt().toString(), l.getRunBy(), l.getFileName(),
                    l.getAddedCount(), l.getUpdatedCount(), l.getLockedCount(),
                    l.getHandoverCount(), l.getErrorCount(), l.getNote());
        }
    }
}
