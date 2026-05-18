package com.bank.branch.platform.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户管理相关配置。
 * 默认密码生产环境必须通过外部 application-prod.yml 或环境变量覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.user")
public class AuthUserProperties {

    /** 重置密码的默认值（BCrypt 加密前的明文） */
    private String defaultPassword = "Branch@2026";

    /** 批量接口 ids 上限 */
    private int maxBatchIds = 50;
}
