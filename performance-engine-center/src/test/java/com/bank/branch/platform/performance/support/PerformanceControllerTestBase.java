package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controller IT 基类.
 * <p>启动完整 Spring 上下文, 使用 MockMvc 不启动 Tomcat, 事务自动回滚.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PerfTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback(true)
public abstract class PerformanceControllerTestBase {

    @Autowired
    protected MockMvc mockMvc;
}
