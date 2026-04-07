package com.bank.branch.platform.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 启动类，为 @SpringBootTest 提供配置锚点。
 * 仅加载 auth 模块内的 Bean（通过 @MapperScan + @SpringBootApplication 自动扫描）。
 * 不引入 Web MVC 和 Redis 等外部依赖。
 */
@SpringBootApplication(
        scanBasePackages = "com.bank.branch.platform.auth"
)
@MapperScan("com.bank.branch.platform.auth.mapper")
public class AuthTestConfiguration {
}
