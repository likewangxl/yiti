package com.bank.branch.platform.auth.location;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 机构位置能力配置。默认关闭，密钥只允许由环境或外部配置注入。 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.org-location")
public class OrgLocationProperties {

    private boolean storageEnabled = false;
    private boolean geocodingEnabled = false;

    /** 高德 Web 服务 Key；代码和日志均不提供默认值。 */
    private String apiKey;

    /** 候选确认令牌签名密钥；代码和日志均不提供默认值。 */
    private String signingSecret;

    private int connectTimeoutMillis = 3000;
    private int requestTimeoutMillis = 5000;
    private int maxResponseBytes = 1024 * 1024;
    private int maxCandidates = 5;
    private int maxPreviewRequestsPerMinute = 10;
    private long candidateTtlSeconds = 300;
}
