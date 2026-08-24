package com.bpm;

import com.bpm.api.dto.ProjectDto;
import com.bpm.application.ProjectReportExportService;
import com.bpm.application.ProjectService;
import com.bpm.application.ProjectTaskService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Xuất Excel Backlog phải khớp đúng những gì đang hiển thị trên lưới. */
@SpringBootTest
@ActiveProfiles("test")
class BacklogExcelExportTest {

    /** Dòng dữ liệu đầu tiên: 3 dòng tiêu đề + 1 dòng trống + 1 dòng header. */
    private static final int FIRST_DATA_ROW = 5;
    private static final int COL_STT = 0;
    private static final int COL_NAME = 2;
    private static final int COL_EST = 5;

    @Autowired ProjectService projectService;
    @Autowired ProjectTaskService taskService;
    @Autowired ProjectReportExportService exportService;

    private String projectId;

    private ProjectDto.TaskResponse task(String type, String parentId, String title, Double est) {
        return taskService.create(projectId, new ProjectDto.TaskRequest(
                parentId, title, null, type, null, null, null, est, null, null,
                null, null, null, null, null, null, null, null, null, null, null), "tester");
    }

    /** Epic → Story → Task → Sub-task cấp 1 → Sub-task cấp 2 (mỗi lá est 1h). */
    private List<ProjectDto.TaskResponse> buildTree(String code) {
        projectId = projectService.create(new ProjectDto.ProjectRequest(
                code, "Dự án " + code, null, null, null, null, null, null, null), "tester").id();
        var epic = task("EPIC", null, "Epic A", null);
        var story = task("STORY", epic.id(), "Story A", null);
        var parent = task("TASK", story.id(), "Task A", 1.0);
        var sub1 = task("SUBTASK", parent.id(), "Sub-task cấp 1", 1.0);
        task("SUBTASK", sub1.id(), "Sub-task cấp 2", 1.0);
        return taskService.list(projectId);
    }

