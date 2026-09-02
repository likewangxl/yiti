package com.bank.branch.platform.bizapp.schema;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bank.branch.platform.bizapp.entity.SupportProcessLog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 中台支持过程记录表名必须遵循 MARKETING_ 前缀约定。 */
class SupportProcessLogTableNameContractTest {

    private static final String EXPECTED_TABLE = "MARKETING_SUPPORT_PROCESS_LOG";
    private static final Pattern LEGACY_STANDALONE_TABLE =
            Pattern.compile("(?<![A-Z0-9_])SUPPORT_PROCESS_LOG(?![A-Z0-9_])");

    @Test
    void entityUsesMarketingPrefixedTable() {
        TableName tableName = SupportProcessLog.class.getAnnotation(TableName.class);

        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo(EXPECTED_TABLE);
    }

    @Test
    void mapperXmlUsesMarketingPrefixedTableWithoutLegacyStandaloneName() throws IOException {
        String mapperXml = readClasspathResource("mapper/bizapp/SupportProcessLogMapper.xml");

        assertThat(mapperXml).contains(EXPECTED_TABLE);
        assertThat(LEGACY_STANDALONE_TABLE.matcher(mapperXml).find()).isFalse();
    }

    @Test
    void testSchemaUsesMarketingPrefixedTableWithoutLegacyStandaloneName() throws IOException {
        String schema = readClasspathResource("schema.sql");

        assertThat(schema).contains(EXPECTED_TABLE);
        assertThat(LEGACY_STANDALONE_TABLE.matcher(schema).find()).isFalse();
    }

    private String readClasspathResource(String resource) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("classpath resource %s", resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
