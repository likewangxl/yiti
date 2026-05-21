package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.TargetAdjustRespDTO;
import com.bank.branch.platform.performance.entity.PerfTargetAdjustApply;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfTargetAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitTargetAdjustCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 目标修正申请服务 (V1.2 Q3.2a).
 *
 * <p>职责：
 * <ul>
 *   <li>入参校验 + 目标方案存在性校验（TARGET_PLAN_NOT_FOUND）</li>
 *   <li>把 adjustments + reason 序列化为 JSON 写入 {@code remark} 字段（承载目标值修改建议）</li>
 *   <li>插入主表 status=IN_APPROVAL</li>
 *   <li>启动 {@code perf_target_adjust_v1} BPMN（单一流程，不分对公/零售）</li>
 *   <li>回写 processInstanceId</li>
 * </ul>
 *
 * <p>落地时机：审批通过后，{@code TargetAdjustCompletedListener} 读取 apply.remark，
 * 解析 adjustments，对每个 metricCode 调用 {@code PerfTargetValueMapper.upsertBatch}
 * 更新 (planId, subjectType, subjectId, cycleKey, metricCode) 五元组对应的目标值.
 */
@Slf4j
@Service
public class TargetAdjustService {

    /** 合法的对象类型. */
    private static final Set<String> ALLOWED_SUBJECT_TYPES = Set.of("EMP", "ORG");

    /** BPMN 流程定义 key（目标修正单一流程，不分对公/零售）. */
    public static final String PROCESS_KEY = "perf_target_adjust_v1";

    /** 流程业务类型（对齐 workflow-center bizType 枚举）. */
    public static final String BIZ_TYPE = "TARGET_ADJUST";

    /** businessKey 前缀. */
    public static final String BIZ_KEY_PREFIX = "TARGET_ADJUST:";

    /**
     * V1.4 S1.3：WORKFLOW_PARTICIPANT scope 的流程定义 key 前缀，
     * 配合 PerfScopeHelper.workflowParticipantFragment 查询目标调整相关流程.
     */
    public static final String WORKFLOW_PREFIX = "perf_target_adjust_";

    /** 申请编号日期前缀格式（TA{yyyyMMdd}{8 位 UUID}）. */
    private static final DateTimeFormatter APPLY_NO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PerfTargetAdjustApplyMapper applyMapper;
    private final PerfTargetPlanMapper targetPlanMapper;
    private final WorkflowApi workflowApi;
    private final CurrentUserApi currentUserApi;
    private final PerfScopeHelper perfScopeHelper;
    private final ObjectMapper objectMapper;

