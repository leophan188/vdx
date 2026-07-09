package com.bpm.application;

import com.bpm.domain.process.ProcessDefinition;
import com.bpm.infrastructure.ProcessDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ghi SKELETON sơ đồ chuẩn QT01 (song song Soạn thảo || phối hợp, OR đơn vị/cá nhân, các vòng lặp) vào BẢN
 * NHÁP của quy trình QT01.01 — KHÔNG ban hành, KHÔNG đụng hồ sơ đang chạy. Người dùng mở Designer chỉnh
 * layout + hoàn thiện điều kiện/người thực hiện rồi ban hành. Chỉ áp 1 lần (marker Process_qt0101_v2).
 */
@Component
@Order(50)
public class Qt0101RedesignRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Qt0101RedesignRunner.class);
    private static final String QT01_KEY = "qt01-01-tham-gia-y-kien";
    private static final String MARKER = "Process_qt0101_v2";

    private final ProcessDefinitionRepository repo;
    private final ProcessService processService;

    public Qt0101RedesignRunner(ProcessDefinitionRepository repo, ProcessService processService) {
        this.repo = repo;
        this.processService = processService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ProcessDefinition p = repo.findAll().stream()
                    .filter(x -> QT01_KEY.equals(x.getProcessKey())).findFirst().orElse(null);
            if (p == null) {
                return; // chưa seed QT01 → bỏ qua
            }
            if (p.getBpmnXml() != null && p.getBpmnXml().contains(MARKER)) {
                return; // đã áp skeleton v2 → không ghi đè lần nữa
            }
            processService.saveDesign(p.getId(), buildBpmn(), buildMeta(), "system");
            log.info("[Qt0101Redesign] Đã ghi SKELETON sơ đồ chuẩn vào BẢN NHÁP QT01.01 — mở Designer để chỉnh layout + ban hành.");
        } catch (Exception e) {
            log.warn("[Qt0101Redesign] Không ghi được skeleton QT01: {}", e.getMessage());
        }
    }

    // ---- Mô tả node/flow (id, loại, tên, lane, cột) → sinh BPMN + DI theo lưới ----
    private record N(String id, String kind, String name, int lane, int col) {}
    private record F(String id, String src, String tgt) {}

    /** lane: 0=Soạn thảo(trên), 1=chính, 2=đơn vị, 3=cá nhân. */
    private static final int[] LANE_Y = {110, 320, 500, 660};

    private List<N> nodes() {
        List<N> n = new ArrayList<>();
        n.add(new N("StartEvent_1", "start", "Bắt đầu", 1, 0));
        n.add(new N("Task_01", "task", "Tạo nhiệm vụ / Nghiên cứu, tham mưu", 1, 1));
        n.add(new N("Task_02", "task", "Phê duyệt nhiệm vụ", 1, 2));
        n.add(new N("GW_appr", "xor", "Đồng ý?", 1, 3));
        n.add(new N("GW_split", "par", "Tách song song", 1, 4));
        n.add(new N("Task_03", "task", "Soạn dự thảo", 0, 5));
        n.add(new N("Task_04", "task", "Xác định nhu cầu phối hợp", 1, 5));
        n.add(new N("GW_coord", "inc", "Hình thức phối hợp?", 1, 6));
        n.add(new N("Task_05", "task", "Phân công chuyên viên", 2, 7));
        n.add(new N("Task_06", "task", "Tham gia ý kiến, góp ý", 2, 8));
        n.add(new N("Task_07", "task", "Phê duyệt, chuyển về chủ trì", 2, 9));
        n.add(new N("Task_08", "task", "Tham gia ý kiến, gửi về chủ trì", 3, 7));
        n.add(new N("GW_cjoin", "inc", "Hội tụ phối hợp", 2, 10));
        n.add(new N("Task_09", "task", "Tiếp thu ý kiến, góp ý", 2, 11));
        n.add(new N("GW_absorb", "xor", "Tiếp thu?", 2, 12));
        n.add(new N("Task_10", "task", "Giải trình vào bảng tổng hợp ý kiến", 3, 11));
        n.add(new N("GW_join", "par", "Hội tụ", 1, 13));
        n.add(new N("Task_11", "task", "Điều chỉnh dự thảo nội dung tham mưu", 1, 14));
        n.add(new N("Task_12", "task", "Kiểm tra, phê duyệt dự thảo", 1, 15));
        n.add(new N("GW_dappr", "xor", "Đồng ý?", 1, 16));
        n.add(new N("Task_13", "task", "Chuyển trình ký", 1, 17));
        n.add(new N("Task_14", "task", "Ký duyệt, ban hành văn bản", 1, 18));
        n.add(new N("Task_15", "task", "Đóng hồ sơ & lưu trữ", 1, 19));
        n.add(new N("Task_16", "task", "Thông báo kết thúc", 1, 20));
        n.add(new N("EndEvent_1", "end", "Kết thúc", 1, 21));
        return n;
    }

    private List<F> flows() {
        List<F> f = new ArrayList<>();
        f.add(new F("f_start", "StartEvent_1", "Task_01"));
        f.add(new F("f01", "Task_01", "Task_02"));
        f.add(new F("f02", "Task_02", "GW_appr"));
        f.add(new F("Flow_reject", "GW_appr", "Task_01"));
        f.add(new F("Flow_appr", "GW_appr", "GW_split"));
        f.add(new F("f_split_draft", "GW_split", "Task_03"));
        f.add(new F("f_split_coord", "GW_split", "Task_04"));
        f.add(new F("f04", "Task_04", "GW_coord"));
        f.add(new F("Flow_donvi", "GW_coord", "Task_05"));
        f.add(new F("Flow_canhan", "GW_coord", "Task_08"));
        f.add(new F("Flow_nocoord", "GW_coord", "GW_cjoin"));
        f.add(new F("f05", "Task_05", "Task_06"));
        f.add(new F("f06", "Task_06", "Task_07"));
        f.add(new F("f07", "Task_07", "GW_cjoin"));
        f.add(new F("f08", "Task_08", "GW_cjoin"));
        f.add(new F("f_cjoin", "GW_cjoin", "Task_09"));
        f.add(new F("f09", "Task_09", "GW_absorb"));
        f.add(new F("Flow_tiepthu", "GW_absorb", "GW_join"));
        f.add(new F("Flow_giaitrinh", "GW_absorb", "Task_10"));
        f.add(new F("f10", "Task_10", "Task_09"));
        f.add(new F("f_draft_join", "Task_03", "GW_join"));
        f.add(new F("f_join", "GW_join", "Task_11"));
        f.add(new F("f11", "Task_11", "Task_12"));
        f.add(new F("f12", "Task_12", "GW_dappr"));
        f.add(new F("Flow_return", "GW_dappr", "Task_11"));
        f.add(new F("Flow_dok", "GW_dappr", "Task_13"));
        f.add(new F("f13", "Task_13", "Task_14"));
        f.add(new F("f14", "Task_14", "Task_15"));
        f.add(new F("f15", "Task_15", "Task_16"));
        f.add(new F("f16", "Task_16", "EndEvent_1"));
        return f;
    }

    /** Cổng có default (else) để luồng không kẹt khi điều kiện không thoả. */
    private static final Map<String, String> GW_DEFAULT = Map.of(
            "GW_appr", "Flow_appr", "GW_coord", "Flow_nocoord", "GW_absorb", "Flow_tiepthu", "GW_dappr", "Flow_dok");

    private String buildBpmn() {
        List<N> ns = nodes();
        List<F> fs = flows();
        Map<String, List<String>> incoming = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (F f : fs) {
            outgoing.computeIfAbsent(f.src(), k -> new ArrayList<>()).add(f.id());
            incoming.computeIfAbsent(f.tgt(), k -> new ArrayList<>()).add(f.id());
        }
        StringBuilder proc = new StringBuilder();
        proc.append("<bpmn:process id=\"").append(MARKER).append("\" name=\"QT01.01 – Tạo và xử lý nhiệm vụ (sơ đồ chuẩn)\" isExecutable=\"true\">");
        for (N n : ns) {
            String tag = switch (n.kind()) {
                case "start" -> "startEvent";
                case "end" -> "endEvent";
                case "task" -> "userTask";
                case "xor" -> "exclusiveGateway";
                case "par" -> "parallelGateway";
                case "inc" -> "inclusiveGateway";
                default -> "task";
            };
            proc.append("<bpmn:").append(tag).append(" id=\"").append(n.id()).append("\" name=\"").append(xml(n.name())).append("\"");
            if (GW_DEFAULT.containsKey(n.id())) {
                proc.append(" default=\"").append(GW_DEFAULT.get(n.id())).append("\"");
            }
            proc.append(">");
            for (String in : incoming.getOrDefault(n.id(), List.of())) {
                proc.append("<bpmn:incoming>").append(in).append("</bpmn:incoming>");
            }
            for (String out : outgoing.getOrDefault(n.id(), List.of())) {
                proc.append("<bpmn:outgoing>").append(out).append("</bpmn:outgoing>");
            }
            proc.append("</bpmn:").append(tag).append(">");
        }
        for (F f : fs) {
            proc.append("<bpmn:sequenceFlow id=\"").append(f.id()).append("\" sourceRef=\"").append(f.src())
                    .append("\" targetRef=\"").append(f.tgt()).append("\" />");
        }
        proc.append("</bpmn:process>");

        // DI: shape theo lưới (col*colW), edge nối tâm.
        int colW = 190;
        Map<String, int[]> box = new LinkedHashMap<>(); // id -> {x,y,w,h}
        StringBuilder di = new StringBuilder();
        for (N n : ns) {
            int cx = 120 + n.col() * colW, cy = LANE_Y[n.lane()];
            int w = n.kind().equals("task") ? 150 : (n.kind().equals("start") || n.kind().equals("end") ? 36 : 50);
            int h = n.kind().equals("task") ? 80 : (n.kind().equals("start") || n.kind().equals("end") ? 36 : 50);
            int x = cx - w / 2, y = cy - h / 2;
            box.put(n.id(), new int[]{x, y, w, h});
            di.append("<bpmndi:BPMNShape id=\"").append(n.id()).append("_di\" bpmnElement=\"").append(n.id())
                    .append("\"><dc:Bounds x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"").append(w)
                    .append("\" height=\"").append(h).append("\" /></bpmndi:BPMNShape>");
        }
        for (F f : fs) {
            int[] a = box.get(f.src()), b = box.get(f.tgt());
            int x1 = a[0] + a[2] / 2, y1 = a[1] + a[3] / 2, x2 = b[0] + b[2] / 2, y2 = b[1] + b[3] / 2;
            di.append("<bpmndi:BPMNEdge id=\"").append(f.id()).append("_di\" bpmnElement=\"").append(f.id())
                    .append("\"><di:waypoint x=\"").append(x1).append("\" y=\"").append(y1)
                    .append("\" /><di:waypoint x=\"").append(x2).append("\" y=\"").append(y2).append("\" /></bpmndi:BPMNEdge>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                + " xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"Definitions_qt0101_v2\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + proc
                + "<bpmndi:BPMNDiagram id=\"BPMNDiagram_1\"><bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"" + MARKER + "\">"
                + di + "</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>";
    }

    /**
     * stepsMeta skeleton: mỗi userTask có actions + statusLabel (assignee để người dùng cấu hình trong Designer).
     * Demo assignee ĐỘNG cho nhánh cá nhân (Task_08 → theo trường ca_nhan_phoi_hop). Điều kiện nhánh gắn sẵn:
     * Flow_donvi/Flow_canhan = trường phối hợp CÓ DỮ LIỆU; Flow_reject/Flow_return = theo hành động.
     */
    private String buildMeta() {
        StringBuilder m = new StringBuilder("{");
        // userTasks
        String[][] tasks = {
                {"Task_01", "Tạo nhiệm vụ"}, {"Task_02", "Phê duyệt nhiệm vụ"}, {"Task_03", "Soạn dự thảo"},
                {"Task_04", "Xác định nhu cầu phối hợp"}, {"Task_05", "Phân công chuyên viên"},
                {"Task_06", "Tham gia ý kiến (đơn vị)"}, {"Task_07", "Phê duyệt, chuyển chủ trì"},
                {"Task_08", "Tham gia ý kiến (cá nhân)"}, {"Task_09", "Tiếp thu ý kiến"},
                {"Task_10", "Giải trình tổng hợp ý kiến"}, {"Task_11", "Điều chỉnh dự thảo"},
                {"Task_12", "Kiểm tra, phê duyệt dự thảo"}, {"Task_13", "Chuyển trình ký"},
                {"Task_14", "Ký, ban hành"}, {"Task_15", "Đóng hồ sơ, lưu trữ"}, {"Task_16", "Thông báo kết thúc"}
        };
        List<String> parts = new ArrayList<>();
        for (String[] t : tasks) {
            String extra = "Task_08".equals(t[0])
                    ? ",\"assigneeType\":\"FIELD\",\"assigneeFieldKey\":\"ca_nhan_phoi_hop\""
                    : ",\"assigneeType\":\"POSITION\"";
            parts.add("\"" + t[0] + "\":{\"name\":\"" + esc(t[1]) + "\",\"statusLabel\":\"" + esc(t[1])
                    + "\",\"actions\":[\"SUBMIT\"]" + extra + ",\"slaHours\":8}");
        }
        // Điều kiện nhánh (op notEmpty cho bảng/trường; theo hành động cho vòng lặp)
        parts.add("\"Flow_donvi\":{\"condition\":{\"field\":\"don_vi_phoi_hop\",\"op\":\"notEmpty\"}}");
        parts.add("\"Flow_canhan\":{\"condition\":{\"field\":\"ca_nhan_phoi_hop\",\"op\":\"notEmpty\"}}");
        parts.add("\"Flow_reject\":{\"condition\":{\"field\":\"lastAction\",\"op\":\"eq\",\"value\":\"REJECT\"}}");
        parts.add("\"Flow_return\":{\"condition\":{\"field\":\"lastAction\",\"op\":\"eq\",\"value\":\"RETURN\"}}");
        parts.add("\"Flow_giaitrinh\":{\"condition\":{\"field\":\"lastAction\",\"op\":\"ne\",\"value\":\"APPROVE\"}}");
        m.append(String.join(",", parts)).append("}");
        return m.toString();
    }

    private static String xml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
