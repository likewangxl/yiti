package com.bank.branch.platform.report.sql;

import com.bank.branch.platform.report.ReportTestApplication;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * report-analytics-center Flyway 迁移集成测试基类（M0.2.1）.
 *
 * <p>对齐 performance-engine-center 的 {@code PerformanceFlywayTestBase}：
 * 走本地 MySQL {@code onepl_test_bootstrap} 库（跨模块共享，rpt_* 表和 perf_* 表名隔离，互不影响）。
 *
 * <p>选择不用 H2：DDL 中的 {@code int(11)} / {@code ENGINE=InnoDB} / {@code KEY (col)} 内联索引语法
 * 在 H2 MySQL Mode 下兼容性不稳，且 plan 后续 M5 还要 `rpt_export_task` + 异步导出表的并发场景，
 * 真实 MySQL 才能精确对齐生产 DDL 行为。
 *
 * <p><strong>测试环境前置</strong>：本地需要 MySQL 8.0 实例 {@code onepl_test_bootstrap} 库已创建
 * （root/123456）。无 Docker 环境，故 IT 仅在开发者本地有库时运行。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ReportTestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/onepl_test_bootstrap?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
    "spring.datasource.username=root",
    "spring.datasource.password=djdev",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.flyway.enabled=true",
    // 使用模块命名空间子目录 sql/report/ 避免与 performance-engine-center 的 V1_0_0__performance_ddl.sql
    // 同版本号冲突（FlywayException: Found more than one migration with version 1.0.0）
    "spring.flyway.locations=classpath:sql/report",
    // 使用独立 schema_history 表避免与 perf 模块的 V1_0_0__performance_ddl 已登记记录冲突
    // （否则 Flyway 看到 1.0.0 已 success=1 会跳过执行 V1_0_0__rpt_init.sql）
    "spring.flyway.table=flyway_schema_history_rpt",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.baseline-version=0",
    "spring.flyway.clean-disabled=false",
    "spring.flyway.clean-on-validation-error=false",
    "spring.flyway.validate-on-migrate=false",
    "spring.flyway.ignore-migration-patterns=*:missing,*:pending,*:ignored,*:future",
    "mybatis-plus.mapper-locations=classpath*:mapper/**/*Mapper.xml",
    "mybatis-plus.configuration.map-underscore-to-camel-case=true",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.cache.type=none",
    "spring.main.allow-bean-definition-overriding=true"
})
public abstract class ReportFlywayTestBase {

    @Autowired
    protected JdbcTemplate jdbc;
}
