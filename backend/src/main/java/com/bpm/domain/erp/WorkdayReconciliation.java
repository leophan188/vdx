package com.bpm.domain.erp;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Kết quả đối soát công ERP vs công khách hàng cho MỘT kỳ.
 *
 * Quy đổi giờ → công: {@link #HOURS_PER_DAY} giờ = 1 công. Đây là quy ước, không phải sự thật hiển
 * nhiên — ai đổi giờ chuẩn thì đổi ở đây, đừng rải phép chia 8 khắp nơi.
 */
public final class WorkdayReconciliation {

    /** Giờ làm chuẩn cho một công. */
    public static final double HOURS_PER_DAY = 8d;

    /**
     * Ngưỡng coi là KHỚP: lệch dưới 0,01 công thì thôi. Số công là kết quả của phép chia nên gần như
     * luôn có đuôi thập phân; không có ngưỡng thì cả trăm dòng hiện "lệch 0,0000001" và bảng đối soát
     * mất sạch giá trị cảnh báo.
     */
    public static final double TOLERANCE_DAYS = 0.01d;

    private WorkdayReconciliation() {
    }

    /**
     * Một dòng đối soát.
     *
     * @param matchKey    khoá ghép (tên chuẩn hoá)
     * @param name        tên hiển thị — ưu tiên tên bên ERP, không có thì lấy tên khách hàng ghi
     * @param empCode     mã nhân sự khách hàng ghi (nếu file có)
     * @param erpHours    tổng giờ ERP trong kỳ
     * @param erpDays      giờ ERP quy ra công
     * @param erpDaysCount số NGÀY có chấm công (khác số công: một ngày chấm 4h vẫn là 1 ngày)
     * @param customerDays số công khách hàng ghi nhận
     * @param diffDays     ERP − khách hàng, dương nghĩa là ERP nhiều hơn
     * @param status      {@link Status}
     */
    public record Row(String matchKey, String name, String empCode,
                      double erpHours, double erpDays, int erpDaysCount,
                      double customerDays, double diffDays, Status status) {
    }

    /** Vì sao một dòng đáng chú ý — người đọc cần phân biệt "lệch số" với "chỉ có ở một bên". */
    public enum Status {
        MATCHED("Khớp"),
        DIFF("Lệch số công"),
        ERP_ONLY("Chỉ có ở ERP"),
        CUSTOMER_ONLY("Chỉ có ở khách hàng");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Tổng hợp cả kỳ, để đầu màn hình nói ngay tình trạng chung. */
    public record Summary(int total, int matched, int diff, int erpOnly, int customerOnly,
                          double erpDays, double customerDays, double diffDays) {
    }

    public static Summary summarize(List<Row> rows) {
        int matched = 0;
        int diff = 0;
        int erpOnly = 0;
        int custOnly = 0;
        double erp = 0;
        double cust = 0;
        for (Row r : rows) {
            switch (r.status()) {
                case MATCHED -> matched++;
                case DIFF -> diff++;
                case ERP_ONLY -> erpOnly++;
                case CUSTOMER_ONLY -> custOnly++;
            }
            erp += r.erpDays();
            cust += r.customerDays();
        }
        return new Summary(rows.size(), matched, diff, erpOnly, custOnly,
                round2(erp), round2(cust), round2(erp - cust));
    }

    /**
     * Khoá ghép hai nguồn: bỏ dấu, gộp khoảng trắng, chữ thường.
     *
     * Ghép theo TÊN chứ không theo mã vì hai hệ thống không dùng chung bộ mã: ERP có id nội bộ của
     * Odoo, file khách hàng có mã của họ (hoặc không có mã nào). Tên là thứ duy nhất chắc chắn xuất
     * hiện ở cả hai bên. Hệ quả phải chấp nhận: hai người trùng tên sẽ bị gộp làm một — bảng đối soát
     * hiện số ngày chấm công để người xem nhận ra ngay trường hợp đó.
     */
    public static String matchKey(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }

    /** Giờ → công, làm tròn 2 chữ số. */
    public static double toDays(double hours) {
        return round2(hours / HOURS_PER_DAY);
    }
}
