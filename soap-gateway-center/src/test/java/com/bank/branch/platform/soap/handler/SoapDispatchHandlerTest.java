package com.bank.branch.platform.soap.handler;

import com.bank.branch.platform.soap.config.SoapNettyProperties;
import com.bank.branch.platform.soap.endpoint.SoapEndpoint;
import com.bank.branch.platform.soap.parse.SoapMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SoapDispatchHandler 路由分发单元测试（用 Netty EmbeddedChannel）。
 *
 * <p>聚焦“按 uri 路由到对应 SoapEndpoint 并回写其响应”这一行为；
 * 端点内部业务由 {@code AxlryPrsRvrSysSvcEndpointTest} 单独覆盖,这里用桩端点隔离。</p>
 */
class SoapDispatchHandlerTest {

    /** 桩端点：servicePath=/S080021264,invoke 返回固定标记串。 */
    private static class StubEndpoint implements SoapEndpoint {
        @Override
        public String getServicePath() {
            return "/S080021264";
        }

        @Override
        public String invoke(SoapMessage message) {
            return "<resp>ENDPOINT_HIT:" + message.getBodyLocalName() + "</resp>";
        }
    }

    private FullHttpRequest postRequest(String uri, String body) {
        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, uri,
                Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)));
        req.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/xml; charset=UTF-8");
        req.headers().set("soapAction", "urn:/AxlryPrsRvrSysSvc");
        return req;
    }

    private String sampleXml() throws Exception {
        return StreamUtils.copyToString(
                new ClassPathResource("soap/sample-axlry-prs-rvr.xml").getInputStream(),
                StandardCharsets.UTF_8);
    }

    @Test
    void routesByUriToEndpoint_andReturnsItsResponse() throws Exception {
        SoapDispatchHandler handler = new SoapDispatchHandler(new SoapNettyProperties(), List.of(new StubEndpoint()));
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        ch.writeInbound(postRequest("/S080021264", sampleXml()));

        FullHttpResponse resp = ch.readOutbound();
        assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
        String content = resp.content().toString(StandardCharsets.UTF_8);
        assertThat(content).contains("ENDPOINT_HIT:ReqAxlryPrsRvrSysSvc");
    }

    @Test
    void unknownServicePath_returns404() throws Exception {
        SoapDispatchHandler handler = new SoapDispatchHandler(new SoapNettyProperties(), List.of(new StubEndpoint()));
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        ch.writeInbound(postRequest("/UNKNOWN", sampleXml()));

        FullHttpResponse resp = ch.readOutbound();
        assertThat(resp.status()).isEqualTo(HttpResponseStatus.NOT_FOUND);
    }

    @Test
    void isHealthUri_returnsZero_withoutHittingEndpoint() {
        SoapDispatchHandler handler = new SoapDispatchHandler(new SoapNettyProperties(), List.of(new StubEndpoint()));
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // 健康检查:GET /isHealth,无 SOAP body,不进入业务端点
        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/isHealth");
        ch.writeInbound(req);

        FullHttpResponse resp = ch.readOutbound();
        assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat(resp.content().toString(StandardCharsets.UTF_8)).isEqualTo("0");
    }
}
