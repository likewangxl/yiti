package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.TargetSubjectDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueBatchReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpsertTargetValueReqDTO;
import com.bank.branch.platform.performance.service.TargetValueService;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueBatchCmd;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 *
 * <p>V1.3 R4.1 改造：Controller 不再 {@code new PerfTargetValue()}，批量 upsert 改为
 * 传 {@link UpsertTargetValueBatchCmd} 给 Service，entity 构造下沉到 Service 层.
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
     * <p>V1.3 R1.3 改造：切换到 {@link TargetValueService#pageWithScopeDto} 以启用
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
        // V1.3 R4.1：Controller 不再感知 entity，改调 pageWithScopeDto
        PageResult<TargetValueDTO> dtoPage = targetValueService.pageWithScopeDto(
                planId, subjectType, subjectId, cycleKey, pageNo, pageSize);
        return ResponseWrapper.page(dtoPage);
    }

    /**
     * 目标值「对象」下拉（方案内目标值去重 + 标签解析，2026-06-15）.
     *
     * <p>用于目标值管理页查询区的对象下拉：EMP→「工号 姓名」、ORG→「部门编号 机构名称」。
     * 复用 LIST 鉴权资源族（GET /api/perf/target-values/subjects → P_PERF_TGT_V_SUBJ）。
     *
     * @param planId 目标方案ID（必填）
     * @return 去重并解析标签后的对象列表
     */
    @GetMapping("/subjects")
    @Operation(summary = "目标值对象下拉（方案内去重）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<List<TargetSubjectDTO>> subjects(
            @RequestParam(value = "planId") @NotBlank String planId) {
        log.debug("[TargetValueController.subjects] planId={}", planId);
        return ResponseWrapper.success(targetValueService.listSubjects(planId));
    }

    /**
     * 目标值「阶段名称」下拉（方案内去重，非空）。
     *
     * <p>用于目标值管理页查询区的「阶段名称」下拉。
     * 复用 LIST 鉴权资源族（GET /api/perf/target-values/stage-names → P_PERF_TGT_V_STAGE）。
     *
     * @param planId 目标方案ID（必填）
     * @return 去重的非空阶段名称列表
     */
    @GetMapping("/stage-names")
    @Operation(summary = "目标值阶段名称下拉（方案内去重）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<List<String>> stageNames(
            @RequestParam(value = "planId") @NotBlank String planId) {
        log.debug("[TargetValueController.stageNames] planId={}", planId);
        return ResponseWrapper.success(targetValueService.listStageNames(planId));
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
                .stageName(req.getStageName())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
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
     *
     * <p>V1.3 R4.1 改造：Controller 不再 {@code new PerfTargetValue()}，改为装配
     * {@link UpsertTargetValueBatchCmd} 交给 {@link TargetValueService#upsertBatchFromCmd}.
     */
    @PostMapping("/batch")
    @Operation(summary = "批量目标 upsert (<=500)")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.WRITE)
    @AuditLog(action = "CREATE", resourceType = "TARGET_VALUE")
    public ResponseWrapper<Integer> batch(@Valid @RequestBody UpsertTargetValueBatchReqDTO req) {
        log.info("[TargetValueController.batch] size={}", req.getValues().size());
        String operator = currentUserApi.getCurrentEmpId();
        List<UpsertTargetValueCmd> cmds = new ArrayList<>(req.getValues().size());
        for (UpsertTargetValueReqDTO item : req.getValues()) {
            cmds.add(UpsertTargetValueCmd.builder()
                    .planId(item.getPlanId())
                    .subjectType(item.getSubjectType())
                    .subjectId(item.getSubjectId())
                    .cycleKey(item.getCycleKey())
                    .metricCode(item.getMetricCode())
                    .targetValue(item.getTargetValue())
                    .baseValue(item.getBaseValue())
                    .stageName(item.getStageName())
                    .startDate(item.getStartDate())
                    .endDate(item.getEndDate())
                    // operator 仅在 batchCmd 顶层设置，I-2 强制覆盖 created_by 在 Service 侧处理
                    .build());
        }
        UpsertTargetValueBatchCmd batchCmd = UpsertTargetValueBatchCmd.builder()
                .items(cmds)
                .operator(operator)
                .build();
        int affected = targetValueService.upsertBatchFromCmd(batchCmd);
        return ResponseWrapper.success(affected);
    }

    /**
     * 物理删除单条目标值（目标值管理页"删除"操作，前端二次确认后调用）。
     *
     * <p>高危操作：独立 URL + {@code @BizAuth(DELETE)} + {@code @AuditLog} 审计；
     * PERF_TARGET_VALUE 无逻辑删除列，直接物理删除。
     *
     * @param id 目标值主键
     * @return 受影响行数（0 表示该 id 不存在）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "物理删除目标值")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.DELETE)
    @AuditLog(action = "DELETE", resourceType = "TARGET_VALUE")
    public ResponseWrapper<Integer> delete(@PathVariable("id") String id) {
        log.info("[TargetValueController.delete] id={}", id);
        int affected = targetValueService.deleteById(id);
        return ResponseWrapper.success(affected);
    }
}
