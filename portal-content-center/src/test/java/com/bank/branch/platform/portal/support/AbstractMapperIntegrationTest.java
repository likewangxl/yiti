package com.bank.branch.platform.portal.support;

import com.bank.branch.platform.portal.config.PortalMyBatisConfig;
import org.junit.jupiter.api.Tag;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Mapper 集成测试基类
 *
 * <p>V1 环境说明：直连本地 MySQL 8.0 实例（127.0.0.1:3306/onepl），
 * 不使用 Testcontainers（开发机无 Docker Desktop）。
 * DDL 通过 spring.sql.init.schema-locations 加载 3 份 clean DDL（CREATE TABLE IF NOT EXISTS，幂等安全）。
 * </p>
 *
 * <p>V2 升级路径：如果 CI 环境有 Docker，可切换回 Testcontainers 方案
 * （pom.xml 已保留 testcontainers 依赖），参照 spec r3 §6.2 的原始设计。</p>
 *
 * <p>测试隔离：每个测试方法通过 {@code @Sql} 注解重置 fixture 数据。
 * 共享 onepl 数据库不会影响已有的 auth/governance/workflow 表
 * （DDL 全部使用 CREATE TABLE IF NOT EXISTS）。</p>
 */
@Tag("integration")
@SpringBootTest(classes = {
    DataSourceAutoConfiguration.class,
    SqlInitializationAutoConfiguration.class,
    MybatisAutoConfiguration.class,
    PortalMyBatisConfig.class
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractMapperIntegrationTest {
    // 配置全部在 application-test.yml 中，无需 @DynamicPropertySource
    // spring.sql.init.schema-locations 在 application-test.yml 中指定 3 份 DDL
}
