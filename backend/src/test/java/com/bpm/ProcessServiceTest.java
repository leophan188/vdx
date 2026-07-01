package com.bpm;

import com.bpm.application.ProcessService;
import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.process.ProcessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProcessServiceTest {

    @Autowired ProcessService processService;

    @Test
    void create_thenSaveDesign_persistsXmlAndMeta() {
        ProcessDefinition p = processService.create("mau-1", "Quy trình mẫu #1", "test");
        assertThat(p.getStatus()).isEqualTo(ProcessStatus.DRAFT);
        assertThat(p.getVersion()).isEqualTo(1);

        String xml = "<?xml version=\"1.0\"?><definitions/>";
        String meta = "{\"Task_1\":{\"position\":\"p1\",\"slaHours\":24,\"actions\":[\"APPROVE\"]}}";
        processService.saveDesign(p.getId(), xml, meta, "test");

        ProcessDefinition reloaded = processService.get(p.getId());
        assertThat(reloaded.getBpmnXml()).isEqualTo(xml);
        assertThat(reloaded.getStepsMetaJson()).contains("slaHours");
    }

    @Test
    void duplicateKey_isRejected() {
        processService.create("dup-key", "A", "test");
        assertThatThrownBy(() -> processService.create("dup-key", "B", "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publish_snapshotsImmutableVersion_republishIncrements() {
        ProcessDefinition p = processService.create("pub-1", "QT publish", "test");
        processService.saveDesign(p.getId(), "<v1/>", "{\"a\":1}", "test");
        var v1 = processService.publish(p.getId(), "test");
        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(processService.get(p.getId()).getStatus()).isEqualTo(ProcessStatus.PUBLISHED);
        assertThat(processService.get(p.getId()).getPublishedVersion()).isEqualTo(1);

        // sửa bản nháp rồi ban hành lại → phiên bản 2; phiên bản 1 snapshot GIỮ NGUYÊN nội dung cũ
        processService.saveDesign(p.getId(), "<v2/>", "{\"a\":2}", "test");
        var v2 = processService.publish(p.getId(), "test");
        assertThat(v2.getVersion()).isEqualTo(2);
        var versions = processService.listVersions(p.getId());
        assertThat(versions).hasSize(2);
        var snap1 = versions.stream().filter(x -> x.getVersion() == 1).findFirst().orElseThrow();
        assertThat(snap1.getBpmnXml()).isEqualTo("<v1/>"); // bất biến
    }

    @Test
    void publish_withoutDesign_isBlocked_and_retire() {
        ProcessDefinition empty = processService.create("pub-empty", "Trống", "test");
        assertThatThrownBy(() -> processService.publish(empty.getId(), "test"))
                .isInstanceOf(IllegalStateException.class);

        ProcessDefinition p = processService.create("pub-2", "QT retire", "test");
        processService.saveDesign(p.getId(), "<x/>", null, "test");
        processService.publish(p.getId(), "test");
        processService.retire(p.getId(), "test");
        assertThat(processService.get(p.getId()).getStatus()).isEqualTo(ProcessStatus.RETIRED);
    }

    @Test
    void rename_and_delete() {
        ProcessDefinition p = processService.create("to-edit", "Cũ", "test");
        processService.rename(p.getId(), "Mới", "test");
        assertThat(processService.get(p.getId()).getName()).isEqualTo("Mới");

        processService.delete(p.getId(), "test");
        assertThat(processService.list().stream().noneMatch(x -> x.getId().equals(p.getId()))).isTrue();
    }
}
