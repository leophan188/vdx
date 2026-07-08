package com.bpm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.InputSource;

/**
 * Tiêm `conditionExpression` (JUEL) vào các nhánh rẽ của BPMN lúc deploy, từ `stepsMeta[flowId].condition`
 * (Story 3.5b). Chỉ tiêm trên sequenceFlow XUẤT PHÁT TỪ exclusive/inclusive gateway để tránh kẹt token ở
 * luồng thường. Biến điều kiện = tên trường form (đã là biến tiến trình khi start/complete).
 */
@Component
public class BpmnConditionInjector {

    private static final Logger log = LoggerFactory.getLogger(BpmnConditionInjector.class);
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private final ObjectMapper objectMapper;

    public BpmnConditionInjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String inject(String bpmnXml, String stepsMetaJson) {
        if (bpmnXml == null || stepsMetaJson == null || stepsMetaJson.isBlank()) {
            return bpmnXml;
        }
        JsonNode meta;
        try {
            meta = objectMapper.readTree(stepsMetaJson);
        } catch (Exception e) {
            return bpmnXml;
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(bpmnXml)));

            Set<String> gatewayIds = collectGatewayIds(doc);
            if (gatewayIds.isEmpty()) {
                return bpmnXml; // không có rẽ nhánh → không cần tiêm
            }

            NodeList flows = doc.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");
            boolean changed = false;
            for (int i = 0; i < flows.getLength(); i++) {
                Element flow = (Element) flows.item(i);
                String id = flow.getAttribute("id");
                String sourceRef = flow.getAttribute("sourceRef");
                JsonNode step = meta.get(id);
                if (step == null || !step.has("condition") || step.get("condition").isNull()) {
                    continue;
                }
                if (!gatewayIds.contains(sourceRef)) {
                    log.debug("[cond] bỏ qua flow {} — nguồn {} không phải gateway", id, sourceRef);
                    continue;
                }
                if (hasConditionChild(flow)) {
                    continue;
                }
                String expr = toExpression(step.get("condition"));
                if (expr == null) {
                    continue;
                }
                String prefix = flow.getPrefix();
                String qn = (prefix != null ? prefix + ":" : "") + "conditionExpression";
                Element ce = doc.createElementNS(BPMN_NS, qn);
                ce.setAttributeNS(XSI_NS, "xsi:type", (prefix != null ? prefix + ":" : "") + "tFormalExpression");
                ce.setTextContent(expr);
                flow.appendChild(ce);
                changed = true;
                log.debug("[cond] flow {} ← {}", id, expr);
            }
            if (!changed) {
                return bpmnXml;
            }
            return serialize(doc);
        } catch (Exception e) {
            log.warn("[cond] không tiêm được điều kiện, dùng BPMN gốc: {}", e.getMessage());
            return bpmnXml;
        }
    }

    private Set<String> collectGatewayIds(Document doc) {
        Set<String> ids = new HashSet<>();
        for (String type : new String[]{"exclusiveGateway", "inclusiveGateway"}) {
            NodeList gs = doc.getElementsByTagNameNS(BPMN_NS, type);
            for (int i = 0; i < gs.getLength(); i++) {
                String id = ((Element) gs.item(i)).getAttribute("id");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private boolean hasConditionChild(Element flow) {
        NodeList kids = flow.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "conditionExpression".equals(n.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    /** {field, op, value} → biểu thức JUEL. op: truthy | eq | ne. */
    private String toExpression(JsonNode cond) {
        String field = cond.path("field").asText(null);
        if (field == null || field.isBlank()) {
            return null;
        }
        String op = cond.path("op").asText("truthy");
        String value = cond.path("value").asText("");
        String v = "'" + value.replace("'", "\\'") + "'";
        // Truy biến qua execution.getVariable(...) thay vì tham chiếu trực tiếp: khi TRƯỜNG ĐIỀU KIỆN
        // CHƯA ĐƯỢC NHẬP thì biến chưa tồn tại — tham chiếu trực tiếp làm JUEL ném
        // PropertyNotFoundException khiến hoàn thành việc lỗi. getVariable trả null → nhánh coi như KHÔNG thoả.
        String var = "execution.getVariable('" + field.replace("\\", "\\\\").replace("'", "\\'") + "')";
        return switch (op) {
            case "eq" -> "${" + var + " == " + v + "}";
            case "ne" -> "${" + var + " != " + v + "}";
            // "có giá trị": đúng cho boolean true + chuỗi không rỗng; sai cho false/rỗng/null (kể cả khi biến chưa tồn tại)
            default -> "${" + var + " == true || (" + var + " != null && " + var + " != '')}";
        };
    }

    private String serialize(Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
