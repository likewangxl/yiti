package com.bank.branch.platform.soap.handler;

import com.bank.branch.platform.soap.config.SoapNettyProperties;
import com.bank.branch.platform.soap.endpoint.SoapEndpoint;
import com.bank.branch.platform.soap.parse.SoapEnvelopeParser;
import com.bank.branch.platform.soap.parse.SoapMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@ChannelHandler.Sharable
public class SoapDispatchHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final SoapNettyProperties props;
    private final Map<String, SoapEndpoint> endpointMap;

    public SoapDispatchHandler(SoapNettyProperties props, List<SoapEndpoint> endpoints) {
        this.props = props;
        this.endpointMap = endpoints.stream()
                .collect(Collectors.toMap(SoapEndpoint::getServicePath, Function.identity()));
        log.info("Registered SOAP endpoints: {}", endpointMap.keySet());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        // 路由键取 uri 路径(去掉查询串),对应报文里的服务号,如 /S080021264
        String servicePath = stripQuery(uri);
        String body = request.content().toString(StandardCharsets.UTF_8);

        if (props.isLogRequest()) {
            StringBuilder headers = new StringBuilder();
            request.headers().forEach(h -> headers.append("  ").append(h.getKey()).append(": ").append(h.getValue()).append('\n'));
            log.info("[SOAP-IN] 收到报文 remote={} method={} uri={}\nheaders:\n{}body:\n{}",
                    ctx.channel().remoteAddress(), request.method(), uri, headers, body);
        }

        // 在 uri 处分流:isHealth → 健康检查;其余按服务号路由到业务端点
        if (isHealthCheck(servicePath)) {
            sendResponse(ctx, HttpResponseStatus.OK, "text/plain; charset=UTF-8",
                    String.valueOf(healthCheck()));
            return;
        }

        SoapEndpoint endpoint = endpointMap.get(servicePath);
        if (endpoint == null) {
            log.warn("[SOAP-IN] 无匹配端点 servicePath={} 已注册={}", servicePath, endpointMap.keySet());
            sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "text/plain; charset=UTF-8",
                    "no endpoint for " + servicePath);
            return;
        }

        try {
            // 解析 SOAP 信封并补充 soapAction 头,交由端点处理
            SoapMessage message = SoapEnvelopeParser.parse(body);
            message.setSoapAction(headerValue(request, "soapAction"));
            String responseXml = endpoint.invoke(message);
            sendResponse(ctx, HttpResponseStatus.OK, "text/xml; charset=UTF-8", responseXml);
        } catch (Exception e) {
            log.error("[SOAP-IN] 端点处理失败 servicePath={}", servicePath, e);
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "text/xml; charset=UTF-8",
                    soapFault(e.getMessage()));
        }
    }

    /** 去掉 uri 的查询串,只保留路径部分作为路由键。 */
    private String stripQuery(String uri) {
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    /** uri 是否为健康检查路径(去掉前导斜杠后忽略大小写匹配 ishealth)。 */
    private boolean isHealthCheck(String servicePath) {
        String path = servicePath.startsWith("/") ? servicePath.substring(1) : servicePath;
        return "ishealth".equalsIgnoreCase(path);
    }

    /**
     * 边车探活:宿主应用健康返回 0,不健康返回 1。
     * 暂未接入具体健康判断逻辑,恒定上报健康。
     */
    private int healthCheck() {
        return 0;
    }

    private String headerValue(FullHttpRequest request, String name) {
        return request.headers().get(name);
    }

    /** 构造 SOAP Fault 信封(端点处理异常时回写)。 */
    private String soapFault(String message) {
        String safe = message == null ? "unknown error"
                : message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body><soap:Fault>"
                + "<faultcode>soap:Server</faultcode>"
                + "<faultstring>" + safe + "</faultstring>"
                + "</soap:Fault></soap:Body></soap:Envelope>";
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("SOAP channel idle, closing: {}", ctx.channel().remoteAddress());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("SOAP channel exception: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                              String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response);
    }
}
