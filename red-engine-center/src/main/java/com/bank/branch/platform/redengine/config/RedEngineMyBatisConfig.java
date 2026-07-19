package com.bank.branch.platform.redengine.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 红色引擎模块 MyBatis-Plus Mapper 扫描配置。
 * 分页/审计填充等拦截器由 common-db 自动配置提供，此处仅声明扫描包。
 */
@Configuration
@MapperScan("com.bank.branch.platform.redengine.mapper")
public class RedEngineMyBatisConfig {
}
