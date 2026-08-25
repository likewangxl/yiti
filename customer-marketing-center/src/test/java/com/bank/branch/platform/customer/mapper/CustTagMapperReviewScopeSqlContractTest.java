package com.bank.branch.platform.customer.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 标签审核记录查询必须由后端收敛到当前审核人。 */
class CustTagMapperReviewScopeSqlContractTest {

    @Test
    void reviewHistory_shouldFilterByCurrentReviewer() throws IOException {
        String xml;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/customer/CustTagMapper.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sqlBody(xml, "TAG_REVIEW_FILTER"))
                .contains("reviewed_by = #{reviewerEmpId}");
        assertThat(selectBody(xml, "selectReviewPage"))
                .contains("<include refid=\"TAG_REVIEW_FILTER\"/>");
        assertThat(selectBody(xml, "countReviewPage"))
                .contains("<include refid=\"TAG_REVIEW_FILTER\"/>");
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
