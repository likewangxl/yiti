package com.bank.branch.platform.customer.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 线索录入台账必须始终收敛到当前录入人。 */
class CustLeadMapperEntryScopeSqlContractTest {

    @Test
    void entryListAndDetail_shouldFilterByCreatedEmployee() throws IOException {
        String xml;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/customer/CustLeadMapper.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sqlBody(xml, "LEAD_CREATED_FILTER")).contains("created_by = #{empId}");
        assertThat(selectBody(xml, "selectCreatedPage"))
                .contains("<include refid=\"LEAD_CREATED_FILTER\"/>");
        assertThat(selectBody(xml, "countCreatedPage"))
                .contains("<include refid=\"LEAD_CREATED_FILTER\"/>");
        assertThat(selectBody(xml, "selectCreatedById")).contains("created_by = #{empId}");
    }

    private String sqlBody(String xml, String id) {
        int start = xml.indexOf("<sql id=\"" + id + "\"");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</sql>", start);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }

    private String selectBody(String xml, String id) {
        int start = xml.indexOf("<select id=\"" + id + "\"");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</select>", start);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
