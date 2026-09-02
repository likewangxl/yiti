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

/**
 * 业绩调整设计器流程配置脚本契约测试。
 *
 * <p>本测试只读取交付脚本，不连接数据库，也不执行其中的 SQL。它守护脚本的
 * 目标流程、节点/审批人配置、路由拓扑、幂等写法和数据库变更白名单。</p>
 */
class AllocOriginalOwnerApprovalConfigSqlTest {

    private static final Path SQL_RELATIVE_PATH = Path.of(
            "docs/superpowers/sql/2026-08-27-alloc-original-owner-employee-and-leader-approval.sql");

    private static final List<String> NEW_IDS = List.of(
            "NC_ORIG_OWNER_EMP",
            "NR_ORIG_OWNER_EMP",
            "AC_ORIG_OWNER_EMP",
            "AR_ORIG_OWNER_EMP",
            "EC_OWNER_EMP_LEADER",
            "ER_OWNER_EMP_LEADER");

    @Test
    void script_is_present_and_scoped_to_two_alloc_designer_flows() {
        String sql = readSql();
        String executable = stripComments(sql);
        List<String> statements = statements(executable);

        assertThat(statements).isNotEmpty();
        assertThat(statements).allMatch(this::isAllowedStatement);
        assertThat(executable)
                .doesNotMatch("(?is).*\\b(DELETE|ALTER|CREATE|DROP|TRUNCATE|CALL|PROCEDURE|TEMPORARY|INFORMATION_SCHEMA)\\b.*");
        assertThat(statements).noneMatch(statement -> statement.matches("(?is)^SELECT\\b.*"));

        assertThat(executable).contains("FDEF_ALLOC_CORP", "FDEF_ALLOC_RETAIL");
        assertThat(statements)
                .filteredOn(statement -> statement.matches("(?is)^(INSERT|UPDATE)\\b.*"))
                .allMatch(statement -> statement.matches("(?is).*FDEF_ALLOC_(CORP|RETAIL).*"));
        assertThat(sql).contains("重新发布");
    }

    @Test
    void script_adds_employee_all_node_and_var_approver_for_both_flows() {
        String executable = stripComments(readSql());
        String normalized = executable.toUpperCase(Locale.ROOT);
        List<String> nodeInserts = statements(executable).stream()
                .filter(statement -> statement.matches("(?is)^INSERT\\s+INTO\\s+WF_FLOW_NODE\\b.*"))
                .toList();
        List<String> approverInserts = statements(executable).stream()
                .filter(statement -> statement.matches("(?is)^INSERT\\s+INTO\\s+WF_FLOW_NODE_APPROVER\\b.*"))
                .toList();

        assertThat(nodeInserts).hasSize(2).allSatisfy(statement -> assertThat(statement)
                .contains("'original_owner_employee_approve'", "'APPROVAL'", "'ALL'", "NOT EXISTS"));
        assertThat(normalized).containsPattern("(?s)INSERT\\s+INTO\\s+WF_FLOW_NODE[\\s\\S]*POS_X[\\s\\S]*POS_Y");
        assertThat(normalized).containsPattern(
                "(?s)GREATEST\\s*\\(\\s*COALESCE\\s*\\(\\s*OWNER\\.POS_X\\s*,\\s*180\\s*\\)\\s*-\\s*180\\s*,\\s*0\\s*\\)");
        assertThat(normalized).containsPattern("(?s)OWNER\\.POS_Y");
        assertThat(nodeInserts).anyMatch(statement -> statement.contains("FDEF_ALLOC_CORP"));
        assertThat(nodeInserts).anyMatch(statement -> statement.contains("FDEF_ALLOC_RETAIL"));
        assertThat(approverInserts).hasSize(2).allSatisfy(statement -> assertThat(statement)
                .contains("'VAR'", "'originalOwnerEmpIds'", "NOT EXISTS"));
        assertThat(approverInserts).anyMatch(statement -> statement.contains("FDEF_ALLOC_CORP"));
        assertThat(approverInserts).anyMatch(statement -> statement.contains("FDEF_ALLOC_RETAIL"));
        assertThat(NEW_IDS).allMatch(id -> id.length() <= 32);
        assertThat(executable).contains(NEW_IDS.toArray(new String[0]));
    }

