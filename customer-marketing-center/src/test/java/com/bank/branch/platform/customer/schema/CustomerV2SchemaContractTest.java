package com.bank.branch.platform.customer.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公司部 V2 第一阶段数据库契约测试。
 *
 * <p>该测试守护客户列表、线索录入和线索审批依赖的基线 DDL，避免后续只修改
 * 页面或实体却遗漏数据库字段、关系表与关键索引。</p>
 */
class CustomerV2SchemaContractTest {

    private static final List<String> REQUIRED_TABLES = List.of(
            "CUST_LEAD_MANAGER_SCOPE",
            "CUST_LEAD_TAG_REL",
            "CUST_PERFORMANCE_RELATION_SNAPSHOT",
            "CUST_TRANSFER_LOG",
            "CUST_TRANSFER_TARGET"
    );

    @Test
    void ddlShouldCoverCustomerListLeadEntryAndLeadApproval() throws IOException {
        String ddl = Files.readString(resolveDdlPath(), StandardCharsets.UTF_8).toUpperCase();

        REQUIRED_TABLES.forEach(table ->
                assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS `" + table + "`"));

        assertThat(ddl)
                .contains("`LEAD_TYPE`")
                .contains("`DISTRIBUTION_MODE`")
                .contains("`MAIN_MANAGER_ID`")
                .contains("`SUBMITTED_TIME`")
                .contains("`REJECT_REASON`")
                .contains("`CURRENT_LEAD_ID`")
                .contains("`MAIN_ORG_ID`")
                .contains("`LAST_TOUCH_TIME`")
                .contains("`SOURCE_FILE_OBJECT_ID`")
                .contains("CREATE TABLE IF NOT EXISTS `CUSTOMER_MARKET_CUSTOMER`")
                .contains("UNIQUE KEY `UK_CUSTOMER_MARKET_CREDIT_CODE`")
                .contains("KEY `IDX_CUSTOMER_MARKET_OPENED_MANAGER`")
                .contains("KEY `IDX_CUSTOMER_MARKET_OPENED_ORG`");
    }

    @Test
    void approvalHistoryShouldRemainOwnedByWorkflowCenter() throws IOException {
        String ddl = Files.readString(resolveDdlPath(), StandardCharsets.UTF_8).toUpperCase();

        assertThat(ddl).doesNotContain("CREATE TABLE IF NOT EXISTS `CUST_LEAD_APPROVAL`");
        assertThat(ddl)
                .contains("`BUSINESS_KEY`")
                .contains("`PROCESS_INSTANCE_ID`");
    }

    private Path resolveDdlPath() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = workingDirectory.resolve("docs/schema/ddl-customer.sql");
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        return workingDirectory.resolve("../docs/schema/ddl-customer.sql").normalize();
    }
}
