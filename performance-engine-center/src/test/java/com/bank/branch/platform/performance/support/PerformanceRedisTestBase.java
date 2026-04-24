package com.bank.branch.platform.performance.support;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers Redis 集成测试基类（V1.3 Task R5.1）.
 *
 * <p>用法：<em>事务型</em>单线程 Redis IT 继承本类，JVM 生命周期内复用同一个 Redis 容器
 * （{@code @Container static} 字段），{@link DynamicPropertySource} 将容器端口注入
 * {@code spring.data.redis.host/port}，覆盖 application-test.yml 中的 localhost:6379 默认。
 *
 * <p>继承关系：{@link PerformanceMapperTestBase} 提供 @SpringBootTest + @Transactional +
 * @Rollback，本类追加 Testcontainers Redis。并发型 IT 不要继承本类（事务与多线程冲突），
 * 使用 {@link PerformanceConcurrentRedisTestBase}。
 *
 * <p>Docker 依赖：测试必须运行在 Docker daemon 可用的宿主机。具体测试类建议用
 * {@code @EnabledIfSystemProperty(named="testcontainers.enabled", matches="true")} 或
 * {@code @EnabledIfDockerAvailable} 保护，CI 通过 -Dtestcontainers.enabled=true 激活；
 * 本地无 Docker 时 `mvn verify` 跳过避免阻塞。
 *
 * <p>Redis 镜像：redis:6.2.14-alpine（与生产 Redis 6.X 基线匹配）。
 */
@Testcontainers
public abstract class PerformanceRedisTestBase extends PerformanceMapperTestBase {

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
