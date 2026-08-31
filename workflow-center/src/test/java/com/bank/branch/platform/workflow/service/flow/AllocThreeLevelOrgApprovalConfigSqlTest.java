package com.bank.branch.platform.workflow.service.flow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** 业绩调整三级机构负责人逐级审批设计器 SQL 契约。 */
class AllocThreeLevelOrgApprovalConfigSqlTest {

    private static final Path SQL_RELATIVE_PATH = Path.of(
            "docs/superpowers/sql/2026-08-31-alloc-three-level-org-leader-approval.sql");

    @Test
    void script_is_scoped_idempotent_and_uses_only_allowed_dml() {
        String sql = readSql();
        String executable = stripComments(sql);
        List<String> statements = statements(executable);

        assertThat(statements).isNotEmpty().allMatch(this::isAllowedStatement);
        assertThat(executable)
                .doesNotMatch("(?is).*\\b(DELETE|ALTER|CREATE|DROP|TRUNCATE|CALL|PROCEDURE|TEMPORARY|INFORMATION_SCHEMA)\\b.*")
                .contains("FDEF_ALLOC_CORP", "FDEF_ALLOC_RETAIL", "START TRANSACTION", "COMMIT");
        assertThat(statements).noneMatch(statement -> statement.matches("(?is)^SELECT\\b.*"));
        assertThat(sql).contains("重新发布", "在途实例", "approve_mode", "VARCHAR(16)");
    }

    @Test
    void applicant_level3_routes_through_level3_then_level2_leaders() {
        String executable = stripComments(readSql());

        assertThat(executable)
                .containsPattern("(?is)UPDATE\\s+WF_FLOW_NODE_APPROVER[\\s\\S]*?org_scope\\s*=\\s*'SELF'[\\s\\S]*?branch_approve_l3")
                .containsPattern("(?is)UPDATE\\s+WF_FLOW_NODE_APPROVER[\\s\\S]*?org_scope\\s*=\\s*'L2'[\\s\\S]*?branch_approve_l2")
                .containsPattern("(?is)UPDATE\\s+WF_FLOW_EDGE[\\s\\S]*?branch_approve_l3[\\s\\S]*?branch_approve_l2")
                .contains("\"field\":\"startOrgLevel\"", "\"op\":\"EQ\"", "\"value\":\"3\"");
        assertThat(readSql()).contains("3级机构负责人审批", "2级机构负责人审批");
    }

    @Test
    void original_owner_route_adds_optional_level3_group_before_existing_level2_group() {
        String executable = stripComments(readSql());
        String normalized = executable.toUpperCase(Locale.ROOT);

        assertThat(normalized)
                .contains("ORIGINAL_OWNER_LEVEL3_APPROVE", "ORIGINALOWNERLEVEL3ORGAPPROVALGROUPS",
                        "ORIGINALOWNERLEVEL3APPROVALREQUIRED", "'GROUP_ALL'")
                .contains("\"VALUE\":\"YES\"", "\"VALUE\":\"NO\"",
                        "ORIGINAL_OWNER_EMPLOYEE_APPROVE", "ORIGINAL_OWNER_APPROVE")
                .containsPattern("(?s)INSERT\\s+INTO\\s+WF_FLOW_NODE[\\s\\S]*NOT\\s+EXISTS")
                .containsPattern("(?s)INSERT\\s+INTO\\s+WF_FLOW_NODE_APPROVER[\\s\\S]*NOT\\s+EXISTS")
                .containsPattern("(?s)INSERT\\s+INTO\\s+WF_FLOW_EDGE[\\s\\S]*NOT\\s+EXISTS");
        assertThat(readSql()).contains("原业绩所属3级机构负责人审批", "原业绩所属2级机构负责人审批");
    }

    private String readSql() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && current != null; i++) {
            Path candidate = current.resolve(SQL_RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (IOException e) {
                    throw new UncheckedIOException("无法读取 SQL 契约文件: " + candidate, e);
                }
            }
            current = current.getParent();
        }
        fail("SQL 契约文件不存在: " + SQL_RELATIVE_PATH);
        return "";
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--[^\\r\\n]*", "");
    }

    private List<String> statements(String executable) {
        return Arrays.stream(executable.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private boolean isAllowedStatement(String statement) {
        return statement.matches("(?is)^(START\\s+TRANSACTION|COMMIT|INSERT\\s+INTO\\b[\\s\\S]*|UPDATE\\b[\\s\\S]*)$");
    }
}
