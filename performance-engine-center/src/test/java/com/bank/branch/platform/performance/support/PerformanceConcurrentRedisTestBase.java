package com.bank.branch.platform.performance.support;

import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers Redis 并发集成测试基类（V1.3 Task R5.1）.
 *
 * <p>用法：<em>并发</em>型 Redis IT 继承本类，得到一个 Redis 容器实例 + 不含
 * {@code @Transactional} 的 Spring 上下文（多线程场景下 Spring 事务与线程本地绑定不兼容，
 * 数据清理需由 {@link TestDbCleaner} + 数据前缀隔离承担）。
 *
 * <p>继承关系：{@link PerformanceConcurrentTestBase} 提供纯 SpringBootTest（无事务），
 * 本类追加 Testcontainers Redis，作用与 {@link PerformanceRedisTestBase} 对称但无事务。
 *
 * <p>Docker 依赖：同 {@link PerformanceRedisTestBase}，具体测试类建议用
 * {@code @EnabledIfSystemProperty(named="testcontainers.enabled", matches="true")} 保护，
 * 无 Docker 环境下跳过 IT；CI 通过 -Dtestcontainers.enabled=true 激活。
 */
@Testcontainers
public abstract class PerformanceConcurrentRedisTestBase extends PerformanceConcurrentTestBase {

    @Container
    protected static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:6.2.14-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }
}
