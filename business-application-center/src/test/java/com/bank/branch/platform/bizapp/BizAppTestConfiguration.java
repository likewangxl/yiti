package com.bank.branch.platform.bizapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 业务申请中心测试入口配置。
 * 仅用于独立 Mapper 集成测试（非 Controller 集成测试）。
 * Controller 集成测试使用 AbstractControllerIntegrationTest.TestApp。
 */
@SpringBootApplication(scanBasePackages = "com.bank.branch.platform.bizapp")
@MapperScan("com.bank.branch.platform.bizapp.mapper")
public class BizAppTestConfiguration {
}
