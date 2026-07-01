package com.bpm.domain.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả kiểm tra định dạng file đầu vào theo mẫu (Epic 4, FR-D02).
 * Liệt kê lỗi theo dòng/cột để admin sửa & tải lại. valid=true khi không có lỗi.
 */
public class ValidationResult {

    /** Một lỗi định dạng. row=1-based số dòng trong sheet (0 = lỗi cấp file/header), column=tên cột nếu có. */
    public record Issue(int row, String column, String message) {
    }

    private final List<Issue> issues = new ArrayList<>();
    private int dataRows;

    public void add(int row, String column, String message) {
        issues.add(new Issue(row, column, message));
    }

    public void addFileLevel(String message) {
        issues.add(new Issue(0, null, message));
    }

    public void setDataRows(int n) { this.dataRows = n; }

    public boolean isValid() { return issues.isEmpty(); }

    public List<Issue> getIssues() { return issues; }

    public int getDataRows() { return dataRows; }
}
