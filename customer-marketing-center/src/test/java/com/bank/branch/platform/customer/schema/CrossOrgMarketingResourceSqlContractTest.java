package com.bank.branch.platform.customer.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 跨机构营销审核角色资源与数据范围 SQL 契约。 */
class CrossOrgMarketingResourceSqlContractTest {

    private static final String SQL_PATH =
            "docs/superpowers/sql/2026-08-11-customer-phase3-resource-align.sql";

    @Test
    void reviewerRolesShouldReceiveReviewResourcesAndAllScope() throws IOException {
        String sql = compact(readSql());

        assertThat(sql)
                .contains("'CORP_DEPT','CORP_DEPT_LEADER','RETAIL_DEPT','RETAIL_DEPT_LEADER'")
                .contains("'M_MKT_CROSS_ORG','C_CROSS_LIST','C_CROSS_DETAIL','C_CROSS_APPROVE','C_CROSS_REJECT'")
                .contains("'CROSS_ORG_MARKETING', 'ALL'");
    }

    @Test
    void executableSqlShouldContainOnlyAllowedStatementFamilies() throws IOException {
        String sql = readSql().replaceAll("(?m)^\\s*--.*$", "");

        assertThat(sql.toUpperCase())
                .doesNotContain("SET NAMES")
                .doesNotContain("DELETE ")
                .doesNotContain("CREATE ")
                .doesNotContain("ALTER ")
                .doesNotContain("DROP ")
                .doesNotContain("INFORMATION_SCHEMA");
        assertThat(sql.stripTrailing().toUpperCase()).endsWith("COMMIT;");
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
