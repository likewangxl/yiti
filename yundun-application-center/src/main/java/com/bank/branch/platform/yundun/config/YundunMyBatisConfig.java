package com.bank.branch.platform.yundun.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 浦爱云盾模块 MyBatis-Plus Mapper 扫描配置。 */
@Configuration
@MapperScan("com.bank.branch.platform.yundun.mapper")
public class YundunMyBatisConfig {
}
