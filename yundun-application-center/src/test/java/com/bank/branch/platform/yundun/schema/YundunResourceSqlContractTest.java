package com.bank.branch.platform.yundun.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 浦爱云盾资源 DML 的结构契约。 */
class YundunResourceSqlContractTest {

    private static final String SQL_PATH =
            "docs/superpowers/sql/2026-08-19-yundun-resource-align.sql";

    @Test
    void shouldRegisterAllMenusEndpointsAndAdminScope() throws IOException {
        String sql = compact(readSql());

        assertThat(sql)
                .contains("'M_GROUP_VIOLATION'")
                .contains("'M_YD_ACCOUNTABILITY'")
                .contains("'M_YD_CREDIT'")
                .contains("'P_YD_ACCT_LIST'")
                .contains("'P_YD_ACCT_IMP_TPL'")
                .contains("'P_YD_CREDIT_LIST'")
                .contains("'P_YD_CREDIT_IMP_TPL'")
                .contains("r.ROLE_CODE = 'SYS_ADMIN'")
                .contains("'VIOLATION', 'ALL'");
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
