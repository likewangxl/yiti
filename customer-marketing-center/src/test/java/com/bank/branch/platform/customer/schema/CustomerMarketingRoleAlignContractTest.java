package com.bank.branch.platform.customer.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 客户营销客户经理、线索审批角色及在途线索候选组对齐契约。 */
class CustomerMarketingRoleAlignContractTest {

    private static final String SQL_PATH =
            "docs/superpowers/sql/2026-08-13-customer-marketing-roles-align.sql";

    @Test
    void shouldDefineCustomerManagerRoleAndItsSevenMenus() throws IOException {
        String sql = compact(readSql());

        assertThat(sql)
                .contains("'R_CUST_MARKETING_MANAGER', 'CUST_MARKETING_MANAGER', '客户营销-客户经理'")
                .contains("'CUST_MARKETING_MANAGER' AS ROLE_CODE")
                .contains("'M_MKT_CUST_LIST'")
                .contains("'M_MKT_LEAD_ENTRY'")
                .contains("'M_MKT_POOL_AVAIL'")
                .contains("'M_MKT_POOL_CLAIM'")
                .contains("'M_MKT_CROSS_ORG'")
                .contains("'M_MKT_TOUCH_MINE'")
                .contains("'M_MKT_TRANSFER'");
    }

    @Test
    void shouldDefineLeadApproverRoleWithPageAndWorkflowActions() throws IOException {
        String sql = compact(readSql());

        assertThat(sql)
                .contains("'R_CUST_LEAD_APPROVER', 'CUST_LEAD_APPROVER', '客户营销-线索审批'")
                .contains("'CUST_LEAD_APPROVER' AS ROLE_CODE")
                .contains("'M_MKT_LEAD_APPR'")
                .contains("'C_LEAD_APPR_LIST'")
                .contains("'C_LEAD_APPR_DET'")
                .contains("'C_LEAD_APPR_EXP'")
                .contains("'W_TASK_APPROVE'")
                .contains("'W_TASK_REJECT'")
                .doesNotContain("'CUST_LEAD_APPROVER', 'W_TASK_CLAIM'");
    }

    @Test
    void shouldAddApproverAndAdminToBothLeadWorkflowNodes() throws IOException {
        String sql = compact(readSql());

        assertThat(sql)
                .contains("process_definition_key = 'lead_approve_v1' AND node_key = 'branch_manager_approve'")
                .contains("'[\"BRANCH_HEAD\",\"CUST_LEAD_APPROVER\",\"SYS_ADMIN\"]'")
                .contains("process_definition_key = 'lead_approve_v1' AND node_key = 'hq_review'")
                .contains("'[\"CORP_DEPT\",\"RETAIL_DEPT\",\"CUST_LEAD_APPROVER\",\"SYS_ADMIN\"]'");
    }

    @Test
    void shouldBackfillActiveLeadTasksWithoutAssigningRoleToUsers() throws IOException {
        String sql = compact(readSql()).toUpperCase();

        assertThat(sql)
                .contains("ACT_RU_TASK")
                .contains("ACT_RU_IDENTITYLINK")
                .contains("ACT_HI_IDENTITYLINK")
                .contains("ROLE:CUST_LEAD_APPROVER")
                .contains("ROLE:SYS_ADMIN")
                .doesNotContain("INSERT INTO PT_USER_ROLE");
    }

    @Test
    void shouldConfigureRequiredBusinessScopes() throws IOException {
        String sql = compact(readSql());

        assertThat(sql)
                .contains("'CUST_MARKETING_MANAGER', 'CROSS_ORG_MARKETING', 'SELF_CREATED'")
                .contains("'CUST_LEAD_APPROVER', 'LEAD', 'ALL'");
    }

    private String readSql() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve(SQL_PATH))) {
            current = current.getParent();
        }
        assertThat(current).as("仓库根目录").isNotNull();
        return Files.readString(current.resolve(SQL_PATH), StandardCharsets.UTF_8);
    }

    private String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
