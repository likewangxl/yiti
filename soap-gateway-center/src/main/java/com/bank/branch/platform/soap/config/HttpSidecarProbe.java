package com.bank.branch.platform.soap.config;

import com.bank.branch.platform.common.web.sidecar.SidecarProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * {@link SidecarProbe} 生产实现：用 JDK 内置 {@link HttpClient} GET 边车健康端口
 * （{@code platform.sidecar.health-base-url}，默认 {@code http://127.0.0.1:8089}）。
 *
 * <p>连接 / 读取超时取 {@link SidecarProperties} 的 connect/read 超时；任何异常（含超时、连接拒绝）
 * 一律吞掉返回 {@code null}，交由 {@link SidecarRegistrationChecker} 按「未就绪」重试。</p>
 */
@Slf4j
@Component
public class HttpSidecarProbe implements SidecarProbe {

    private final SidecarProperties props;
    private final HttpClient httpClient;

    public HttpSidecarProbe(SidecarProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
    }

    @Override
    public String get(String path) {
        String url = props.getHealthBaseUrl() + path;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            return body == null ? null : body.trim();
        } catch (Exception e) {
            log.warn("[Sidecar] 调用 {} 失败/超时: {}", url, e.getMessage());
            return null;
        }
    }
}
