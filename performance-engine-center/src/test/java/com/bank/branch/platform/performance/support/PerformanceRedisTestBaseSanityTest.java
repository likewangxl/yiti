package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R5.1 Red/Green: PerformanceRedisTestBase 自冒烟测试.
 *
 * <p>目标：验证继承 {@link PerformanceRedisTestBase} 的 IT 可以拿到一个真实可用的
 * Redis 实例（由 Testcontainers-redis 在 @Container 阶段启动），{@code RedisTemplate}
 * 能完成 SET/GET 回环。
 *
 * <p>Docker 网关：启动 Redis 容器依赖本机可访问 Docker daemon。本 Sanity 测试使用
 * {@code @EnabledIfSystemProperty(named="testcontainers.enabled", matches="true")}
 * 默认关闭；CI 环境通过 {@code -Dtestcontainers.enabled=true} 打开，本地无 Docker 环境
 * 跳过避免 `mvn verify` 全量阻塞。
 *
 * <p>TDD 节奏：
 * <ul>
 *   <li>Red: 当前 {@link PerformanceRedisTestBase} 不存在, 文件编译失败</li>
 *   <li>Green: 创建 PerformanceRedisTestBase + pom 引入 testcontainers-redis</li>
 * </ul>
 */
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class PerformanceRedisTestBaseSanityTest extends PerformanceRedisTestBase {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void redis_isAvailable_viaTestcontainers() {
        redisTemplate.opsForValue().set("perf:test:ping", "PONG");
        assertThat(redisTemplate.opsForValue().get("perf:test:ping")).isEqualTo("PONG");
    }
}
