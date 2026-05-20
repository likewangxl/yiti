package com.bank.branch.platform.common.web.sidecar;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 边车 HTTP 客户端（基于 JDK 17 内置 java.net.http.HttpClient）。
 * <p>职责：
 * <ol>
 *   <li>自动在 URL 上拼接 global_flow_no / cons_flow_no 两个流水号（边车硬性规范）</li>
 *   <li>自动在 Headers 注入 serviceName / functionId（点对点调用必填）</li>
 *   <li>提供 JSON（11002）与 SOAP（11003）两种调用方法</li>
 *   <li>可选打印完整请求/响应（{@code platform.sidecar.log-request=true} 时）</li>
 * </ol>
 */
@Slf4j
public class SidecarHttpClient {

    private final SidecarProperties props;
    private final FlowIdGenerator idGen;
    private final HttpClient http;
    private final ObjectMapper json;

    public SidecarHttpClient(SidecarProperties props, FlowIdGenerator idGen, ObjectMapper json) {
        this.props = props;
        this.idGen = idGen;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
    }

    /**
     * POST + JSON 调用边车（11002 端口场景，走网关模式 — 不带 serviceName/functionId 头）。
     * <p>说明：边车 11003/11002 看到请求无 serviceName 头时自动转 7603 行内 ESB 网关。
     * 点对点调用需要 serviceName/functionId 安全中心审批，跨系统场景用不上。</p>
     *
     * @param path        目标微服务 path（前导斜杠可省）
     * @param body        业务请求对象（会被 Jackson 序列化为 JSON）
     * @param respType    响应反序列化目标类（传 String.class 拿原文）
     */
    public <T> T postJson(String path, Object body, Class<T> respType) {
        String url = buildUrl(path);

        String bodyJson;
        try {
            bodyJson = json.writeValueAsString(body == null ? new java.util.HashMap<>() : body);
        } catch (Exception e) {
            throw new RuntimeException("边车 JSON 请求体序列化失败: " + e.getMessage(), e);
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();

        if (props.isLogRequest()) {
            log.info("[Sidecar][JSON-REQ] POST {}\n  body: {}", url, bodyJson);
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("边车 JSON 调用 IO 异常: " + e.getMessage(), e);
        }

        if (props.isLogRequest()) {
            log.info("[Sidecar][JSON-RSP] status={} body={}", resp.statusCode(), resp.body());
        }
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("边车 JSON 调用失败 status=" + resp.statusCode() + " body=" + resp.body());
        }
        if (respType == String.class) {
            @SuppressWarnings("unchecked") T cast = (T) resp.body();
            return cast;
        }
        try {
            return json.readValue(resp.body(), respType);
        } catch (Exception e) {
            throw new RuntimeException("边车 JSON 响应反序列化失败: " + e.getMessage() + " body=" + resp.body(), e);
        }
    }

    /**
     * POST + SOAP/XML 调用边车（11003 端口场景，走网关模式 — 不带 serviceName/functionId 头）。
     * <p>说明：边车 11003 看到请求无 serviceName 头时自动转 7603 行内 ESB 网关，
     * 网关再路由到下游微服务（如 UIAS S120030044）。这是跨系统调用的标准模式。</p>
     *
     * @param path     目标微服务 path（如 /services/S120030044）
     * @param soapXml  已经拼好的完整 SOAP Envelope 字符串
     * @return         边车响应的 SOAP 响应 XML 原文
     */
    public String postSoap(String path, String soapXml) {
        String url = buildUrl(path);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("Accept", "text/xml")
                .header("SOAPAction", "")
                .POST(HttpRequest.BodyPublishers.ofString(soapXml, StandardCharsets.UTF_8))
                .build();

        if (props.isLogRequest()) {
            log.info("[Sidecar][SOAP-REQ] POST {}\n  body:\n{}", url, soapXml);
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("边车 SOAP 调用 IO 异常: " + e.getMessage(), e);
        }

        if (props.isLogRequest()) {
            log.info("[Sidecar][SOAP-RSP] status={} body:\n{}", resp.statusCode(), resp.body());
        }
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("边车 SOAP 调用失败 status=" + resp.statusCode() + " body=" + resp.body());
        }
        return resp.body();
    }

    /** 拼出带两个流水号 query 的完整 URL */
    private String buildUrl(String path) {
        String globalFlowNo = idGen.genGlobalFlowNo();
        String consFlowNo = idGen.genConsFlowNo();
        return props.getBaseUrl() + (path.startsWith("/") ? path : "/" + path)
                + "?global_flow_no=" + URLEncoder.encode(globalFlowNo, StandardCharsets.UTF_8)
                + "&cons_flow_no=" + URLEncoder.encode(consFlowNo, StandardCharsets.UTF_8);
    }
}
