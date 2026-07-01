package com.bpm.domain.ot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Trạng thái chốt kỳ OT (Epic 3, FR-C05). PK = periodKey "YYYY-MM".
 * Một bản ghi chỉ tồn tại khi kỳ đã chốt (closed=true). Không có bản ghi ⇒ kỳ mở.
 */
@Entity
@Table(name = "ot_period")
public class OtPeriod {

    @Id
    @Column(name = "period_key", length = 7, nullable = false, updatable = false)
    private String periodKey;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    protected OtPeriod() {
    }

    public OtPeriod(String periodKey, String closedBy) {
        this.periodKey = periodKey;
        this.closed = true;
        this.closedAt = Instant.now();
        this.closedBy = closedBy;
    }

    public String getPeriodKey() { return periodKey; }
    public boolean isClosed() { return closed; }
    public Instant getClosedAt() { return closedAt; }
    public String getClosedBy() { return closedBy; }
}
