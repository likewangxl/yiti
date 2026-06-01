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
        if (!HttpMethod.POST.equals(request.method())) {
            sendResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "text/plain", "Only POST allowed");
            return;
        }

        String uri = request.uri().split("\\?")[0];
        String xmlBody = request.content().toString(StandardCharsets.UTF_8);

        if (props.isLogRequest()) {
            log.info("[SOAP-IN] uri={} body:\n{}", uri, xmlBody);
        }

        SoapEndpoint endpoint = endpointMap.get(uri);
        if (endpoint == null) {
            log.warn("[SOAP-IN] No endpoint for uri={}", uri);
            sendSoapFault(ctx, "Client", "Unknown service path: " + uri);
            return;
        }

        try {
            SoapMessage soapMessage = SoapEnvelopeParser.parse(xmlBody);
            String responseXml = endpoint.invoke(soapMessage);

            if (props.isLogRequest()) {
                log.info("[SOAP-OUT] uri={} response:\n{}", uri, responseXml);
            }
            sendResponse(ctx, HttpResponseStatus.OK, "text/xml; charset=UTF-8", responseXml);
        } catch (Exception e) {
            log.error("[SOAP-IN] Processing failed uri={}", uri, e);
            sendSoapFault(ctx, "Server", e.getMessage());
        }
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

    private void sendSoapFault(ChannelHandlerContext ctx, String faultCode, String faultString) {
        String fault = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <soap:Fault>
                      <faultcode>soap:%s</faultcode>
                      <faultstring>%s</faultstring>
                    </soap:Fault>
                  </soap:Body>
                </soap:Envelope>""".formatted(faultCode, escapeXml(faultString));
        sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "text/xml; charset=UTF-8", fault);
    }

    private static String escapeXml(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
