package com.bank.branch.platform.redengine.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单线程 Mapper IT 基类（同构复制
 * performance-engine-center {@code PerformanceMapperTestBase} 的模式）。
 *
 * <p>用法：普通 Mapper 增删改查测试继承此类，方法默认 {@code @Transactional + @Rollback(true)}
 * 自动回滚，无需手工清理测试数据。
 * <p>数据库：连接 onepl_test_bootstrap（application-test.yml 覆盖配置），绝不连接 yiti 生产库。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = RedEngineTestApp.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@Rollback(true)
public abstract class RedEngineMapperTestBase {
}
