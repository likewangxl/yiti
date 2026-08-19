package com.bank.branch.platform.customer.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CustClaimMapperPoolSqlContractTest {

    @Test
    void pendingPool_shouldContainOnlyApprovedPublicLeadCustomers() throws IOException {
        String xml;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/customer/CustClaimMapper.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml).doesNotContain("cl.id IS NULL");
        assertThat(xml).doesNotContain("cl.distribution_mode = 'SCOPE'");
        assertThat(occurrences(xml, "cl.distribution_mode = 'PUBLIC'")).isEqualTo(2);
        assertThat(occurrences(xml, "cl.lead_status = 'APPROVED'")).isEqualTo(2);
    }

    private int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
