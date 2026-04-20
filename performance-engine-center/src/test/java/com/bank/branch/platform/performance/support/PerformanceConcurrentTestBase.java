package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 并发 IT 基类.
 * <p>**不含 @Transactional** —— Spring 事务与多线程不兼容 (线程本地绑定, 子线程看不到父事务).
 * <p>继承本类的测试必须:
 * <ul>
 *   <li>使用独立数据前缀 (例如 CONCUR_SC_* / CONCUR_METRIC_*)</li>
 *   <li>在 @AfterEach 或 @Sql(AFTER_TEST_METHOD) 手工 DELETE 清理</li>
 *   <li>禁止依赖其他单线程测试遗留的数据</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {PerfTestApp.class, PerfTestConfig.class})
@ActiveProfiles("test")
public abstract class PerformanceConcurrentTestBase {
}
