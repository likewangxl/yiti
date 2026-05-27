package com.bank.branch.platform.common.web.sidecar;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 边车（Sidecar）通用配置。
 * <p>对应银行 ESF 边车 HTTP+JSON / HTTP+SOAP 通道：
 * <ul>
 *   <li>11002 = HTTP+JSON（默认 base-url 不指向此端口；按需要切换）</li>
 *   <li>11003 = HTTP+SOAP（统一认证走此端口）</li>
 *   <li>8089  = 健康探测端口（ishealth/up/down/getstatus）</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "platform.sidecar")
public class SidecarProperties {

    /** 边车业务端口 baseUrl（默认指向 11003 HTTP+SOAP，统一认证场景） */
    private String baseUrl = "http://127.0.0.1:11003";

    /** 边车健康探测端口 baseUrl（默认 8089） */
    private String healthBaseUrl = "http://127.0.0.1:8089";

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 3000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 10000;

    /** 开发期打印请求/响应日志；生产建议关或在拦截器层做敏感字段脱敏 */
    private boolean logRequest = true;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getHealthBaseUrl() { return healthBaseUrl; }
    public void setHealthBaseUrl(String healthBaseUrl) { this.healthBaseUrl = healthBaseUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public boolean isLogRequest() { return logRequest; }
    public void setLogRequest(boolean logRequest) { this.logRequest = logRequest; }
}