    public TargetAdjustService(PerfTargetAdjustApplyMapper applyMapper,
                               PerfTargetPlanMapper targetPlanMapper,
                               WorkflowApi workflowApi,
                               CurrentUserApi currentUserApi,
                               PerfScopeHelper perfScopeHelper) {
        this.applyMapper = applyMapper;
        this.targetPlanMapper = targetPlanMapper;
        this.workflowApi = workflowApi;
        this.currentUserApi = currentUserApi;
        this.perfScopeHelper = perfScopeHelper;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 提交目标修正申请.
     *
     * <p>事务边界：apply insert + updateStatus 回写 processInstanceId 共享同一事务，
     * 任一步骤异常整体回滚（Flowable 启动流程失败亦回滚 apply，避免孤儿申请）.
     *
     * @param cmd 提交命令
     * @return 新建申请 ID
     * @throws PerfException VALIDATION_FAILED / TARGET_PLAN_NOT_FOUND
     */
    @Transactional(rollbackFor = Exception.class)
    public String submit(SubmitTargetAdjustCmd cmd) {
        validateBasic(cmd);
        validateAdjustments(cmd);
        PerfTargetPlan plan = validateAndGetTargetPlan(cmd.getPlanId());

        String applyId = genApplyId();
        String applyNo = genApplyNo();
        String businessKey = BIZ_KEY_PREFIX + applyId;
        String remarkJson = buildRemarkJson(cmd);

        // 1. 落地主表（status=IN_APPROVAL，尚无 processInstanceId）
        PerfTargetAdjustApply apply = new PerfTargetAdjustApply();
        apply.setId(applyId);
        apply.setPlanId(cmd.getPlanId());
        apply.setSubjectType(cmd.getSubjectType());
        apply.setSubjectId(cmd.getSubjectId());
        apply.setCycleKey(cmd.getCycleKey());
        apply.setStatus("IN_APPROVAL");
        apply.setBusinessKey(businessKey);
        apply.setProcessInstanceId(null);
        apply.setOwnerOrgId(cmd.getOwnerOrgId());
        apply.setRemark(remarkJson);
        apply.setCreatedBy(cmd.getApplicant());
        LocalDateTime now = LocalDateTime.now();
        apply.setCreatedTime(now);
        apply.setUpdatedBy(cmd.getApplicant());
        apply.setUpdatedTime(now);
        applyMapper.insert(apply);

        // 2. 启动 Flowable 流程（单一 BPMN：perf_target_adjust_v1）
        StartProcessCmd startCmd = new StartProcessCmd();
        startCmd.setBizType(BIZ_TYPE);
        startCmd.setBizId(applyId);
        startCmd.setBusinessKey(businessKey);
        startCmd.setProcessDefinitionKey(PROCESS_KEY);
        startCmd.setStartUser(cmd.getApplicant());
        startCmd.setStartOrgId(cmd.getOwnerOrgId());
        startCmd.setTitle("目标修正-" + cmd.getSubjectType() + "-" + cmd.getSubjectId()
                + "-" + applyNo);
        Map<String, Object> vars = new HashMap<>();
        vars.put("applyId", applyId);
        vars.put("planId", cmd.getPlanId());
        vars.put("subjectType", cmd.getSubjectType());
        vars.put("subjectId", cmd.getSubjectId());
        vars.put("cycleKey", cmd.getCycleKey());
        // 原业绩所属人：取目标方案的 ownerEmpId 作为 BPMN original_owner_approve
        // 节点的 flowable:assignee 单人指派候选；plan.ownerEmpId 为空则不写此键.
        if (!isBlank(plan.getOwnerEmpId())) {
            vars.put("originalOwnerEmpId", plan.getOwnerEmpId());
        }
        startCmd.setVariables(vars);
        WorkflowLaunchResp resp = workflowApi.startProcess(startCmd);

        // 3. 回写 processInstanceId
        applyMapper.updateStatus(applyId, "IN_APPROVAL", resp.getProcessInstanceId());
        log.info("[TargetAdjustService.submit] applyId={}, applyNo={}, planId={}, subject={}:{}, "
                        + "cycleKey={}, pid={}",
                applyId, applyNo, cmd.getPlanId(), cmd.getSubjectType(), cmd.getSubjectId(),
                cmd.getCycleKey(), resp.getProcessInstanceId());
        return applyId;
    }

    /**
     * 查询单条申请.
     *
     * @param id 申请 ID
     * @return 申请实体
     * @throws PerfException TARGET_ADJUST_APPLY_NOT_FOUND 当申请不存在
     */
    public PerfTargetAdjustApply getById(String id) {
        if (isBlank(id)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "id 为空");
        }
        PerfTargetAdjustApply apply = applyMapper.selectByTargetApplyId(id);
        if (apply == null) {
            throw new PerfException(PerfErrorCode.TARGET_ADJUST_APPLY_NOT_FOUND, id);
        }
        return apply;
    }

