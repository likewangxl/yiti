package com.bank.branch.platform.report.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大屏数据源 config_json schemaVersion 读时兼容适配器单测（spec 2026-07-17 §3.4）.
 *
 * <p>旧数据无 schemaVersion 视为 1；已有版本原样保留。适配集中在唯一入口，勿散落。
 */
class ScreenConfigSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void withDefaults_missingSchemaVersion_defaultsTo1() throws Exception {
        JsonNode adapted = ScreenConfigSchema.withDefaults(
                objectMapper.readTree("{\"table\":\"EMP_INDEX_RESULT\"}"));
        assertThat(adapted.path("schemaVersion").asInt()).isEqualTo(1);
        // 其余字段不受影响
        assertThat(adapted.path("table").asText()).isEqualTo("EMP_INDEX_RESULT");
    }

    @Test
    void withDefaults_existingSchemaVersion_untouched() throws Exception {
        JsonNode adapted = ScreenConfigSchema.withDefaults(
                objectMapper.readTree("{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\"}"));
        assertThat(adapted.path("schemaVersion").asInt()).isEqualTo(2);
    }

    /** spec 2026-07-17 §4：scopeMode 缺省补 SUBJECT（历史数据零迁移，读时兼容）. */
    @Test
    void withDefaults_missingScopeMode_defaultsToSubject() throws Exception {
        JsonNode adapted = ScreenConfigSchema.withDefaults(
                objectMapper.readTree("{\"table\":\"EMP_INDEX_RESULT\"}"));
        assertThat(adapted.path("scopeMode").asText()).isEqualTo("SUBJECT");
    }

    @Test
    void withDefaults_existingScopeModeGlobal_untouched() throws Exception {
        JsonNode adapted = ScreenConfigSchema.withDefaults(
                objectMapper.readTree("{\"scopeMode\":\"GLOBAL\",\"sql\":\"SELECT 1\"}"));
        assertThat(adapted.path("scopeMode").asText()).isEqualTo("GLOBAL");
    }
}
