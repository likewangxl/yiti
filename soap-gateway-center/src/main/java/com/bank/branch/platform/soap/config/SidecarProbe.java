package com.bank.branch.platform.soap.config;

/**
 * 边车（sidecar）健康/注册探测端口的极简 GET 客户端抽象。
 *
 * <p>抽出接口便于对 {@link SidecarRegistrationChecker} 的轮询逻辑做单元测试
 * （生产实现 {@link HttpSidecarProbe} 走 JDK HttpClient 调 8089 健康端口）。</p>
 */
public interface SidecarProbe {

    /**
     * GET 边车健康端点（{@code healthBaseUrl + path}），返回响应体（trim）。
     *
     * @param path 端点路径，如 {@code /isready} / {@code /up}
     * @return 响应体文本（已 trim）；连接失败 / 超时 / 任何异常时返回 {@code null}
     */
    String get(String path);
}
