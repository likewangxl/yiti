package com.bank.branch.platform.customer.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 线索导入详情仅保留原始文件下载，且控制器不能把管理员标志传入批次查询。 */
class MarketingLeadImportControllerContractTest {

    private static final String SOURCE = readSource();

    @Test
    void onlyExposesSourceFileDownload() {
        assertTrue(SOURCE.contains("/source-file"));
        assertFalse(SOURCE.contains("error-file"));
        assertFalse(SOURCE.contains("errorFile"));
    }

    @Test
    void doesNotUseSystemAdminScopeForImportRecords() {
        assertFalse(SOURCE.contains("isSystemAdmin"));
    }

    private static String readSource() {
        try {
            return Files.readString(Path.of(
                    "src/main/java/com/bank/branch/platform/customer/controller/MarketingLeadImportController.java"));
        } catch (Exception ex) {
            throw new IllegalStateException("无法读取线索导入控制器源码", ex);
        }
    }
}
