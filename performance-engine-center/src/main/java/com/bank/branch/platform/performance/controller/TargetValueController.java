package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueBatchReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueReqDTO;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.facade.assembler.TargetAssembler;
import com.bank.branch.platform.performance.service.TargetValueService;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueCmd;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标值 REST 控制器 (3 个端点, 对齐 PT_RESOURCE P_PERF_TGT_V_*).
 *
 * <p>路径映射:
 * <ul>
 *   <li>GET  /api/perf/target-values         → P_PERF_TGT_V_LIST</li>
 *   <li>POST /api/perf/target-values         → P_PERF_TGT_V_ADD (单条 upsert)</li>
 *   <li>POST /api/perf/target-values/batch   → P_PERF_TGT_V_BAT (批量 upsert, &lt;=500)</li>
 * </ul>
 *
 * <p>异常策略: Controller 不做 try-catch, PerfException 冒泡至全局异常处理器,
 * 业务错误统一以 200 + 错误码返回; JSR-303 校验失败由 MethodArgumentNotValidException
 * 全局处理器转 400.
 *
 * <p>批量 500 守卫: DTO {@code @Size(max=500)} (400 语义) + Service 层 BATCH_UPPER_LIMIT
 * (PERF-40910) 双重防线, 避免 Controller 被绕过导致 DB 压力。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/target-values")
@Tag(name = "Performance Target Value", description = "目标值 upsert/查询")
@Validated
@RequiredArgsConstructor
public class TargetValueController {

    private final CurrentUserApi currentUserApi;
    private final TargetValueService targetValueService;

    /**
     * 按方案分页查询目标值.
     *
     * <p>V1.3 R1.3 改造：切换到 {@link TargetValueService#pageWithScope} 以启用
     * 基于 {@code PerfScopeHelper} 的数据范围注入（普通绩效配置员仅见自建目标值）。
     *
     * <p>planId 仍保留 {@code @NotBlank} 以满足客户端「必须在方案上下文内查询」的使用惯例,
     * Service 层 pageWithScope 已允许 planId 为空, 但 Controller 层主动收紧到非空避免
     * 泄露全库目标值;{@code listByPlan} 路径保留为内部 Service 方法供其他编排复用。
     */
    @GetMapping
    @Operation(summary = "分页查询目标值")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<TargetValueDTO> list(
            @RequestParam(value = "planId") @NotBlank String planId,
            @RequestParam(value = "subjectType", required = false) String subjectType,
            @RequestParam(value = "subjectId", required = false) String subjectId,
            @RequestParam(value = "cycleKey", required = false) String cycleKey,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[TargetValueController.list] planId={}, subjectType={}, subjectId={}, cycleKey={}, pageNo={}, pageSize={}",
                planId, subjectType, subjectId, cycleKey, pageNo, pageSize);
        // V1.3 R1.3：改用 pageWithScope 注入 PerfScopeHelper 数据范围（原 listByPlan 保留为 Service 层内部方法）
        PageResult<PerfTargetValue> raw = targetValueService.pageWithScope(
                planId, subjectType, subjectId, cycleKey, pageNo, pageSize);
        List<TargetValueDTO> dtos = new ArrayList<>(raw.getRecords().size());
        for (PerfTargetValue v : raw.getRecords()) {
            dtos.add(TargetAssembler.toDto(v));
        }
        return ResponseWrapper.page(PageResult.of(raw.getPageNo(), raw.getPageSize(), raw.getTotal(), dtos));
    }

    /**
     * 单值 upsert. 冲突 (UK 相同) 时更新 target_value / base_value, 否则新增。
     *
     * <p>返回受影响行数 (MySQL 语义: 新增 1 / 更新 2)。
     */
    @PostMapping
    @Operation(summary = "单值目标 upsert")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "TARGET_VALUE")
    public ResponseWrapper<Integer> create(@Valid @RequestBody UpsertTargetValueReqDTO req) {
        log.info("[TargetValueController.create] planId={}, subjectType={}, subjectId={}, cycleKey={}, metricCode={}",
                req.getPlanId(), req.getSubjectType(), req.getSubjectId(), req.getCycleKey(), req.getMetricCode());
        UpsertTargetValueCmd cmd = UpsertTargetValueCmd.builder()
                .planId(req.getPlanId())
                .subjectType(req.getSubjectType())
                .subjectId(req.getSubjectId())
                .cycleKey(req.getCycleKey())
                .metricCode(req.getMetricCode())
                .targetValue(req.getTargetValue())
                .baseValue(req.getBaseValue())
                .operator(currentUserApi.getCurrentEmpId())
                .build();
        int affected = targetValueService.upsertOne(cmd);
        return ResponseWrapper.success(affected);
    }

    /**
     * 批量 upsert (单批上限 500, 超过则 400; 空列表 400).
     *
     * <p>Controller 层双重防线: DTO {@code @NotEmpty} + {@code @Size(max=500)} 先过滤,
     * Service 层仍保留 BATCH_UPPER_LIMIT 守卫以兜底; 调用方若绕过 Controller 直接
     * 调 Service, Service 层抛 PERF-40910。
     */
    @PostMapping("/batch")
    @Operation(summary = "批量目标 upsert (<=500)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "TARGET_VALUE")
    public ResponseWrapper<Integer> batch(@Valid @RequestBody UpsertTargetValueBatchReqDTO req) {
        log.info("[TargetValueController.batch] size={}", req.getValues().size());
        String operator = currentUserApi.getCurrentEmpId();
        List<PerfTargetValue> list = new ArrayList<>(req.getValues().size());
        for (UpsertTargetValueReqDTO item : req.getValues()) {
            PerfTargetValue v = new PerfTargetValue();
            v.setPlanId(item.getPlanId());
            v.setSubjectType(item.getSubjectType());
            v.setSubjectId(item.getSubjectId());
            v.setCycleKey(item.getCycleKey());
            v.setMetricCode(item.getMetricCode());
            v.setTargetValue(item.getTargetValue());
            v.setBaseValue(item.getBaseValue());
            // createdBy 在 Service 层强制覆盖为 operator (安全契约 I-2), 此处不设置
            list.add(v);
        }
        int affected = targetValueService.upsertBatch(list, operator);
        return ResponseWrapper.success(affected);
    }
}
