package com.bank.branch.platform.customer.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 触达任务列表/详情 SQL 必须一次返回页面展示字段并保持权限边界。 */
class TouchTaskViewMapperContractTest {

    @Test
    void viewQueries_shouldAggregateCustomerLogsAndParticipantsAndScopeViewer() throws IOException {
        String xml = Files.readString(Path.of("src/main/resources/mapper/customer/TouchTaskMapper.xml"));

        assertThat(xml).contains("c.cust_name AS cust_name");
        assertThat(xml).contains("AS customer_name");
        assertThat(xml).contains("AS log_count");
        assertThat(xml).contains("AS participant_emp_ids_text");
        assertThat(xml).contains("AS eligible_collaborator_emp_ids_text");
        assertThat(xml).contains("MARKETING_TOUCH_WORKLOG_PARTICIPANT");
        assertThat(xml).contains("t.org_id = #{viewerOrgId}");
        assertThat(xml).contains("pv.participant_role = 'COLLABORATOR'");
        assertThat(xml).contains("wv.operator_emp_id = t.assignee_emp_id");
        assertThat(xml).contains("pv.participant_org_id = t.org_id");
        assertThat(xml).contains("ck.cust_name LIKE CONCAT");
    }
}
