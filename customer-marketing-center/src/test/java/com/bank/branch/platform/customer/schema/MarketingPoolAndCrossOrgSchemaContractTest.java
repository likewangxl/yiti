package com.bank.branch.platform.customer.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 目标设计稿对应的新营销表 Mapper/实体 SQL 契约。 */
class MarketingPoolAndCrossOrgSchemaContractTest {

    @Test
    void targetMappersShouldUseMarketingTablesAndNeverLegacyPoolTables() throws IOException {
        String pool = read("src/main/resources/mapper/marketing/MarketingCustomerClaimMapper.xml");
        String cross = read("src/main/resources/mapper/marketing/MarketingCrossOrgApplyMapper.xml")
                + read("src/main/resources/mapper/marketing/MarketingCrossOrgRuleMapper.xml")
                + read("src/main/resources/mapper/marketing/MarketingCustomerPerformanceRelSnapshotMapper.xml")
                + read("src/main/resources/mapper/marketing/MarketingTouchTaskMapper.xml");

        assertThat(pool).contains("MARKETING_LEAD_INFO", "MARKETING_CUSTOMER_INFO",
                "MARKETING_CUSTOMER_CLAIM", "SOURCE_LEAD_ID", "CLAIM_STATUS = 'CLAIMED'");
        assertThat(pool).doesNotContain("CUSTOMER_MARKET_CUSTOMER", "CUST_CLAIM");
        assertThat(cross).contains("MARKETING_CROSS_ORG_APPLY", "MARKETING_CROSS_ORG_RULE",
                "MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT", "MARKETING_TOUCH_TASK", "IN_APPROVAL");
        assertThat(cross).doesNotContain("CROSS_ORG_MARKETING_APPLY", "CUST_PERFORMANCE_RELATION_SNAPSHOT",
                "UPDATE TOUCH_TASK", "INSERT INTO TOUCH_TASK");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8).toUpperCase();
    }
}
