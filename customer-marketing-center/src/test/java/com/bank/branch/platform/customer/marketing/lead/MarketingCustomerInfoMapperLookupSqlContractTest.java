package com.bank.branch.platform.customer.marketing.lead;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户反显查询必须显式识别重名，禁止静默取第一条。 */
class MarketingCustomerInfoMapperLookupSqlContractTest {

    @Test
    void lookupUsesActiveExactIdentityAndReadsAtMostTwoRows() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/marketing/MarketingCustomerInfoMapper.xml"));
        String normalized = xml.replaceAll("\\s+", " ");

        assertTrue(normalized.contains("<select id=\"selectActiveMatches\""));
        assertTrue(normalized.contains("record_status = 'ACTIVE'"));
        assertTrue(normalized.contains("unified_credit_code = #{unifiedCreditCode}"));
        assertTrue(normalized.contains("cust_name = #{customerName}"));
        assertTrue(normalized.contains("LIMIT 2"));
    }
}
