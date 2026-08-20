package com.bank.branch.platform.common.db;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DruidDefaultTimeoutTest {

    @Test
    void shouldNotApplyTenSecondNetworkTimeoutWhenTimeoutIsNotConfigured() throws Exception {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:druid-default-timeout;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setValidationQuery("SELECT 1");

        try {
            // 初始化会触发 Druid 对连接和读超时默认值的处理，但 initialSize=0 不连接真实数据库。
            dataSource.init();

            assertThat(dataSource.getConnectTimeout()).isZero();
            assertThat(dataSource.getSocketTimeout()).isZero();
        } finally {
            dataSource.close();
        }
    }
}
