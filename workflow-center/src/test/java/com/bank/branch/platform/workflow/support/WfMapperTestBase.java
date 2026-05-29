package com.bank.branch.platform.workflow.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * workflow-center 单线程 Mapper IT 基类。
 * <p>
 * 继承此类的测试方法默认 {@code @Transactional + @Rollback(true)}，
 * 测试完毕自动回滚，无需手工清理测试数据。
 * <br>
 * 数据库：连接本地 yiti 开发库，使用 application-test.yml 覆盖配置。
 * Flowable ProcessEngine 全部排除，避免 ACT_* 表初始化失败。
 * </p>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {WfTestApp.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@Rollback(true)
public abstract class WfMapperTestBase {
}
