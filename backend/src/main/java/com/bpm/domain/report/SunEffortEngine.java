package com.bpm.domain.report;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lõi tool "Phân bổ chi phí nhân sự (Sun ITS)" — KHÔNG phụ thuộc Spring để test thuần (cùng input → cùng output).
 *
 * Đầu vào: sheet đầu tiên của file (dữ liệu thô đã chuẩn hoá — "Raw normalized").
 * Quy ước đã chốt với nghiệp vụ:
 * · Total MD đọc THẲNG từ file (không tự tính lại từ số giờ / MD tự khai).
 * · Expense (VNĐ) đọc THẲNG từ file, nhưng đối chiếu với Total MD × Manday → lệch thì cảnh báo mềm (không chặn).
 * · Manday (VNĐ) luôn có sẵn trong file.
 *
 * Đầu ra: 3 bảng tổng hợp — theo nhân sự · theo dự án · theo cặp nhân sự × dự án
 * (cột Date của bảng thứ ba là KHOẢNG ngày từ–đến của cặp đó).
 */
public final class SunEffortEngine {

    /** Số dòng tối đa đẩy lên màn hình mỗi bảng; phần còn lại xem trong file .xlsx. */
    public static final int MAX_UI_ROWS = 200;

    /** Dung sai đối chiếu chi phí (VNĐ) — chênh dưới mức này coi như do làm tròn. */
    private static final double EXPENSE_TOLERANCE = 1.0d;

    /** Số cảnh báo lệch chi phí tối đa liệt kê chi tiết. */
    private static final int MAX_WARNINGS = 50;

    /** Nhãn cho dòng không điền tên dự án — vẫn tính vào tổng, nhưng tách riêng để thấy ngay. */
    static final String NO_PROJECT = "(Chưa gán dự án)";

    /** Nhãn cho dòng không điền họ tên. */
    static final String NO_NAME = "(Chưa có tên)";

    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SunEffortEngine() {
    }

    // ============================ mô hình dữ liệu ============================

    /** Một dòng chấm công dự án đã chuẩn hoá. */
    public record SunRow(int sheetRow, LocalDate date, String email, String name, String position,
                         String level, String vendor, String project,
                         double totalMd, double expense, double manday) {
    }

    /** Tổng theo nhân sự. */
    public record PersonTotal(String name, double totalMd, double expense) {
    }

    /** Tổng theo dự án. */
    public record ProjectTotal(String project, double totalMd, double expense) {
    }

    /** Chi phí theo cặp nhân sự × dự án; dateRange = "dd/MM/yyyy – dd/MM/yyyy". */
    public record PersonProjectTotal(String dateRange, String email, String position, String level,
                                     String vendor, String project, double totalMd, double expense,
                                     double manday, String name) {
    }

    /** Toàn bộ kết quả một lần chạy. */
    public record SunReport(List<SunRow> raw, List<PersonTotal> byPerson, List<ProjectTotal> byProject,
                            List<PersonProjectTotal> byPersonProject, List<String> warnings) {
    }

    // ============================ COMPUTE ============================

    /** Đọc workbook (đã qua validate) → dựng toàn bộ kết quả. */
    public static SunReport compute(Workbook wb) {
        return compute(ExcelReportEngine.readRows(ReportTemplate.PHAN_BO_CHI_PHI_SUN_ITS, wb));
    }

    /** Tính từ các dòng đã đọc — tách riêng để test thuần không cần file. */
    public static SunReport compute(List<ExcelReportEngine.InputRow> input) {
        List<SunRow> raw = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int mismatches = 0;
        int noProject = 0;

        for (ExcelReportEngine.InputRow ir : input) {
            Map<String, Object> v = ir.values();
            String project = text(v.get("Project Name"));
            if (project.isEmpty()) {
                project = NO_PROJECT;
                noProject++;
            }
            String name = text(v.get("Họ và tên"));
            SunRow row = new SunRow(
                    ir.sheetRow(),
                    (LocalDate) v.get("Date"),
                    text(v.get("Email")),
                    name.isEmpty() ? NO_NAME : name,
                    text(v.get("Position")),
                    text(v.get("Level")),
                    text(v.get("Vendor")),
                    project,
                    number(v.get("Total MD")),
                    number(v.get("Expense (VNĐ)")),
                    number(v.get("Manday (VNĐ)")));
            raw.add(row);

            double expected = row.totalMd() * row.manday();
            if (Math.abs(row.expense() - expected) > EXPENSE_TOLERANCE) {
                mismatches++;
                if (warnings.size() < MAX_WARNINGS) {
                    warnings.add("Dòng " + row.sheetRow() + " (" + row.name() + " · " + row.project() + "): "
                            + "chi phí trong file " + money(row.expense())
                            + " lệch với Total MD × Manday = " + money(expected) + ".");
                }
            }
        }
        if (mismatches > warnings.size()) {
            warnings.add("… và " + (mismatches - warnings.size()) + " dòng lệch chi phí khác.");
        }
        if (noProject > 0) {
            warnings.add(noProject + " dòng không điền tên dự án — đã gộp vào nhóm \"" + NO_PROJECT + "\".");
        }
        warnings.addAll(nameEmailWarnings(raw));

        return new SunReport(raw, byPerson(raw), byProject(raw), byPersonProject(raw), warnings);
    }

