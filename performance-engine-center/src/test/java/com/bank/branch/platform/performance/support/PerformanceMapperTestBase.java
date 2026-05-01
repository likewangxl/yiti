package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单线程 Mapper IT 基类.
 * <p>用法: 普通 Mapper 增删改查测试继承此类, 方法默认 @Transactional + @Rollback(true) 自动回滚, 无需手工清理.
 * <p>数据库: 连接本地 onepl, 使用 application-test.yml 覆盖的配置.
 * <p>注意: 并发场景请使用 {@link PerformanceConcurrentTestBase}, 不要继承本类.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {PerfTestApp.class, PerfTestConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@Rollback(true)
public abstract class PerformanceMapperTestBase {
}
