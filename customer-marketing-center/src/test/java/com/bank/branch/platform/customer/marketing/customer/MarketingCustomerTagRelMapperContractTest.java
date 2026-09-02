package com.bank.branch.platform.customer.marketing.customer;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户手工维护标签的关系 SQL 契约。 */
class MarketingCustomerTagRelMapperContractTest {

    @Test
    void manualReactivationClearsPreviousImportReference() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/marketing/MarketingCustomerTagRelMapper.xml"));
        String normalized = xml.replaceAll("\\s+", " ");

        assertTrue(normalized.contains("<update id=\"reactivateWithSourceType\">"));
        assertTrue(normalized.contains("source_type = #{sourceType}, source_ref_id = NULL"));
    }

    @Test
    void touchRuleQueryShouldUseFormalMarketingTablesOnly() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/marketing/MarketingCustomerTagRelMapper.xml"));
        String normalized = xml.replaceAll("\\s+", " ").toUpperCase();

        assertTrue(normalized.contains("<SELECT ID=\"SELECTEFFECTIVETOUCHRULES\""));
        assertTrue(normalized.contains("FROM MARKETING_CUSTOMER_TAG_REL"));
        assertTrue(normalized.contains("JOIN MARKETING_CUSTOMER_TAG"));
        assertTrue(normalized.contains("MARKETING_TOUCH_LIMIT_RULE"));
        assertTrue(!normalized.contains("CUSTOMER_MARKET_CUSTOMER"));
        assertTrue(!normalized.contains("FROM CUST_TAG"));
        assertTrue(!normalized.contains("FROM CUST_TAG_REL"));
        assertTrue(!normalized.contains("CUST_TOUCH_LIMIT_RULE"));
    }
}
