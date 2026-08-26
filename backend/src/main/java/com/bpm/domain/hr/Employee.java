package com.bpm.domain.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Nhân sự (Epic 1 GĐ2 — FR-A01..A08). Nguồn dữ liệu là FILE nhân sự (Excel/CSV) do admin tải lên,
 * thay cho cách đồng bộ Google-link cũ. Khoá định danh nghiệp vụ = {@code empCode} (cột "ID" trong file).
 *
 * <p>Conventions: PK UUID (chuỗi 36 ký tự), cột snake_case, thời gian UTC (Instant cho metadata,
 * LocalDate cho ngày nghiệp vụ dd/MM/yyyy). Liên kết {@code userAccountId} tới tài khoản đăng nhập
 * (username = empCode) để mở/khoá theo trạng thái nhân sự.
 */
@Entity
@Table(name = "employee", indexes = {
        @Index(name = "ix_employee_emp_code", columnList = "emp_code", unique = true),
        @Index(name = "ix_employee_dept_code", columnList = "dept_code")
})
public class Employee {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** Mã nhân sự — cột "ID" trong file (vd "0025", "1353.1"). Duy nhất, khoá đối chiếu import. */
    @Column(name = "emp_code", length = 50, nullable = false, unique = true)
    private String empCode;

    /** Trạng thái nhân sự theo file (vd "Đang làm việc"). Khác "Đang làm việc" → khoá tài khoản. */
    @Column(name = "status", length = 100)
    private String status;

    @Column(name = "full_name", length = 200, nullable = false)
    private String fullName;

    /** Vị trí công việc. */
    @Column(name = "job_position", length = 200)
    private String jobPosition;

    /** Chức danh. */
    @Column(name = "title", length = 200)
    private String title;

    /** Mã bộ phận. */
    @Column(name = "dept_code", length = 100)
    private String deptCode;

    /** Đơn vị. */
    @Column(name = "unit", length = 200)
    private String unit;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * Ngày rời công ty — chốt lúc hồ sơ chuyển sang trạng thái không còn làm việc, để thâm niên dừng
     * đúng ngày nghỉ thay vì chạy tiếp tới hôm nay. Nguồn nhân sự KHÔNG có cột này nên giá trị ghi
     * được là ngày hệ thống ghi nhận, không phải ngày nghỉ trên giấy tờ; ai quay lại làm thì xoá đi.
     * Để null cho hồ sơ cũ — {@code ddl-auto=update} thêm cột này rỗng, và null ở đây có nghĩa
     * "không biết", màn hình tự lùi về mốc khác.
     */
    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Column(name = "phone", length = 50)
    private String phone;

    /** Loại hợp đồng hiện tại. */
    @Column(name = "contract_type", length = 200)
    private String contractType;

