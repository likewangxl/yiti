package com.bank.branch.platform.portal.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.assertj.core.api.Assertions.assertThat;

class MapperContainerSmokeTest extends AbstractMapperIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void shouldStartMysqlContainerAndLoadAllDdls() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Verify product_info table exists (from ddl-portal.sql)
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'product_info'")) {
                assertThat(rs.next()).as("product_info table should exist").isTrue();
            }
            // Verify portal_shortcut table exists
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'portal_shortcut'")) {
                assertThat(rs.next()).as("portal_shortcut table should exist").isTrue();
            }
            // Verify the normalized user-product relation table exists
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'PORTAL_USER_PRODUCT_REL'")) {
                assertThat(rs.next()).as("PORTAL_USER_PRODUCT_REL table should exist").isTrue();
            }
            // Verify PT_USER table exists (from ddl-auth.sql)
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'PT_USER'")) {
                assertThat(rs.next()).as("PT_USER table should exist").isTrue();
            }
        }
    }
}
