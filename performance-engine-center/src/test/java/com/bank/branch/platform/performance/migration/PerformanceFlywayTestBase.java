package com.bank.branch.platform.performance.migration;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Flyway 迁移集成测试共享基类（本地 MySQL 降级版本）.
 *
 * <p>注意：Docker 不可用，故降级为本地 MySQL onepl_test_v103 数据库。
 * 每次测试前建议手动重建该库，或依赖 Flyway baseline-on-migrate=false 做全量迁移。
 *
 * <p>使用 PerformanceMigrationApp 作为 SpringBootTest 上下文，
 * 仅扫描 performance 子包，启用 Flyway 迁移（classpath:sql 目录）。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {com.bank.branch.platform.performance.support.PerfTestApp.class, com.bank.branch.platform.performance.support.PerfTestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/onepl_test_v103?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
    "spring.datasource.username=root",
    "spring.datasource.password=djdev",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:sql",
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
    "spring.main.allow-bean-definition-overriding=true",
    // V1.2 Phase Q0.1 引入 workflow-center 后 Flowable AutoConfig 会加载，
    // 这里显式禁用不需要的 app/dmn/cmmn/form/eventregistry 子引擎，避免多引擎初始化冲突；
    // history-level/database-schema-update 对齐 bootstrap/application.yml
    "flowable.history-level=audit",
    "flowable.database-schema-update=true",
    "flowable.idm.enabled=false",
    "flowable.app.enabled=false",
    "flowable.eventregistry.enabled=false",
    "flowable.dmn.enabled=false",
    "flowable.cmmn.enabled=false",
    "flowable.form.enabled=false"
})
public abstract class PerformanceFlywayTestBase {

    @Autowired
    protected JdbcTemplate jdbc;
}