    /** Gộp theo nhân sự — MỘT dòng cho mỗi người (xem {@link #personKey}). */
    private static List<PersonTotal> byPerson(List<SunRow> raw) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (SunRow r : raw) {
            String key = personKey(r);
            sums.computeIfAbsent(key, k -> new double[2]);
            sums.get(key)[0] += r.totalMd();
            sums.get(key)[1] += r.expense();
            names.putIfAbsent(key, r.name());
        }
        List<PersonTotal> out = new ArrayList<>();
        sums.forEach((k, s) -> out.add(new PersonTotal(names.get(k), round2(s[0]), round2(s[1]))));
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    /** Gộp theo dự án. */
    private static List<ProjectTotal> byProject(List<SunRow> raw) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (SunRow r : raw) {
            String key = r.project().toLowerCase();
            sums.computeIfAbsent(key, k -> new double[2]);
            sums.get(key)[0] += r.totalMd();
            sums.get(key)[1] += r.expense();
            labels.putIfAbsent(key, r.project());
        }
        List<ProjectTotal> out = new ArrayList<>();
        sums.forEach((k, s) -> out.add(new ProjectTotal(labels.get(k), round2(s[0]), round2(s[1]))));
        out.sort((a, b) -> a.project().compareTo(b.project()));
        return out;
    }

    /**
     * Gộp theo cặp (nhân sự × dự án) — cùng khoá nhân sự với bảng "Tổng theo nhân sự" để hai bảng khớp nhau.
     * Thuộc tính mô tả (Email/Position/Level/Vendor) lấy theo dòng đầu tiên của cặp; Manday là đơn giá bình quân
     * gia quyền (= tổng chi phí / tổng MD) để đúng cả khi đơn giá đổi giữa kỳ.
     */
    private static List<PersonProjectTotal> byPersonProject(List<SunRow> raw) {
        Map<String, PairAccumulator> acc = new LinkedHashMap<>();
        for (SunRow r : raw) {
            acc.computeIfAbsent(personKey(r) + " " + r.project().toLowerCase(),
                    k -> new PairAccumulator(r)).add(r);
        }
        List<PersonProjectTotal> out = new ArrayList<>();
        for (PairAccumulator a : acc.values()) {
            double md = round2(a.totalMd);
            double expense = round2(a.expense);
            double manday = a.totalMd > 0 ? round2(a.expense / a.totalMd) : round2(a.first.manday());
            out.add(new PersonProjectTotal(dateRange(a.from, a.to), a.first.email(), a.first.position(),
                    a.first.level(), a.first.vendor(), a.first.project(), md, expense, manday, a.first.name()));
        }
        out.sort((x, y) -> {
            int c = x.name().compareTo(y.name());
            return c != 0 ? c : x.project().compareTo(y.project());
        });
        return out;
    }

    private static final class PairAccumulator {
        final SunRow first;
        LocalDate from;
        LocalDate to;
        double totalMd;
        double expense;

        PairAccumulator(SunRow first) {
            this.first = first;
        }

        void add(SunRow r) {
            totalMd += r.totalMd();
            expense += r.expense();
            if (r.date() != null) {
                if (from == null || r.date().isBefore(from)) {
                    from = r.date();
                }
                if (to == null || r.date().isAfter(to)) {
                    to = r.date();
                }
            }
        }
    }

    /**
     * Khoá nhân sự = HỌ TÊN đã chuẩn hoá (bỏ khoảng trắng thừa, không phân biệt hoa thường).
     *
     * KHÔNG dùng email làm khoá: bảng chấm công thật có cùng một người bị gõ sai email thành nhiều
     * biến thể (vinhnq3@vmogroup.com / vinhnq3@vmogroup.com.vn / vinnq3@vmogroup.com), khoá theo email
     * thì "Tổng theo nhân sự" tách người đó thành 3 dòng — đúng nghĩa đen nhưng sai điều người dùng cần.
     * Đổi lại, hai người TRÙNG TÊN sẽ bị gộp; {@link #nameEmailWarnings} cảnh báo mọi tên có nhiều email
     * và mọi email gắn nhiều tên để người dùng còn rà lại dữ liệu gốc.
     */
    private static String personKey(SunRow r) {
        String name = normKey(r.name());
        return name.isEmpty() || NO_NAME.equalsIgnoreCase(r.name())
                ? "email:" + normKey(r.email())
                : "name:" + name;
    }

    private static String normKey(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * Soi lệch định danh trong file: một tên gắn nhiều email (gõ sai email) hoặc một email gắn nhiều tên
     * (gõ sai tên). Không chặn tính toán — chỉ nói rõ chỗ nào đáng ngờ.
     */
    private static List<String> nameEmailWarnings(List<SunRow> raw) {
        Map<String, Map<String, String>> emailsOfName = new LinkedHashMap<>();
        Map<String, Map<String, String>> namesOfEmail = new LinkedHashMap<>();
        for (SunRow r : raw) {
            if (r.email().isBlank() || r.name().isBlank()) {
                continue;
            }
            emailsOfName.computeIfAbsent(normKey(r.name()), k -> new LinkedHashMap<>())
                    .putIfAbsent(normKey(r.email()), r.email());
            namesOfEmail.computeIfAbsent(normKey(r.email()), k -> new LinkedHashMap<>())
                    .putIfAbsent(normKey(r.name()), r.name());
        }
        List<String> out = new ArrayList<>();
        for (SunRow r : raw) {
            Map<String, String> emails = emailsOfName.get(normKey(r.name()));
            if (emails != null && emails.size() > 1) {
                String msg = "\"" + r.name() + "\" có " + emails.size() + " email khác nhau trong file ("
                        + String.join(", ", emails.values()) + ") — đã gộp chung theo họ tên, hãy rà lại email.";
                if (!out.contains(msg)) {
                    out.add(msg);
                }
            }
        }
        for (Map.Entry<String, Map<String, String>> e : namesOfEmail.entrySet()) {
            if (e.getValue().size() > 1) {
                out.add("Email " + e.getKey() + " gắn với " + e.getValue().size() + " họ tên khác nhau ("
                        + String.join(", ", e.getValue().values()) + ") — mỗi tên được tính thành một người.");
            }
        }
        return out;
    }

    private static String dateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return "";
        }
        return from.equals(to) ? from.format(DATE_VN) : from.format(DATE_VN) + " – " + to.format(DATE_VN);
    }

    // ============================ KẾT QUẢ CHO MÀN HÌNH ============================

    /** Chuyển sang model trung lập để hiển thị; bảng dài bị cắt còn {@link #MAX_UI_ROWS} dòng (kèm cảnh báo). */
    public static ReportResult toResult(SunReport rep) {
        // Cộng từ dữ liệu GỐC (không phải từ các nhóm đã làm tròn) để tổng khớp đúng con số trong file.
        double totalMd = rep.raw().stream().mapToDouble(SunRow::totalMd).sum();
        double totalExpense = rep.raw().stream().mapToDouble(SunRow::expense).sum();

        List<ReportResult.Metric> metrics = List.of(
                new ReportResult.Metric("Tổng nỗ lực (MD)", decimal(round2(totalMd))),
                new ReportResult.Metric("Tổng chi phí (VNĐ)", money(totalExpense)),
                new ReportResult.Metric("Số nhân sự", String.valueOf(rep.byPerson().size())),
                new ReportResult.Metric("Số dự án", String.valueOf(rep.byProject().size())),
                new ReportResult.Metric("Dòng dữ liệu", String.valueOf(rep.raw().size())));

        List<String> warnings = new ArrayList<>(rep.warnings());

        List<List<Object>> personRows = new ArrayList<>();
        for (PersonTotal p : rep.byPerson()) {
            personRows.add(List.of(p.name(), p.totalMd(), p.expense()));
        }
        List<List<Object>> projectRows = new ArrayList<>();
        for (ProjectTotal p : rep.byProject()) {
            projectRows.add(List.of(p.project(), p.totalMd(), p.expense()));
        }
        List<List<Object>> pairRows = new ArrayList<>();
        for (PersonProjectTotal p : rep.byPersonProject()) {
            pairRows.add(List.of(p.dateRange(), p.email(), p.position(), p.level(), p.vendor(),
                    p.project(), p.totalMd(), p.expense(), p.manday(), p.name()));
        }

        List<ReportResult.Table> tables = List.of(
                new ReportResult.Table("byPerson", "Tổng theo nhân sự",
                        List.of("Họ và tên", "Total MD", "Expense (VNĐ)"),
                        List.of("TEXT", "NUMBER", "MONEY"),
                        cap(personRows, "Tổng theo nhân sự", warnings)),
                new ReportResult.Table("byProject", "Tổng theo dự án",
                        List.of("Project Name", "Total MD", "Expense (VNĐ)"),
                        List.of("TEXT", "NUMBER", "MONEY"),
                        cap(projectRows, "Tổng theo dự án", warnings)),
                new ReportResult.Table("byPersonProject", "Chi phí theo nhân sự dự án",
                        List.of("Date", "Email", "Position", "Level", "Vendor", "Project Name",
                                "Total MD", "Expense (VNĐ)", "Manday (VNĐ)", "Họ và tên"),
                        List.of("TEXT", "TEXT", "TEXT", "TEXT", "TEXT", "TEXT",
                                "NUMBER", "MONEY", "MONEY", "TEXT"),
                        cap(pairRows, "Chi phí theo nhân sự dự án", warnings)));

        return new ReportResult(metrics, tables, warnings);
    }

    private static List<List<Object>> cap(List<List<Object>> rows, String title, List<String> warnings) {
        if (rows.size() <= MAX_UI_ROWS) {
            return rows;
        }
        warnings.add("Bảng \"" + title + "\" có " + rows.size() + " dòng — màn hình chỉ hiển thị "
                + MAX_UI_ROWS + " dòng đầu, hãy tải file .xlsx để xem đầy đủ.");
        return new ArrayList<>(rows.subList(0, MAX_UI_ROWS));
    }

    // ============================ GHI .XLSX ============================

    /** Sinh file kết quả 4 sheet đúng cấu trúc biểu mẫu (raw + 3 bảng tổng hợp). */
    public static byte[] write(SunReport rep) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);

            Sheet raw = wb.createSheet("Raw normalized");
            header(raw, st, "Date", "Email", "Họ và tên", "Position", "Level", "Vendor", "Project Name",
                    "Total MD", "Expense (VNĐ)", "Manday (VNĐ)");
            int r = 1;
            for (SunRow row : rep.raw()) {
                Row dr = raw.createRow(r++);
                dateCell(dr, 0, row.date(), st);
                textCell(dr, 1, row.email());
                textCell(dr, 2, row.name());
                textCell(dr, 3, row.position());
                textCell(dr, 4, row.level());
                textCell(dr, 5, row.vendor());
                textCell(dr, 6, row.project());
                numCell(dr, 7, row.totalMd(), st.number);
                numCell(dr, 8, row.expense(), st.money);
                numCell(dr, 9, row.manday(), st.money);
            }
            autoSize(raw, 10);

            Sheet person = wb.createSheet("Tổng theo nhân sự");
            header(person, st, "Họ và tên", "Total MD", "Expense (VNĐ)");
            r = 1;
            for (PersonTotal p : rep.byPerson()) {
                Row dr = person.createRow(r++);
                textCell(dr, 0, p.name());
                numCell(dr, 1, p.totalMd(), st.number);
                numCell(dr, 2, p.expense(), st.money);
            }
            autoSize(person, 3);

            Sheet project = wb.createSheet("Tổng theo dự án");
            header(project, st, "Project Name", "Total MD", "Expense (VNĐ)");
            r = 1;
            for (ProjectTotal p : rep.byProject()) {
                Row dr = project.createRow(r++);
                textCell(dr, 0, p.project());
                numCell(dr, 1, p.totalMd(), st.number);
                numCell(dr, 2, p.expense(), st.money);
            }
            autoSize(project, 3);

            Sheet pair = wb.createSheet("Chi phí theo nhân sự dự án");
            header(pair, st, "Date", "Email", "Position", "Level", "Vendor", "Project Name",
                    "Total MD", "Expense (VNĐ)", "Manday (VNĐ)", "Họ và tên");
            r = 1;
            for (PersonProjectTotal p : rep.byPersonProject()) {
                Row dr = pair.createRow(r++);
                textCell(dr, 0, p.dateRange());
                textCell(dr, 1, p.email());
                textCell(dr, 2, p.position());
                textCell(dr, 3, p.level());
                textCell(dr, 4, p.vendor());
                textCell(dr, 5, p.project());
                numCell(dr, 6, p.totalMd(), st.number);
                numCell(dr, 7, p.expense(), st.money);
                numCell(dr, 8, p.manday(), st.money);
                textCell(dr, 9, p.name());
            }
            autoSize(pair, 10);

            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không ghi được file kết quả: " + e.getMessage(), e);
        }
    }

    /**
     * Sinh BIỂU MẪU trống để người dùng tải về điền: đủ 4 sheet, sheet dữ liệu có header 12 cột
     * và 2 dòng ví dụ; 3 sheet kết quả chỉ có header (do tool tự điền khi chạy).
     */
    public static byte[] writeSampleTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);

            Sheet raw = wb.createSheet("Raw normalized");
            header(raw, st, "Date", "Email", "Họ và tên", "Position", "Level", "Vendor", "Project Name",
                    "Total MD", "Expense (VNĐ)", "Manday (VNĐ)", "MD nhân sự tự khai",
                    "Thời gian thực hiện (chỉ điền số giờ, không điền ký tự khác)");

            sampleRow(raw, st, 1, LocalDate.of(2026, 7, 1), "sondn3@vmogroup.com", "Đàm Ngọc Sơn",
                    1.0, 2_818_181.82d);
            sampleRow(raw, st, 2, LocalDate.of(2026, 7, 2), "sondn3@vmogroup.com", "Đàm Ngọc Sơn",
                    0.5, 2_818_181.82d);
            autoSize(raw, 12);

            header(wb.createSheet("Tổng theo nhân sự"), st, "Họ và tên", "Total MD", "Expense (VNĐ)");
            header(wb.createSheet("Tổng theo dự án"), st, "Project Name", "Total MD", "Expense (VNĐ)");
            header(wb.createSheet("Chi phí theo nhân sự dự án"), st, "Date", "Email", "Position", "Level",
                    "Vendor", "Project Name", "Total MD", "Expense (VNĐ)", "Manday (VNĐ)", "Họ và tên");

            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được biểu mẫu: " + e.getMessage(), e);
        }
    }

    private static void sampleRow(Sheet sheet, Styles st, int r, LocalDate date, String email, String name,
                                  double md, double manday) {
        Row dr = sheet.createRow(r);
        dateCell(dr, 0, date, st);
        textCell(dr, 1, email);
        textCell(dr, 2, name);
        textCell(dr, 3, "DEV");
        textCell(dr, 4, "Middle");
        textCell(dr, 5, "VMO");
        textCell(dr, 6, "HR Platform");
        numCell(dr, 7, md, st.number);
        numCell(dr, 8, round2(md * manday), st.money);
        numCell(dr, 9, manday, st.money);
        numCell(dr, 10, 1, st.number);
        numCell(dr, 11, 8, st.number);
    }

    // ============================ helpers ghi file ============================

    /** Bộ style dùng lại (POI giới hạn số CellStyle nên KHÔNG tạo style trong vòng lặp). */
    private static final class Styles {
        final CellStyle head;
        final CellStyle date;
        final CellStyle number;
        final CellStyle money;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            head = wb.createCellStyle();
            head.setFont(bold);
            date = wb.createCellStyle();
            date.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            number = wb.createCellStyle();
            number.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.##"));
            money = wb.createCellStyle();
            money.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
        }
    }

    private static void header(Sheet sheet, Styles st, String... headers) {
        Row hr = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            hr.createCell(c).setCellValue(headers[c]);
            hr.getCell(c).setCellStyle(st.head);
        }
    }

    private static void textCell(Row row, int c, String value) {
        row.createCell(c).setCellValue(ExcelReportEngine.sanitize(value));
    }

    private static void numCell(Row row, int c, double value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void dateCell(Row row, int c, LocalDate value, Styles st) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
        if (value != null) {
            cell.setCellValue(java.sql.Date.valueOf(value));
            cell.setCellStyle(st.date);
        }
    }

    private static void autoSize(Sheet sheet, int cols) {
        for (int c = 0; c < cols; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    // ============================ helpers số/chuỗi ============================

    private static String text(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static double number(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0d;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0d) / 100.0d;
    }

    /** Bộ định dạng số kiểu VN (1.234,56) — DecimalFormat không an toàn đa luồng nên tạo mới mỗi lần dùng. */
    private static DecimalFormat viFormat(String pattern) {
        DecimalFormatSymbols vi = new DecimalFormatSymbols(Locale.US);
        vi.setGroupingSeparator('.');
        vi.setDecimalSeparator(',');
        return new DecimalFormat(pattern, vi);
    }

    /** Định dạng tiền kiểu VN: 2.818.181,82 */
    public static String money(double v) {
        return viFormat("#,##0.00").format(v);
    }

    /** Định dạng số MD kiểu VN, bỏ phần thập phân thừa: 1.234,5 */
    public static String decimal(double v) {
        return viFormat("#,##0.##").format(v);
    }
}
