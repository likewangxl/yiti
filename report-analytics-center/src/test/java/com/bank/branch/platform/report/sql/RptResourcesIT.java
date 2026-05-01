package com.bank.branch.platform.report.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 25 PT_RESOURCE 完整性守护（Task M6.1.1）.
 *
 * <p>plan §5 验收清单要求：M0-M5 累计 25 条 R_RPT_* PT_RESOURCE：
 * <ul>
 *   <li>M1 (V1_0_1): 8 条（query-dimensions / dynamic-query×2 / saved-queries×4 + 1 导出占位）</li>
 *   <li>M2 (V1_0_2): 3 条（dashboard president/org/emp）</li>
 *   <li>M3 (V1_0_3): 6 条（3 view + 3 export）</li>
 *   <li>M4 (V1_0_4): 4 条（sql-probe execute/history/history-detail/whitelist）</li>
 *   <li>M5 (V1_0_6): 3 条（export-tasks status/cancel/download）</li>
 *   <li>M6 (V1_0_7): 1 条占位（sql-probe export 规划态，STATUS=0 disabled）</li>
 * </ul>
 * = 25 条整。
 *
 * <p>SYS_CODE='RPT' 全部统一；STATUS=0 启用 / STATUS=1 禁用（pt_resource 真实语义）。
 *
 * <p>命名前缀使用 {@code R_RPT_*}（auth-permission-center 资源命名约定，前缀 R_*），
 * plan 字面 {@code RPT_*} 是建模级简称，实际落库列值为 {@code R_RPT_*}.
 *
 * <p>对 R_BACK_TECH 角色绑定的守护，仅在测试库存在该角色时生效（防御式断言：
 * 测试库 pt_role 表为空时，V1_0_4 脚本的 NOT EXISTS 子查询不会插行；
 * 真实生产库由 seed-v1.sql 注入业务角色后绑定生效）.
 */
class RptResourcesIT extends ReportFlywayTestBase {

    @Test
    void rptResources_shouldHaveAtLeast25Records() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'R_RPT_%' AND SYS_CODE='RPT'",
            Integer.class);
        assertThat(cnt).isNotNull().isGreaterThanOrEqualTo(25);
    }

    @Test
    void rptSqlProbe_executeResource_shouldOnlyAllowBackTechWhenRoleExists() {
        // 前置：测试库 pt_role 中有 R_BACK_TECH 角色才校验（生产库 seed-v1.sql 会注入；
        // 测试库不一定）。无 R_BACK_TECH → 跳过断言，但仍守护"无业务角色 leak"
        Integer hasBackTech = jdbc.queryForObject(
            "SELECT COUNT(*) FROM PT_ROLE WHERE ROLE_CODE = 'R_BACK_TECH'",
            Integer.class);

        if (hasBackTech != null && hasBackTech > 0) {
            // R_BACK_TECH 存在 → 必有 R_RPT_SQL_EXEC 绑定
            Integer roleBoundCount = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT prr.ROLE_ID) FROM PT_ROLE_RESOURCE prr "
                    + "JOIN pt_role r ON prr.ROLE_ID = r.ROLE_ID "
                    + "WHERE prr.RESOURCE_ID = 'R_RPT_SQL_EXEC' "
                    + "  AND r.ROLE_CODE = 'R_BACK_TECH'",
                Integer.class);
            assertThat(roleBoundCount).isNotNull().isGreaterThanOrEqualTo(1);
        }

        // 业务角色（非 R_ADMIN/R_BACK_TECH）leak 到 R_RPT_SQL_EXEC：必须为 0
        Integer leakedToBusinessRoles = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT prr.ROLE_ID) FROM PT_ROLE_RESOURCE prr "
                + "JOIN pt_role r ON prr.ROLE_ID = r.ROLE_ID "
                + "WHERE prr.RESOURCE_ID = 'R_RPT_SQL_EXEC' "
                + "  AND r.ROLE_CODE NOT IN ('R_BACK_TECH', 'R_ADMIN')",
            Integer.class);
        assertThat(leakedToBusinessRoles).isNotNull().isZero();
    }
}
