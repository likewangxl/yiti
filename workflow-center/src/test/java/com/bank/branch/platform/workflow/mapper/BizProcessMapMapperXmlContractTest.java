package com.bank.branch.platform.workflow.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BIZ_PROCESS_MAP 自定义 SQL 合同测试。
 *
 * <p>这些断言不连接数据库，专门防止 Mapper XML 回退到会触发唯一键冲突的 INSERT
 * 或遗漏重提时所需的显式 NULL 清理。</p>
 */
class BizProcessMapMapperXmlContractTest {

    @Test
    void restartQueryLocksExistingBusinessKeyAndUpdateOnlyAllowsTerminalStatus() throws IOException {
        String xml = readXml();
        String normalized = compact(xml).toUpperCase(Locale.ROOT);

        assertThat(normalized)
                .contains("ID=\"SELECTFORUPDATEBYBUSINESSKEY\"")
                .contains("WHERE BUSINESS_KEY = #{BUSINESSKEY}")
                .contains("FOR UPDATE");

        String restart = updateSection(xml, "updateForRestart");
        String normalizedRestart = compact(restart).toUpperCase(Locale.ROOT);
        assertThat(normalizedRestart)
                .contains("CURRENT_ASSIGNEE = NULL")
                .contains("CANDIDATE_GROUPS = NULL")
                .contains("END_TIME = NULL")
                .contains("PROCESS_STATUS IN ('COMPLETED', 'CANCELLED')");
    }

    private String readXml() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/mapper/workflow/BizProcessMapMapper.xml")) {
            assertThat(input).as("BizProcessMapMapper.xml must be on test classpath").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String updateSection(String xml, String id) {
        String startToken = "<update id=\"" + id + "\"";
        int start = xml.indexOf(startToken);
        assertThat(start).as("missing update %s", id).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</update>", start);
        assertThat(end).as("missing closing update %s", id).isGreaterThan(start);
        return xml.substring(start, end);
    }

    private String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