    @Column(name = "bank_account", length = 100)
    private String bankAccount;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "level", length = 100)
    private String level;

    /**
     * Nhân sự thuê ngoài / mượn — tạo & cập nhật THỦ CÔNG, KHÔNG đồng bộ từ file/Google Sheet.
     * Khi import/sync, nhân sự external được LOẠI TRỪ khỏi nhóm khoá (vắng mặt trong file ≠ nghỉ).
     * Nullable + default false để không vỡ ddl-update trên dữ liệu cũ.
     */
    @Column(name = "external", nullable = true, columnDefinition = "boolean default false")
    private Boolean external = Boolean.FALSE;

    /** Liên kết tài khoản đăng nhập (nullable cho tới khi tạo tài khoản khi apply). */
    @Column(name = "user_account_id", length = 36)
    private String userAccountId;

    /** Liên thông cơ cấu tổ chức (Epic 1 GĐ2): OrgUnit lá theo Đơn vị + Mã bộ phận. Nullable nếu không có org. */
    @Column(name = "org_unit_id", length = 36)
    private String orgUnitId;

    /** Liên thông vị trí (Epic 1 GĐ2): Position (1 ghế) của nhân sự, người giữ = tài khoản nhân sự. */
    @Column(name = "position_id", length = 36)
    private String positionId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    protected Employee() {
    }

    public Employee(String empCode, String fullName, String actor) {
        this.id = UUID.randomUUID().toString();
        this.empCode = empCode;
        this.fullName = fullName;
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    /** Cập nhật toàn bộ trường nghiệp vụ (dùng cho upsert import + sửa tay). */
    public void apply(String status, String fullName, String jobPosition, String title, String deptCode,
                      String unit, LocalDate joinDate, LocalDate birthDate, String phone, String contractType,
                      String bankAccount, String bankName, String level, String actor) {
        this.status = status;
        this.fullName = fullName;
        this.jobPosition = jobPosition;
        this.title = title;
        this.deptCode = deptCode;
        this.unit = unit;
        this.joinDate = joinDate;
        this.birthDate = birthDate;
        this.phone = phone;
        this.contractType = contractType;
        this.bankAccount = bankAccount;
        this.bankName = bankName;
        this.level = level;
        syncLeaveDate(LocalDate.now());
        touch(actor);
    }

    /**
     * Đồng bộ ngày nghỉ theo trạng thái hiện tại. Chỉ ghi LẦN ĐẦU chuyển sang nghỉ: mỗi lần đồng bộ
     * lại ghi đè thì ngày nghỉ luôn bằng hôm nay và thâm niên của người đã nghỉ cứ thế tăng mãi.
     */
    public void syncLeaveDate(LocalDate today) {
        if (isActive()) {
            this.leaveDate = null;
        } else if (hasLeft() && this.leaveDate == null) {
            this.leaveDate = today;
        }
    }

    /**
     * ĐÃ NGHỈ thật sự — phân biệt với "Chưa Onboard", cũng là trạng thái không-đang-làm nhưng theo
     * hướng ngược lại: người chưa vào làm thì không có ngày nghỉ và cũng chưa có thâm niên nào.
     */
    public boolean hasLeft() {
        return status != null && status.trim().toLowerCase().contains("nghỉ");
    }

    public void linkAccount(String userAccountId, String actor) {
        this.userAccountId = userAccountId;
        touch(actor);
    }

    /** Gán OrgUnit lá liên thông (Epic 1 GĐ2). */
    public void linkOrgUnit(String orgUnitId, String actor) {
        this.orgUnitId = orgUnitId;
        touch(actor);
    }

    /** Gán Position liên thông (Epic 1 GĐ2). */
    public void linkPosition(String positionId, String actor) {
        this.positionId = positionId;
        touch(actor);
    }

    private void touch(String actor) {
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    /**
     * Nhân sự còn đang làm việc. Đối chiếu bằng CHỨA "đang làm" chứ không so khớp nguyên chuỗi:
     * file nhân sự thực tế có biến thể ("Đang làm việc - thử việc"), khớp nguyên chuỗi sẽ coi những
     * người này là đã nghỉ — cắt nhầm quyền dự án và mất luôn tin sinh nhật của họ.
     * Đây cũng đúng cách màn Nhân sự đếm ô "Đang làm việc".
     */
    public boolean isActive() {
        return status != null && status.trim().toLowerCase().contains("đang làm");
    }

    public String getId() { return id; }
    public String getEmpCode() { return empCode; }
    public void setEmpCode(String empCode) { this.empCode = empCode; } // chuẩn hoá (giữ số 0 đầu)
    public LocalDate getLeaveDate() { return leaveDate; }
    public void setLeaveDate(LocalDate leaveDate) { this.leaveDate = leaveDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getJobPosition() { return jobPosition; }
    public void setJobPosition(String jobPosition) { this.jobPosition = jobPosition; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDeptCode() { return deptCode; }
    public void setDeptCode(String deptCode) { this.deptCode = deptCode; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    /** Nhân sự thuê ngoài/mượn (true) — null coi như false (dữ liệu cũ). */
    public boolean isExternal() { return Boolean.TRUE.equals(external); }
    public void setExternal(boolean external) { this.external = external; }
    public String getUserAccountId() { return userAccountId; }
    public String getOrgUnitId() { return orgUnitId; }
    public String getPositionId() { return positionId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
