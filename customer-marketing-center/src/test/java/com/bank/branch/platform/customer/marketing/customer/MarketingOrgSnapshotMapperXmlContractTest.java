package com.bank.branch.platform.customer.marketing.customer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 聚合 SQL 必须保持客户营销真实表、字段和状态口径。 */
class MarketingOrgSnapshotMapperXmlContractTest {

    @Test
    void snapshotSqlUsesAssignedCustomerAndFirstTouchTaskRelations() throws Exception {
        String xml = readXml();
        int queryStart = xml.indexOf("<select id=\"selectOrgMarketingSnapshots\"");
        int resultMapStart = xml.indexOf("<resultMap id=\"MARKETING_ORG_SNAPSHOT_RESULT\"", queryStart);
        String snapshotSql = xml.substring(queryStart, resultMapStart);

        assertThat(snapshotSql).contains(
                "MARKETING_CUSTOMER_INFO",
                "MARKETING_TOUCH_TASK",
                "COUNT(DISTINCT c.id)",
                "COUNT(DISTINCT t.id)",
                "c.record_status = 'ACTIVE'",
                "c.ownership_status = 'ASSIGNED'",
                "c.main_org_id",
                "c.main_org_id = t.org_id",
                "c.main_org_id IN",
                "t.task_type = 'FIRST_TOUCH'",
                "t.task_status IN ('PENDING', 'IN_PROGRESS')",
                "t.cancel_time IS NULL",
                "t.org_id IN",
                "COALESCE(",
                "GREATEST(customer_stats.source_updated_at, task_stats.source_updated_at)",
                "customer_stats.source_updated_at,",
                "task_stats.source_updated_at");
        assertThat(snapshotSql).doesNotContain("MARKETING_CUSTOMER_CLAIM", "cc.", "t.record_status",
                "tag_name", "task_label");
    }

    private String readXml() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/mapper/marketing/MarketingCustomerClaimMapper.xml")) {
            assertThat(input).as("MarketingCustomerClaimMapper.xml must be on test classpath")
                    .isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
