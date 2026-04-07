package com.bank.branch.platform;

import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试：验证 ApplicationContext 可正常加载。
 * 使用 H2 内存数据库，通过 application-test.yml 初始化 schema，
 * 同时排除 Flowable 和 Redis 等外部依赖的自动配置。
 *
 * 与 SmokeTest 共享同一个 ApplicationContext（相同 @SpringBootTest 配置），
 * 避免重复加载 Spring 上下文。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
class BranchPlatformApplicationTest {

    @Test
    void contextLoads() {
        // ApplicationContext 加载成功即通过
    }
}
