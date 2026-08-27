package com.bank.branch.platform.customer.marketing.tag;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 标签列表与计数必须共用 tagType/viewStatus 筛选语义。 */
class MarketingCustomerTagMapperViewStatusSqlContractTest {

    @Test
    void pageAndCountShareExactTagTypeAndAllViewStatusPredicates() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/marketing/MarketingCustomerTagMapper.xml"));
        String normalized = xml.replaceAll("\\s+", " ");

        assertTrue(normalized.contains("<sql id=\"FILTER_CONDITIONS\""));
        assertTrue(normalized.contains("t.tag_type = #{tagType}"));
        assertTrue(normalized.contains("viewStatus == 'ACTIVE'"));
        assertTrue(normalized.contains("t.approval_status = 'APPROVED' AND t.status = 'ENABLED'"));
        assertTrue(normalized.contains("viewStatus == 'DISABLED'"));
        assertTrue(normalized.contains("t.approval_status = 'APPROVED' AND t.status = 'DISABLED'"));
        assertTrue(normalized.contains("viewStatus == 'PENDING'"));
        assertTrue(normalized.contains("t.approval_status = 'PENDING'"));
        assertTrue(normalized.contains("viewStatus == 'REJECTED'"));
        assertTrue(normalized.contains("t.approval_status = 'REJECTED'"));
        assertTrue(normalized.contains("viewStatus == 'EXCEPTION'"));
        assertTrue(normalized.contains("OR t.approval_status = 'REJECTED'"));
        assertTrue(occurrences(normalized, "<include refid=\"FILTER_CONDITIONS\"/>") == 2);
    }

    private int occurrences(String value, String expected) {
        return (value.length() - value.replace(expected, "").length()) / expected.length();
    }
}
