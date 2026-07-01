package com.bpm;

import com.bpm.application.FormService;
import com.bpm.domain.form.FormDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FormServiceTest {

    @Autowired FormService formService;

    @Test
    void create_saveSchema_persistsJson() {
        FormDefinition f = formService.create("phieu-trinh", "Phiếu trình hồ sơ", "test");
        String schema = "{\"fields\":[{\"key\":\"tieu_de\",\"label\":\"Tiêu đề\",\"type\":\"text\",\"required\":true}]}";
        formService.saveSchema(f.getId(), schema, "test");
        assertThat(formService.get(f.getId()).getSchemaJson()).contains("tieu_de");
    }

    @Test
    void duplicateKey_rejected() {
        formService.create("dup-form", "A", "test");
        assertThatThrownBy(() -> formService.create("dup-form", "B", "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publish_snapshotsImmutable_republishIncrements_retire() {
        FormDefinition f = formService.create("pub-form", "Form ban hành", "test");
        formService.saveSchema(f.getId(), "{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}", "test");
        var v1 = formService.publish(f.getId(), "test");
        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(formService.get(f.getId()).getPublishedVersion()).isEqualTo(1);

        formService.saveSchema(f.getId(), "{\"fields\":[{\"key\":\"a\",\"type\":\"number\"}]}", "test");
        var v2 = formService.publish(f.getId(), "test");
        assertThat(v2.getVersion()).isEqualTo(2);
        var versions = formService.listVersions(f.getId());
        assertThat(versions).hasSize(2);
        var snap1 = versions.stream().filter(x -> x.getVersion() == 1).findFirst().orElseThrow();
        assertThat(snap1.getSchemaJson()).contains("\"type\":\"text\""); // bất biến

        formService.retire(f.getId(), "test");
        assertThat(formService.get(f.getId()).getStatus().name()).isEqualTo("RETIRED");
    }

    @Test
    void publish_withoutSchema_blocked() {
        FormDefinition empty = formService.create("empty-form", "Trống", "test");
        assertThatThrownBy(() -> formService.publish(empty.getId(), "test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rename_and_delete() {
        FormDefinition f = formService.create("to-edit-form", "Cũ", "test");
        formService.rename(f.getId(), "Mới", "test");
        assertThat(formService.get(f.getId()).getName()).isEqualTo("Mới");
        formService.delete(f.getId(), "test");
        assertThat(formService.list().stream().noneMatch(x -> x.getId().equals(f.getId()))).isTrue();
    }
}
