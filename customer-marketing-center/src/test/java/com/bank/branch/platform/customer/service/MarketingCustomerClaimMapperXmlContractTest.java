package com.bank.branch.platform.customer.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** 客户池详情可见性 SQL 合同测试，不连接数据库。 */
class MarketingCustomerClaimMapperXmlContractTest {

    @Test
    void availableLeadCheckUsesMarketingTablesAndAllPoolVisibilityGuards() throws IOException {
        String xml = readXml();
        String check = selectSection(xml, "countAvailableLead");
        String normalized = compact(check).toUpperCase(Locale.ROOT);

        assertThat(normalized)
                .contains("FROM MARKETING_LEAD_INFO L")
                .contains("JOIN MARKETING_CUSTOMER_INFO C")
                .contains("L.ID = #{LEADID}")
                .contains("L.LEAD_STATUS = 'APPROVED'")
                .contains("L.DISTRIBUTION_MODE = 'PUBLIC'")
                .contains("L.POOL_STATUS = 'AVAILABLE'")
                .contains("C.RECORD_STATUS = 'ACTIVE'")
                .contains("C.OWNERSHIP_STATUS = 'UNASSIGNED'")
                .contains("C.MAIN_MANAGER_ID IS NULL")
                .contains("CC.CLAIMED_BY = #{EMPID}")
                .contains("CC.CLAIM_STATUS = 'CLAIMED'")
                .doesNotContain("CUST_LEAD");
    }

    private String readXml() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/mapper/marketing/MarketingCustomerClaimMapper.xml")) {
            assertThat(input).as("MarketingCustomerClaimMapper.xml must be on test classpath")
                    .isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String selectSection(String xml, String id) {
        String startToken = "<select id=\"" + id + "\"";
        int start = xml.indexOf(startToken);
        assertThat(start).as("missing select %s", id).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</select>", start);
        assertThat(end).as("missing closing select %s", id).isGreaterThan(start);
        return xml.substring(start, end);
    }

    private String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
