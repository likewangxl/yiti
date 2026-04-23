package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.DataTaskStatusReqDTO;
import com.bank.branch.platform.performance.controller.dto.DataTaskStatusRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部数据任务状态上报 REST 控制器（V1.1 Task P6.1）.
 *
 * <p>对齐 03 §G.1：POST /api/data-task/status。外部数据同步系统完成 T 日数据上报后调用本接口
 * 告知本模块数据就绪状态，由本模块按 dataType 触发对应版本发布 / KPI 回算流程。
 *
 * <p><strong>鉴权策略</strong>：03 §G.1 钦定外部接口走 API Token（Header {@code X-Api-Token}），
 * 由 system-governance-center 统一校验，<em>不</em>走前端登录态。因此本端点不标注
 * {@code @BizAuth}（BizAuthConsistencyArchTest 仅校验"若声明 BizAuth 则必须为 PERF_CONFIG"，
 * 不要求所有 Controller 方法都声明）。
 *
 * <p><strong>审计</strong>：外部上报为关键事件，必须记录。走统一 {@code @AuditLog}
 * （action=DATA_TASK_STATUS_REPORT，resourceType=EXT_DATA_TASK，非人工操作故 reasonRequired=false）。
 *
 * <p><strong>幂等</strong>：以 {@code taskId} 为幂等键，由下游 Service 统一保证；
 * 重复上报返回既有 {@link DataTaskStatusRespDTO#getPerfRunTaskId()}。
 *
 * <p><strong>架构守护</strong>：
 * <ul>
 *   <li>{@code NoEntityInControllerArchTest}：返回 DTO 而非 entity</li>
 *   <li>{@code BizAuthConsistencyArchTest}：未声明 {@code @BizAuth}，无需校验 bizType 单档约束</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/data-task")
@Tag(name = "External Data Task", description = "外部数据任务状态上报（V1.1）")
@Validated
@RequiredArgsConstructor
public class DataTaskController {

    /**
     * 接收外部数据同步系统的上报.
     *
     * <p>处理流程（Task P6.2 交付）：
     * <ol>
     *   <li>以 {@code taskId} 查 {@code perf_run_task}，存在则幂等返回</li>
     *   <li>不存在则创建 EXT_DATA 任务记录</li>
     *   <li>{@code status=SUCCESS} 时按 {@code dataType} 触发后续计算管线</li>
     *   <li>{@code status=FAILED} 时只记录，不触发计算</li>
     * </ol>
     *
     * @param req 上报请求
     * @return 本次受理结果（含 taskId / accepted / perfRunTaskId）
     */
    @PostMapping("/status")
    @Operation(summary = "外部数据任务状态上报")
    @AuditLog(action = "DATA_TASK_STATUS_REPORT", resourceType = "EXT_DATA_TASK")
    public ResponseWrapper<DataTaskStatusRespDTO> reportStatus(@Valid @RequestBody DataTaskStatusReqDTO req) {
        log.info("[DataTaskController.reportStatus] taskId={}, dataType={}, dataDate={}, status={}",
                req.getTaskId(), req.getDataType(), req.getDataDate(), req.getStatus());
        // Task P6.1 骨架：真实处理在 P6.2 交付（替换 DataTaskApi.reportDataTaskStatus UOE）
        throw new UnsupportedOperationException("V1.1 delivered");
    }
}
