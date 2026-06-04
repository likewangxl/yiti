package com.bank.branch.platform.soap.controller;

import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.controller.dto.CallPuResponse;
import com.bank.branch.platform.soap.service.CallPuDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * callpu 渠道接收 controller（手机端绩效审批网关 HTTP 入口）。
 *
 * <p>统一入口 {@code POST /api/callpu}，按请求体 {@code RuleName} 分发。分发与业务逻辑下沉到
 * {@link CallPuDispatchService}，以便与 Netty SOAP 端点复用同一套实现。</p>
 *
 * <p>响应为手机端约定的 {@code {ReturnCd, RspMsg}} 信封（成功 ReturnCd="0"，失败 ReturnCd="99"，
 * 始终 HTTP 200，前端以 {@code response.ReturnCd == "0"} 判定成功）。{@code dispatch} 内统一兜底，
 * 业务异常降级为失败信封，避免被全局异常处理器改写成平台标准响应格式。</p>
 *
 * <p>鉴权说明：外部渠道入口，身份认证由上游 callpu/ESB 完成（员工号随报文传入），
 * 故未挂 {@code @BizAuth}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/callpu")
@RequiredArgsConstructor
public class CallPuController {

    private final CallPuDispatchService dispatchService;

    /**
     * callpu 统一分发入口（委托 {@link CallPuDispatchService}）。
     *
     * @param request callpu 请求体（含 RuleName 与业务参数）
     * @return 统一 callpu 响应信封
     */
    @PostMapping
    public CallPuResponse dispatch(@RequestBody CallPuRequest request) {
        return dispatchService.dispatch(request);
    }
}
