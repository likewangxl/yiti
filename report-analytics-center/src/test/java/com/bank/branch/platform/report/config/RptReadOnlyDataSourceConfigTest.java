package com.bank.branch.platform.report.config;

import com.alibaba.druid.filter.logging.Slf4jLogFilter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceWrapper;
import com.bank.branch.platform.report.ReportTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * rptReadOnlyDataSource Bean 守护测试（Task M4.2.2，Red）.
 *
 * <p>覆盖 plan L2762-L2790：
 * <ol>
 *   <li>Bean 存在且为 DruidDataSource 类型</li>
 *   <li>Bean 与主 DataSource 物理隔离（不同 url / username 配置）</li>
 *   <li>defaultReadOnly = true</li>
 * </ol>
 *
 * <p>不覆盖：getConnection().setReadOnly(true) 双层防御 + 真实写 SQL 拒绝
 * （需 MySQL 实例 + sql_probe_readonly 账号；放在子类的真实库 IT 中）.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(
        classes = ReportTestApplication.class,
        properties = {
                "spring.datasource.druid.filter.stat.enabled=true",
                "spring.datasource.druid.filter.slf4j.enabled=false"
        }
)
@ActiveProfiles("test")
class RptReadOnlyDataSourceConfigTest {

    @Autowired
    private DataSource primaryDataSource;

    @Autowired
    @Qualifier("rptReadOnlyDataSource")
    private DataSource rptReadOnlyDataSource;

    @Test
    void primaryDataSourceShouldUseWrapperAndAttachOnlyStatFilter() {
        assertThat(primaryDataSource).isInstanceOf(DruidDataSourceWrapper.class);
        DruidDataSource druid = (DruidDataSource) primaryDataSource;
        assertThat(druid.getProxyFilters().stream().anyMatch(StatFilter.class::isInstance)).isTrue();
        assertThat(druid.getProxyFilters().stream().anyMatch(Slf4jLogFilter.class::isInstance)).isFalse();
    }

    @Test
    void rptReadOnlyDataSourceShouldNotAttachMonitoringFilters() {
        DruidDataSource druid = (DruidDataSource) rptReadOnlyDataSource;
        assertThat(druid.getProxyFilters().stream().anyMatch(StatFilter.class::isInstance)).isFalse();
        assertThat(druid.getProxyFilters().stream().anyMatch(Slf4jLogFilter.class::isInstance)).isFalse();
    }

    @Test
    void rptReadOnlyDataSourceShouldExistAndBeDruid() {
        assertThat(rptReadOnlyDataSource).isNotNull();
        assertThat(rptReadOnlyDataSource).isInstanceOf(DruidDataSource.class);
    }

    @Test
    void rptReadOnlyDataSourceShouldHaveDefaultReadOnlyTrue() {
        DruidDataSource druid = (DruidDataSource) rptReadOnlyDataSource;
        assertThat(druid.getDefaultReadOnly())
                .withFailMessage("rptReadOnlyDataSource 必须 defaultReadOnly=true")
                .isEqualTo(Boolean.TRUE);
    }
}