    private static List<Row> dataRows(byte[] xlsx) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sh = wb.getSheetAt(0);
            List<Row> out = new ArrayList<>();
            for (int r = FIRST_DATA_ROW; r <= sh.getLastRowNum(); r++) {
                if (sh.getRow(r) != null) {
                    out.add(sh.getRow(r));
                }
            }
            return out;
        }
    }

    /** Tiêu đề không được dính ký tự "›" — trước đây chỉ dòng CÓ CON mới có, nhìn như lỗi hiển thị. */
    @Test
    void titleHasNoArrowPrefix() throws Exception {
        List<ProjectDto.TaskResponse> all = buildTree("EXP1");

        List<Row> rows = dataRows(exportService.backlogXlsx("Dự án", all, all));

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            String name = r.getCell(COL_NAME).getStringCellValue();
            assertThat(name).doesNotContain("›");
            assertThat(name).isEqualTo(name.trim());   // không chèn khoảng trắng thụt lề vào chữ
        });
    }

    /** Thụt lề dùng indent thật của Excel và tăng dần theo cấp, kể cả sub-task lồng sub-task. */
    @Test
    void indentGrowsWithDepthIncludingNestedSubtask() throws Exception {
        List<ProjectDto.TaskResponse> all = buildTree("EXP2");

        List<Row> rows = dataRows(exportService.backlogXlsx("Dự án", all, all));

        // Epic → Story → Task → Sub1 → Sub2 nằm liên tiếp, mỗi cấp thụt sâu hơn cấp trên
        int prev = -1;
        for (Row r : rows) {
            int indent = r.getCell(COL_NAME).getCellStyle().getIndention();
            assertThat(indent).isGreaterThan(prev);
            prev = indent;
        }
        assertThat(prev).isEqualTo(8);   // cấp 4 (Sub-task cấp 2) → 4 × 2 nấc
    }

    /** STT: Epic = A, B… · Story đánh lại từ 1 trong mỗi Epic · cấp dưới = cha + "." + thứ tự. */
    @Test
    void sttNumbersByLevel() throws Exception {
        List<ProjectDto.TaskResponse> all = buildTree("EXP4");
        // Epic thứ hai có Story riêng: số Story phải quay lại 1, và con của nó không lẫn số với Epic A
        var epic2 = task("EPIC", null, "Epic B", null);
        var story2 = task("STORY", epic2.id(), "Story B1", null);
        task("TASK", story2.id(), "Task B1-1", 1.0);
        all = taskService.list(projectId);

        List<Row> rows = dataRows(exportService.backlogXlsx("Dự án", all, all));
        java.util.Map<String, String> stt = new java.util.LinkedHashMap<>();
        for (Row r : rows) {
            stt.put(r.getCell(COL_NAME).getStringCellValue(), r.getCell(COL_STT).getStringCellValue());
        }

        assertThat(stt.get("Epic A")).isEqualTo("A");
        assertThat(stt.get("Story A")).isEqualTo("1");
        assertThat(stt.get("Task A")).isEqualTo("1.1");
        assertThat(stt.get("Sub-task cấp 1")).isEqualTo("1.1.1");
        assertThat(stt.get("Sub-task cấp 2")).isEqualTo("1.1.1.1");
        assertThat(stt.get("Epic B")).isEqualTo("B");
        assertThat(stt.get("Story B1")).isEqualTo("1");      // đánh lại từ 1 trong Epic B
        // "1.1" của Epic B là dòng ĐẦU của Story đó, không được tiếp tục đếm từ Epic A
        assertThat(stt.get("Task B1-1")).isEqualTo("1.1");
    }

    /** Nhiều con cùng cấp thì thứ tự tăng dần: 1.1, 1.2, 1.3. */
    @Test
    void sttIncrementsAmongSiblings() throws Exception {
        buildTree("EXP5");
        var story = taskService.list(projectId).stream()
                .filter(t -> "Story A".equals(t.title())).findFirst().orElseThrow();
        task("TASK", story.id(), "Task thứ hai", 1.0);
        task("TASK", story.id(), "Task thứ ba", 1.0);
        List<ProjectDto.TaskResponse> all = taskService.list(projectId);

        List<Row> rows = dataRows(exportService.backlogXlsx("Dự án", all, all));
        java.util.Map<String, String> stt = new java.util.LinkedHashMap<>();
        for (Row r : rows) {
            stt.put(r.getCell(COL_NAME).getStringCellValue(), r.getCell(COL_STT).getStringCellValue());
        }

        assertThat(stt.get("Task A")).isEqualTo("1.1");
        assertThat(stt.get("Task thứ hai")).isEqualTo("1.2");
        assertThat(stt.get("Task thứ ba")).isEqualTo("1.3");
    }

    /**
     * Gập một nhóm trên lưới thì file chỉ có dòng nhóm đó, KHÔNG kèm con —
     * nhưng est của nhóm vẫn phải là est tổng hợp từ các con bị ẩn.
     */
    @Test
    void collapsedGroupExportsWithoutChildrenButKeepsRolledUpEstimate() throws Exception {
        List<ProjectDto.TaskResponse> all = buildTree("EXP3");
        // giả lập người dùng gập "Sub-task cấp 1" → lưới không còn hiện "Sub-task cấp 2"
        List<ProjectDto.TaskResponse> visible = all.stream()
                .filter(t -> !"Sub-task cấp 2".equals(t.title())).toList();

        List<Row> rows = dataRows(exportService.backlogXlsx("Dự án", visible, all));

        assertThat(rows).hasSize(4);
        assertThat(rows).noneSatisfy(r ->
                assertThat(r.getCell(COL_NAME).getStringCellValue()).isEqualTo("Sub-task cấp 2"));

        Row sub1 = rows.stream()
                .filter(r -> "Sub-task cấp 1".equals(r.getCell(COL_NAME).getStringCellValue()))
                .findFirst().orElseThrow();
        // vẫn là nhóm (có con trong dự án) → est tổng hợp từ con bị ẩn, không được để trống
        assertThat(sub1.getCell(COL_EST).getStringCellValue()).isEqualTo("1");
    }
}
