package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Service 层纯单元测试基类.
 * <p>使用 Mockito 隔离依赖, 不启动 Spring 容器.
 */
@ExtendWith(MockitoExtension.class)
public abstract class PerformanceServiceTestBase {
}
