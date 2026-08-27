package com.bank.branch.platform.customer.marketing.lead;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MARKETING_LEAD_INFO 自定义 SQL 合同测试，不连接数据库。
 */
class MarketingLeadInfoMapperXmlContractTest {

    @Test
    void rejectedDraftClearSqlExplicitlyClearsAllApprovalMetadata() throws IOException {
        String xml = readXml();
        String clear = updateSection(xml, "clearApprovalMetadataForDraft");
        String normalized = compact(clear).toUpperCase(Locale.ROOT);

        assertThat(normalized)
                .contains("SUBMITTED_BY = NULL")
                .contains("SUBMITTED_TIME = NULL")
                .contains("BUSINESS_KEY = NULL")
                .contains("PROCESS_INSTANCE_ID = NULL")
                .contains("REVIEWED_BY = NULL")
                .contains("REVIEWED_TIME = NULL")
                .contains("REJECT_REASON = NULL")
                .doesNotContain("LOCK_VERSION = LOCK_VERSION + 1");
    }

    @Test
    void rejectedResubmissionSqlPreservesCurrentSubmissionAndClearsPreviousResult() throws IOException {
        String xml = readXml();
        String prepare = updateSection(xml, "prepareRejectedResubmission");
        String normalized = compact(prepare).toUpperCase(Locale.ROOT);

        assertThat(normalized)
                .contains("SUBMITTED_BY = #{SUBMITTEDBY}")
                .contains("SUBMITTED_TIME = #{SUBMITTEDTIME}")
                .contains("BUSINESS_KEY = #{BUSINESSKEY}")
                .contains("PROCESS_INSTANCE_ID = NULL")
                .contains("REVIEWED_BY = NULL")
                .contains("REVIEWED_TIME = NULL")
                .contains("REJECT_REASON = NULL")
                .contains("LEAD_STATUS = 'REJECTED'");
    }

    @Test
    void approvalFinalizationLinksCustomerAndPoolWithPostTransitionVersion() throws IOException {
        String xml = readXml();
        String finalizeApproval = updateSection(xml, "finalizeApproval");
        String normalized = compact(finalizeApproval).toUpperCase(Locale.ROOT);

        assertThat(normalized)
                .contains("CUST_ID = #{CUSTID}")
                .contains("POOL_STATUS = #{POOLSTATUS}")
                .contains("LEAD_STATUS = 'APPROVED'")
                .contains("LOCK_VERSION = #{EXPECTEDLOCKVERSION}")
                .contains("LOCK_VERSION = LOCK_VERSION + 1")
                .contains("RECORD_STATUS = 'ACTIVE'");
    }

    private String readXml() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/mapper/marketing/MarketingLeadInfoMapper.xml")) {
            assertThat(input).as("MarketingLeadInfoMapper.xml must be on test classpath").isNotNull();
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
