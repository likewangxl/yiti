package com.bank.branch.platform.soap.endpoint;

import com.bank.branch.platform.soap.config.SidecarUniauthProperties;
import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.bank.branch.platform.soap.endpoint.bind.ReqAxlryPrsRvrSysSvcType;
import com.bank.branch.platform.soap.parse.SoapBodyBinder;
import com.bank.branch.platform.soap.parse.SoapMessage;
import com.bank.branch.platform.soap.service.CallPuDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AxlryPrsRvrSysSvc 服务的 SOAP 端点（外部渠道"新 Call 浦" XazcCallPuSvr）。
 *
 * <p>接入链路：Netty 收到 SOAP（uri=服务号 {@code /S080021264}）→ {@code SoapDispatchHandler}
 * 按 servicePath 路由到本端点 → 本端点把 SOAP body 拆成强类型对象,取出业务体 {@code SvcBody.Parm}
 * （一段 callpu JSON）反序列化为 {@link CallPuRequest} → 委托 {@link CallPuDispatchService} 执行业务。</p>
 *
 * <p>分发键取自内层 {@code Parm} JSON 的 {@code RuleName}（如 {@code PERF_LIST}），
 * 与 HTTP 入口 {@code POST /api/callpu} 复用同一套业务实现。</p>
 *
 * <p>响应：当前阶段暂不定义专用响应对象,把 callpu 业务结果（{@code ReturnCd/RspMsg}）以 JSON
 * 内嵌到一个占位 SOAP 信封返回,保证对 Axis2 客户端可解析。待响应规范确定后替换 {@link #wrapSoap}。</p>
 */
@Slf4j
@Component
public class AxlryPrsRvrSysSvcEndpoint implements SoapEndpoint {

    /** 服务号（即报文 uri）；{@code SoapDispatchHandler} 以此为路由键。 */
    private static final String SERVICE_PATH = "/S080021264";

    private final CallPuDispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final RspEnvelopeBuilder rspEnvelopeBuilder;
    private final SidecarUniauthProperties uniauthProperties;

    public AxlryPrsRvrSysSvcEndpoint(CallPuDispatchService dispatchService,
                                     ObjectMapper objectMapper,
                                     RspEnvelopeBuilder rspEnvelopeBuilder,
                                     SidecarUniauthProperties uniauthProperties) {
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
        this.rspEnvelopeBuilder = rspEnvelopeBuilder;
        this.uniauthProperties = uniauthProperties;
    }

    @Override
    public String getServicePath() {
        return SERVICE_PATH;
    }

    @Override
    public String invoke(SoapMessage message) {
        // 1) SOAP body → 强类型对象（命名空间含服务号,由 binder 统一剥离）
        ReqAxlryPrsRvrSysSvcType req = SoapBodyBinder.bind(message, ReqAxlryPrsRvrSysSvcType.class);
        ReqAxlryPrsRvrSysSvcType.SvcBody body = req.getSvcBody();
        if (body == null || body.getParm() == null) {
            throw new IllegalArgumentException("SOAP SvcBody/Parm 缺失");
        }

        // 2) SvcBody.Parm（callpu JSON）→ CallPuRequest
        CallPuRequest callPuRequest = parseParm(body.getParm());
        log.info("[SOAP-IN] service={} SrvicName1={} RuleName={}",
                SERVICE_PATH, body.getSrvicName1(),
                callPuRequest.getRuleName());

        // 3) 委托共用分发服务执行业务
        CallPuResponse response = dispatchService.dispatch(callPuRequest);

        // 4) 按实际响应报文结构装配 SOAP 响应
        //    TargetSysId/BackendSysId/BackendSeqNo 前 4 位取本系统配置(platform.sidecar.uniauth.source-sys-id),
        //    与请求里的 SourceSysId 无关。
        return rspEnvelopeBuilder.build(uniauthProperties.getSourceSysId(), response);
    }

    /** 解析 callpu 载荷 JSON。 */
    private CallPuRequest parseParm(String parmJson) {
        try {
            return objectMapper.readValue(parmJson, CallPuRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Parm JSON 解析失败: " + e.getMessage(), e);
        }
    }
}