    /**
     * 分页查询申请列表.
     *
     * @param status      状态过滤（nullable）
     * @param planId      目标方案过滤（nullable）
     * @param subjectType 对象类型过滤（nullable）
     * @param subjectId   对象 ID 过滤（nullable）
     * @param ownerOrgId  归属机构过滤（nullable）
     * @param createdBy   申请人过滤（nullable）
     * @param pageNo      页码（≥1）
     * @param pageSize    页大小（≥1）
     * @return 分页结果
     */
    public PageResult<PerfTargetAdjustApply> page(String status, String planId,
                                                  String subjectType, String subjectId,
                                                  String ownerOrgId, String createdBy,
                                                  int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<PerfTargetAdjustApply> rows = applyMapper.selectByConditions(
                status, planId, subjectType, subjectId, ownerOrgId, createdBy, offset, pageSize);
        long total = applyMapper.countByConditions(
                status, planId, subjectType, subjectId, ownerOrgId, createdBy);
        return PageResult.of(pageNo, pageSize, total, rows);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本 submit + 装配回显.
     *
     * <p>返回 Map 字段（id / status），与既有 Controller 保持前端契约兼容；
     * DTO 装配下沉到本 Service，Controller 不再感知 entity.
     */
    public Map<String, String> submitDto(SubmitTargetAdjustCmd cmd) {
        String id = submit(cmd);
        PerfTargetAdjustApply loaded = getById(id);
        return Map.of(
                "id", loaded.getId(),
                "status", loaded.getStatus());
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本 getById.
     */
    public TargetAdjustRespDTO getByIdDto(String id) {
        return toRespDto(getById(id));
    }

    /**
     * V1.3 R4.1 / V1.4 S1.3：Controller 专用 DTO 版本分页列表.
     *
     * <p>V1.4 S1.3 增强：注入 PerfScopeHelper 5 参 overload，bizKeyCol="business_key"，
     * workflowPrefix="perf_target_adjust_"，使 WORKFLOW_PARTICIPANT 角色的用户可以看到
     * 自己参与过的目标调整申请（按 businessKey 过滤）。
     *
     * <p>ScopeColumns 映射（perf_target_adjust_apply 表）：
     * <ul>
     *   <li>ownerEmpCol = "created_by"（SELF 降级）</li>
     *   <li>assigneeCol = "created_by"（无 assignee）</li>
     *   <li>createdByCol = "created_by"（SELF_CREATED）</li>
     *   <li>ownerOrgCol = "owner_org_id"（ORG）</li>
     *   <li>bizKeyCol = "business_key"（V1.4 S1.3 新增：WORKFLOW_PARTICIPANT）</li>
     * </ul>
     */
    public PageResult<TargetAdjustRespDTO> pageDto(String status, String planId,
                                                   String subjectType, String subjectId,
                                                   String ownerOrgId, String createdBy,
                                                   int pageNo, int pageSize) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        PerfScopeHelper.ScopeColumns columns = new PerfScopeHelper.ScopeColumns(
                "created_by",      // ownerEmpCol (SELF 降级)
                "created_by",      // assigneeCol (无 assignee)
                "created_by",      // createdByCol (SELF_CREATED)
                "owner_org_id",    // ownerOrgCol (ORG)
                "business_key"     // bizKeyCol (V1.4 S1.3: WORKFLOW_PARTICIPANT)
        );
        PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
                currentEmpId, BizType.PERF_CONFIG, BizAction.LIST, columns, WORKFLOW_PREFIX);

        int offset = Math.max(pageNo - 1, 0) * pageSize;
        long total = applyMapper.countByConditionsWithScope(
                status, planId, subjectType, subjectId, ownerOrgId, createdBy,
                frag.getSql(), frag.getParams());
        List<PerfTargetAdjustApply> rows = applyMapper.selectByConditionsWithScope(
                status, planId, subjectType, subjectId, ownerOrgId, createdBy,
                offset, pageSize, frag.getSql(), frag.getParams());

        List<TargetAdjustRespDTO> dtos = new java.util.ArrayList<>(rows.size());
        for (PerfTargetAdjustApply apply : rows) {
            dtos.add(toRespDto(apply));
        }
        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * V1.3 R4.1：entity → DTO 装配下沉到 Service.
     */
    private TargetAdjustRespDTO toRespDto(PerfTargetAdjustApply apply) {
        TargetAdjustRespDTO dto = new TargetAdjustRespDTO();
        dto.setId(apply.getId());
        dto.setPlanId(apply.getPlanId());
        dto.setSubjectType(apply.getSubjectType());
        dto.setSubjectId(apply.getSubjectId());
        dto.setCycleKey(apply.getCycleKey());
        dto.setStatus(apply.getStatus());
        dto.setBusinessKey(apply.getBusinessKey());
        dto.setProcessInstanceId(apply.getProcessInstanceId());
        dto.setOwnerOrgId(apply.getOwnerOrgId());
        dto.setRemark(apply.getRemark());
        dto.setCreatedBy(apply.getCreatedBy());
        dto.setCreatedTime(apply.getCreatedTime());
        dto.setUpdatedBy(apply.getUpdatedBy());
        dto.setUpdatedTime(apply.getUpdatedTime());
        return dto;
    }

    /**
     * 撤回申请：IN_APPROVAL / DRAFT → REJECTED.
     *
     * <p>V1.2 简化实现：仅将本地 apply 状态置为 REJECTED，保留流程实例不做取消。
     * 生产完整方案需调 WorkflowApi.cancelProcess 同步取消 Flowable 流程（留待后续迭代）.
     *
     * @param id       申请 ID
     * @param reason   撤回原因
     * @param operator 操作人 empId
     * @throws PerfException VALIDATION_FAILED / TARGET_ADJUST_APPLY_NOT_FOUND
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(String id, String reason, String operator) {
        if (isBlank(id)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "id 为空");
        }
        PerfTargetAdjustApply apply = applyMapper.selectByTargetApplyId(id);
        if (apply == null) {
            throw new PerfException(PerfErrorCode.TARGET_ADJUST_APPLY_NOT_FOUND, id);
        }
        if (!"IN_APPROVAL".equals(apply.getStatus()) && !"DRAFT".equals(apply.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "申请状态不可撤回: " + apply.getStatus());
        }
        applyMapper.updateStatus(id, "REJECTED", null);
        // 同步取消 Flowable 流程实例，否则该流程的 active task 会一直留在「待我审批」
        // DRAFT 状态可能未启动流程（process_instance_id=null），需判空
        String pid = apply.getProcessInstanceId();
        if (pid != null && !pid.isBlank()) {
            try {
                workflowApi.cancelProcess(pid, reason);
            } catch (Exception ex) {
                log.warn("[TargetAdjustService.withdraw] cancelProcess 失败 pid={}, 业务侧已置 REJECTED；err={}",
                        pid, ex.getMessage());
            }
        }
        log.info("[TargetAdjustService.withdraw] id={}, reason={}, operator={}",
                id, reason, operator);
    }

    /**
     * 必填字段 + 枚举校验.
     */
    private void validateBasic(SubmitTargetAdjustCmd cmd) {
        if (cmd == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cmd is null");
        }
        if (isBlank(cmd.getPlanId())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "planId 为空");
        }
        if (isBlank(cmd.getSubjectType()) || !ALLOWED_SUBJECT_TYPES.contains(cmd.getSubjectType())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "subjectType 非法: " + cmd.getSubjectType());
        }
        if (isBlank(cmd.getSubjectId())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "subjectId 为空");
        }
        if (isBlank(cmd.getCycleKey())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cycleKey 为空");
        }
        if (isBlank(cmd.getOwnerOrgId())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "ownerOrgId 为空");
        }
        if (isBlank(cmd.getReason())) {
            // 高危操作 reason 必填（与 @AuditLog reasonRequired=true 协同）
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "reason 为空（高危操作必填）");
        }
        if (isBlank(cmd.getApplicant())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "applicant 为空");
        }
    }

    /**
     * adjustments 列表校验：非空、metricCode 去重、newValue 非空.
     */
    private void validateAdjustments(SubmitTargetAdjustCmd cmd) {
        List<SubmitTargetAdjustCmd.TargetAdjustment> list = cmd.getAdjustments();
        if (list == null || list.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "adjustments 不能为空");
        }
        Set<String> metricCodes = new HashSet<>();
        for (SubmitTargetAdjustCmd.TargetAdjustment a : list) {
            if (isBlank(a.getMetricCode())) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "adjustment.metricCode 为空");
            }
            if (a.getNewValue() == null) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "adjustment.newValue 为空: " + a.getMetricCode());
            }
            if (!metricCodes.add(a.getMetricCode())) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "adjustment.metricCode 重复: " + a.getMetricCode());
            }
        }
    }

    /**
     * 目标方案存在性校验，并返回 plan 实体（供 submit 复用 ownerEmpId 等字段，避免重复查询）.
     */
    private PerfTargetPlan validateAndGetTargetPlan(String planId) {
        PerfTargetPlan plan = targetPlanMapper.selectById(planId);
        if (plan == null) {
            throw new PerfException(PerfErrorCode.TARGET_PLAN_NOT_FOUND, planId);
        }
        return plan;
    }

    /**
     * 把 adjustments + reason 组装为 JSON 字符串存 remark 字段.
     *
     * <p>结构：
     * <pre>
     * {
     *   "adjustments": [
     *     {"metricCode": "M_DEP_BAL", "oldValue": 100, "newValue": 120}
     *   ],
     *   "reason": "..."
     * }
     * </pre>
     */
    private String buildRemarkJson(SubmitTargetAdjustCmd cmd) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("adjustments", cmd.getAdjustments());
        root.put("reason", cmd.getReason());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // 序列化失败属内部异常，回退为最小化 JSON
            log.error("[TargetAdjustService.buildRemarkJson] 序列化失败", e);
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "remark JSON 序列化失败");
        }
    }

    /**
     * 生成申请 ID（32 位无横线 UUID）.
     */
    private String genApplyId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成申请编号：TA{yyyyMMdd}{UUID 8 位}.
     */
    private String genApplyNo() {
        String datePart = LocalDateTime.now().format(APPLY_NO_DATE_FMT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TA" + datePart + suffix;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
