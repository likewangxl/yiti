package com.bank.branch.platform.customer.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户营销主档与 M98 客户主档隔离契约测试。
 *
 * <p>{@code CUST_MASTER} 继续承接 M98 T-1 同步及存量跨模块查询；客户营销业务必须使用
 * {@code CUSTOMER_MARKET_CUSTOMER}，防止线索、标签、认领和触达数据混入 M98 主档。</p>
 */
class CustomerMarketTableIsolationContractTest {

    @Test
    void customerMarketingDdlShouldDefineDedicatedCustomerTable() throws IOException {
        String ddl = read("docs/schema/ddl-customer.sql").toUpperCase();

        assertThat(ddl)
                .contains("CREATE TABLE IF NOT EXISTS `CUSTOMER_MARKET_CUSTOMER`")
                .contains("UNIQUE KEY `UK_CUSTOMER_MARKET_CREDIT_CODE`");
    }

    @Test
    void freshDeploymentBaselinesShouldAlsoCreateDedicatedCustomerTable() throws IOException {
        assertThat(read("create-table.sql").toUpperCase())
                .contains("CREATE TABLE `CUSTOMER_MARKET_CUSTOMER`");
        assertThat(read("data.sql").toUpperCase())
                .contains("CREATE TABLE `CUSTOMER_MARKET_CUSTOMER`");
        assertThat(read("docs/schema/ddl-yiti-prod-golive.sql").toUpperCase())
                .contains("CREATE TABLE `CUSTOMER_MARKET_CUSTOMER`");
    }

    @Test
    void customerMarketingEntityAndMapperShouldOnlyUseDedicatedTable() throws IOException {
        String entity = read("customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/CustMaster.java");
        String mapperXml = read("customer-marketing-center/src/main/resources/mapper/customer/CustMasterMapper.xml")
                .toUpperCase();

        assertThat(entity).contains("@TableName(\"CUSTOMER_MARKET_CUSTOMER\")");
        assertThat(mapperXml)
                .contains("FROM CUSTOMER_MARKET_CUSTOMER")
                .doesNotContain("XAN_M98_CUST_STAT_SHOW3")
                .doesNotContain("FROM CUST_MASTER")
                .doesNotContain("INTO CUST_MASTER");
    }

    @Test
    void m98SyncShouldRemainIsolatedOnLegacyCustMaster() throws IOException {
        String mapperXml = read("customer-marketing-center/src/main/resources/mapper/customer/M98CustMasterMapper.xml")
                .toUpperCase();
        String syncService = read("customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/CustMasterSyncService.java");

        assertThat(mapperXml)
                .contains("XAN_M98_CUST_STAT_SHOW3")
                .contains("INTO CUST_MASTER")
                .doesNotContain("CUSTOMER_MARKET_CUSTOMER");
        assertThat(syncService).contains("M98CustMasterMapper");
    }

    private String read(String relativePath) throws IOException {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = workingDirectory.resolve(relativePath);
        Path path = Files.exists(fromRoot) ? fromRoot : workingDirectory.resolve("../" + relativePath).normalize();
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
