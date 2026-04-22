package com.bank.branch.platform.performance.migration;

import com.bank.branch.platform.performance.support.PerfTestApp;

/**
 * Flyway 迁移测试专用启动类（复用 PerfTestApp）.
 *
 * <p>复用测试包中已有的 PerfTestApp（扫描 performance 子包，mock 了 auth Bean），
 * 用于 Flyway 迁移验证测试。
 */
public class PerformanceMigrationApp extends PerfTestApp {
}