    @Test
    void script_switches_leader_node_to_group_all_and_group_variable() {
        String executable = stripComments(readSql());

        assertThat(executable)
                .containsPattern("(?is)UPDATE\\s+WF_FLOW_NODE[\\s\\S]*?approve_mode\\s*=\\s*'GROUP_ALL'[\\s\\S]*?original_owner_approve")
                .containsPattern("(?is)UPDATE\\s+WF_FLOW_NODE_APPROVER[\\s\\S]*?approver_value\\s*=\\s*'originalOwnerOrgApprovalGroups'[\\s\\S]*?original_owner_approve");
        assertThat(readSql()).contains("originalOwnerOrgLeaderEmpIds");
    }

    @Test
    void script_reconnects_owner_route_and_adds_unconditional_employee_to_leader_edges() {
        String executable = stripComments(readSql());
        String normalized = executable.toUpperCase(Locale.ROOT);
        List<String> edgeUpdates = statements(normalized).stream()
                .filter(statement -> statement.matches("(?s)^UPDATE\\s+WF_FLOW_EDGE\\b.*"))
                .toList();
        List<String> edgeSortUpdates = edgeUpdates.stream()
                .filter(statement -> statement.contains("SET E.SORT_NO"))
                .toList();
        List<String> routeRewires = edgeUpdates.stream()
                .filter(statement -> statement.contains("SET E.TO_NODE_ID"))
                .toList();
        List<String> edgeInserts = statements(normalized).stream()
                .filter(statement -> statement.matches("(?s)^INSERT\\s+INTO\\s+WF_FLOW_EDGE\\b.*"))
                .toList();

        assertThat(edgeSortUpdates).hasSize(1).allSatisfy(statement -> assertThat(statement)
                .contains("FDEF_ALLOC_CORP", "FDEF_ALLOC_RETAIL", "GW1_ROUTE", "ORIGINAL_OWNER_APPROVE",
                        "EMP.ID IS NULL", "E.SORT_NO = E.SORT_NO + 1")
                .containsPattern("(?s)E\\.SORT_NO\\s*>\\s*OWNER_ENTRY\\.SORT_NO"));
        assertThat(routeRewires).hasSize(2).allSatisfy(statement -> assertThat(statement)
                .contains("GW1_ROUTE", "ORIGINAL_OWNER_APPROVE", "ORIGINAL_OWNER_EMPLOYEE_APPROVE", "OWNER"));
        assertThat(edgeInserts).hasSize(2).allSatisfy(statement -> assertThat(statement)
                .contains("ORIGINAL_OWNER_EMPLOYEE_APPROVE", "ORIGINAL_OWNER_APPROVE", "NULL")
                .containsPattern("(?s)SELECT[\\s\\S]*NULL\\s*,\\s*0\\s*,\\s*NULL")
                .containsPattern("(?s)OWNER_ENTRY\\.SORT_NO\\s*\\+\\s*1"));
        assertThat(normalized).contains("EC_OWNER_EMP_LEADER", "ER_OWNER_EMP_LEADER");
    }

    @Test
    void script_uses_insert_select_not_exists_and_shifts_sort_only_before_first_insert() {
        String executable = stripComments(readSql());
        String normalized = executable.toUpperCase(Locale.ROOT);

        assertThat(normalized).contains("START TRANSACTION").contains("COMMIT");
        assertThat(normalized).containsPattern("(?s)INSERT\\s+INTO[\\s\\S]*?SELECT[\\s\\S]*?NOT\\s+EXISTS");
        assertThat(normalized).containsPattern("(?s)UPDATE\\s+WF_FLOW_NODE[\\s\\S]*SORT_NO\\s*=\\s*[^;]*\\+\\s*1");
        assertThat(normalized).containsPattern("(?s)UPDATE\\s+WF_FLOW_EDGE[\\s\\S]*SET\\s+E\\.SORT_NO\\s*=\\s*E\\.SORT_NO\\s*\\+\\s*1");
        assertThat(normalized).contains("EMP.ID IS NULL");
        assertThat(normalized).containsPattern("(?s)NOT\\s+EXISTS\\s*\\(\\s*SELECT[\\s\\S]*ORIGINAL_OWNER_EMPLOYEE_APPROVE");
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
