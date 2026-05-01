package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L3 集成测试 - 冒烟测试
 * 验证 bootstrap ApplicationContext 可正常加载，H2数据库初始化成功，
 * 核心数据可正常查询。
 *
 * 注意: 排除 Flowable 自动配置（Flowable 引擎初始化需要专用表和流程文件），
 * 当前阶段只验证 MyBatis + H2 的基础连通性。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
class SmokeTest {

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("冒烟测试: 应用上下文 + H2 数据初始化全链路验证")
    void smokeAll() {
        // 1. ApplicationContext 加载正常
        assertThat(context).isNotNull();

        // 2. JdbcTemplate 可用
        assertThat(jdbcTemplate).isNotNull();

        // 3. auth 表: PT_USER 有数据
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PT_USER", Long.class);
        assertThat(userCount).isGreaterThanOrEqualTo(3);

        // 4. governance 表: SYS_DICT 有数据
        Long dictCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SYS_DICT", Long.class);
        assertThat(dictCount).isGreaterThanOrEqualTo(4);

        // 5. workflow 表: BIZ_PROCESS_MAP 有数据
        Long mapCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BIZ_PROCESS_MAP", Long.class);
        assertThat(mapCount).isGreaterThanOrEqualTo(3);
    }
}
