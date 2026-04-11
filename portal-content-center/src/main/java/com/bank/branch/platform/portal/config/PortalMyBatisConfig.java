package com.bank.branch.platform.portal.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * portal-content-center MyBatis 配置
 * 扫描 portal 模块下的所有 Mapper 接口
 */
@Configuration
@MapperScan(basePackages = "com.bank.branch.platform.portal.mapper")
public class PortalMyBatisConfig {
}
