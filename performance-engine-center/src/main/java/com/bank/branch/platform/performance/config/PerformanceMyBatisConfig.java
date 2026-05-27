package com.bank.branch.platform.performance.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描配置.
 * <p>扫描本模块 com.bank.branch.platform.performance.mapper 下全部 Mapper 接口.
 */
@Configuration
@MapperScan({"com.bank.branch.platform.performance.mapper",
             "com.bank.branch.platform.performance.eval.mapper"})
public class PerformanceMyBatisConfig {
}
