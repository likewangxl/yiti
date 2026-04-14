package com.bank.branch.platform.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * bootstrap 运行期显式注册跨模块 MyBatis Mapper。
 * <p>
 * 业务模块以 JAR 依赖方式装配到 bootstrap 时，
 * MyBatis 默认扫描无法稳定发现依赖模块中的 mapper 接口，
 * 因此这里集中补齐 auth / governance / workflow 的 mapper 扫描。
 * </p>
 */
@Configuration
@MapperScan(basePackages = {
        "com.bank.branch.platform.auth.mapper",
        "com.bank.branch.platform.governance.mapper",
        "com.bank.branch.platform.workflow.mapper"
})
public class BootstrapMyBatisConfig {
}
