package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.AllocAdjustRespDTO;
import com.bank.branch.platform.performance.controller.dto.EmpSuggestRespDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 分配关系调整申请服务 (V1.2 Q2.2).
 *
 * <p>职责：
 * <ul>
 *   <li>入参校验 + 客户存在性校验</li>
 *   <li>主从表聚合写入（apply + items 同事务）</li>
 *   <li>按 {@code bizKind} 前缀路由对公 / 零售 BPMN</li>
 *   <li>启动 Flowable 流程并回写 processInstanceId</li>
 * </ul>
 *
 * <p>对公/零售路由（以 biz_kind 判定，不引入 adjust_type）：
 * <ul>
 *   <li>{@code CORP_*}（如 CORP_LOAN / CORP_DEPOSIT）→ {@code perf_alloc_adjust_corp_v1}</li>
 *   <li>{@code RETAIL_*}（如 RETAIL_CARD / RETAIL_LOAN）→ {@code perf_alloc_adjust_retail_v1}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustService {

    /** 合法分配维度. */
    private static final Set<String> ALLOWED_ALLOC_DIMS = Set.of("RULE", "ACCOUNT");

    /** RULE 维度下 ratio 合计上限（100%）. */
    private static final BigDecimal RULE_RATIO_MAX = new BigDecimal("100");

    /** 对公流程定义 key. */
    public static final String PROCESS_KEY_CORP = "perf_alloc_adjust_corp_v1";

    /** 零售流程定义 key. */
    public static final String PROCESS_KEY_RETAIL = "perf_alloc_adjust_retail_v1";

    /** 流程业务类型（对齐 workflow-center 支持的 bizType 枚举）. */
    public static final String BIZ_TYPE = "ALLOC_ADJUST";

    /**
     * V1.4 S1.3：WORKFLOW_PARTICIPANT scope 的流程定义 key 前缀，
     * 配合 PerfScopeHelper.workflowParticipantFragment 查询分配调整相关流程.
     */
    public static final String WORKFLOW_PREFIX = "perf_alloc_adjust_";

    /** 申请编号日期前缀格式（如 AA20260423xxxx）. */
    private static final DateTimeFormatter APPLY_NO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PerfAllocAdjustApplyMapper applyMapper;
    private final PerfAllocAdjustItemMapper itemMapper;
    private final CustAllocRelationMapper allocRelationMapper;
    private final WorkflowApi workflowApi;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final PerfScopeHelper perfScopeHelper;
    private final AllocAdjustPreviewService allocAdjustPreviewService;

    /** 员工联想默认返回上限. */
    private static final int EMP_SUGGEST_DEFAULT_LIMIT = 20;
    /** 员工联想返回上限硬顶. */
    private static final int EMP_SUGGEST_MAX_LIMIT = 50;

    /**
     * 分配明细员工号输入框自动补齐：按关键字模糊匹配 PT_USER 工号/登录名/中文名.
     *
     * <p>委托 {@link UserApi#pageUsers(String, int, int)}（OR 模糊匹配 USER_ID / USERNAME /
     * USERCHNNAME），转换为 {@link EmpSuggestRespDTO}（登录名 + 中文名）供前端展示。
     * 关键字为空时不返回（避免全表联想）。
     *
     * @param keyword 关键字（工号/登录名/中文名片段）
     * @param limit   返回上限（默认 20，最大 50）
     * @return 建议项列表，不会返回 null
     */
    public List<EmpSuggestRespDTO> suggestEmployees(String keyword, Integer limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        int size = (limit == null || limit < 1) ? EMP_SUGGEST_DEFAULT_LIMIT
                : Math.min(limit, EMP_SUGGEST_MAX_LIMIT);
        PageResult<com.bank.branch.platform.auth.api.dto.UserDTO> page =
                userApi.pageUsers(keyword.trim(), 1, size);
        List<EmpSuggestRespDTO> result = new java.util.ArrayList<>();
        if (page != null && page.getRecords() != null) {
            for (com.bank.branch.platform.auth.api.dto.UserDTO u : page.getRecords()) {
                if (u == null) {
                    continue;
                }
                result.add(new EmpSuggestRespDTO(u.getEmpId(), u.getUsername(), u.getDisplayName()));
            }
        }
        return result;
    }

    /**
     * 提交分配关系调整申请.
     *
     * <p>事务边界：主从表 insert + updateStatus 回写 processInstanceId 共享同一事务，
     * 任一步骤异常整体回滚（Flowable 启动流程失败亦回滚 apply/items，避免孤儿申请）.
     *
     * @param cmd 提交命令
     * @return 新建申请 ID
     * @throws PerfException VALIDATION_FAILED / BIZ_KIND_INVALID
     */
    @Transactional(rollbackFor = Exception.class)
    public String submit(SubmitAllocAdjustCmd cmd) {
        validateBasic(cmd);
        validateItems(cmd);
        // cust_id 直接存用户输入的客户编号（原 cust_no 字段已废弃，统一并入 cust_id）
        String custId = cmd.getCustId();

        // 同客户同维度去重：审批中(IN_APPROVAL)已存在则拒绝重复提交，避免并行调整相互覆盖。
        checkNoInApprovalDuplicate(custId, cmd.getAllocDim());

        // 原业绩分配会签名单：历史审批通过分配优先，查不到则回退本次手工录入。提交审批必须非空。
        List<String> originalOwnerEmpIds = resolveOriginalOwnerEmpIds(
                custId, cmd.getAllocDim(), cmd.getOriginalAllocList());
        if (originalOwnerEmpIds.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "至少需要一条原业绩分配记录");
        }

        String applyId = genApplyId();
        String applyNo = genApplyNo();

        // 1. 落地主表（status=IN_APPROVAL，尚无 processInstanceId）+ 明细
        PerfAllocAdjustApply apply = buildApply(applyId, applyNo, cmd, "IN_APPROVAL");
        applyMapper.insert(apply);
        persistItems(applyId, cmd);

        // 2. 启动 Flowable 流程并回写 processInstanceId（异常冒泡回滚，避免残留无 pid 的 apply）
        String pid = startApprovalWorkflow(applyId, applyNo, custId, cmd, originalOwnerEmpIds);
        applyMapper.updateStatus(applyId, "IN_APPROVAL", pid);
        log.info("[AllocAdjustService.submit] applyId={}, applyNo={}, pid={}", applyId, applyNo, pid);
        return applyId;
    }

    /**
     * 保存为草稿：落库录入信息，状态 DRAFT，<b>不进入审批流程</b>.
     *
     * <p>与 {@link #submit} 的关键差异：
     * <ul>
     *   <li>宽松校验——只校验 {@code custId}/{@code applicant} 必填 + allocDim 枚举（若填），
     *       不强制 items 非空、比例合计、原业绩分配非空，允许保存半成品。</li>
     *   <li>不调 {@link WorkflowApi#startProcess}，不写 processInstanceId。</li>
     * </ul>
     *
     * @param cmd 录入数据
     * @param id  既有草稿 ID；为空=新建草稿，非空=编辑既有草稿（仅 DRAFT 可改）
     * @return 草稿 applyId
     * @throws PerfException VALIDATION_FAILED（custId 缺失 / 编辑目标不存在或非 DRAFT）
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveDraft(SubmitAllocAdjustCmd cmd, String id) {
        validateDraftBasic(cmd);
        String applyId;
        String applyNo;
        if (isBlank(id)) {
            // 新建草稿
            applyId = genApplyId();
            applyNo = genApplyNo();
            PerfAllocAdjustApply apply = buildApply(applyId, applyNo, cmd, "DRAFT");
            applyMapper.insert(apply);
        } else {
            // 编辑既有草稿：仅 DRAFT 可改，保留原 applyNo/创建人/创建时间，重建明细
            PerfAllocAdjustApply existing = applyMapper.selectByAllocApplyId(id);
            if (existing == null) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "草稿不存在: " + id);
            }
            if (!"DRAFT".equals(existing.getStatus())) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "仅草稿状态可编辑: " + existing.getStatus());
            }
            applyId = id;
            applyNo = existing.getApplyNo();
            PerfAllocAdjustApply apply = buildApply(applyId, applyNo, cmd, "DRAFT");
            // 保留原创建人/创建时间，仅更新可编辑字段
            apply.setCreatedBy(existing.getCreatedBy());
            apply.setCreatedTime(existing.getCreatedTime());
            applyMapper.updateDraft(apply);
            // 明细全量重建：先清后插，避免残留旧明细
            itemMapper.deleteByApplyId(applyId);
        }
        persistItems(applyId, cmd);
        log.info("[AllocAdjustService.saveDraft] applyId={}, applyNo={}, mode={}",
                applyId, applyNo, isBlank(id) ? "CREATE" : "UPDATE");
        return applyId;
    }

    /**
     * 草稿提交审批：载入既有 DRAFT 申请 → 完整校验 + 去重 → 起流程 → 状态 DRAFT→IN_APPROVAL.
     *
     * <p>草稿明细已落库，本方法<b>不重新插入</b>，仅按持久化数据重建 cmd 走完整审批校验，
     * 与新建直接提交（{@link #submit}）共用同一套校验与流程启动逻辑。
     *
     * @param id       草稿 applyId
     * @param operator 提交人 empId（作为流程发起人）
     * @return applyId
     * @throws PerfException VALIDATION_FAILED（不存在 / 非 DRAFT / 校验不通过）
     */
    @Transactional(rollbackFor = Exception.class)
    public String submitDraft(String id, String operator) {
        if (isBlank(id)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "id 为空");
        }
        PerfAllocAdjustApply apply = applyMapper.selectByAllocApplyId(id);
        if (apply == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "草稿不存在: " + id);
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "仅草稿状态可提交审批: " + apply.getStatus());
        }
        List<PerfAllocAdjustItem> persisted = itemMapper.selectByApplyId(id);
        SubmitAllocAdjustCmd cmd = rebuildCmdFromPersisted(apply, persisted, operator);

        validateBasic(cmd);
        validateItems(cmd);
        checkNoInApprovalDuplicate(cmd.getCustId(), cmd.getAllocDim());
        List<String> originalOwnerEmpIds = resolveOriginalOwnerEmpIds(
                cmd.getCustId(), cmd.getAllocDim(), cmd.getOriginalAllocList());
        if (originalOwnerEmpIds.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "至少需要一条原业绩分配记录");
        }
        // 起流程并回写状态（草稿明细已在库，不重插）
        String pid = startApprovalWorkflow(id, apply.getApplyNo(), cmd.getCustId(), cmd, originalOwnerEmpIds);
        applyMapper.updateStatus(id, "IN_APPROVAL", pid);
        log.info("[AllocAdjustService.submitDraft] applyId={}, applyNo={}, pid={}", id, apply.getApplyNo(), pid);
        return id;
    }

    /**
     * 同客户同维度审批中(IN_APPROVAL)去重校验。
     * <p>去重粒度精确到维度：RULE 审批中不阻塞 ACCOUNT 的新提交，反之亦然。
     * PerfException 的 errorCode.format 已自动拼「基础消息 + ": " + arg」，只传 custId 避免重复。
     */
    private void checkNoInApprovalDuplicate(String custId, String allocDim) {
        if (applyMapper.countInApprovalByCustAndDim(custId, allocDim) > 0) {
            throw new PerfException(PerfErrorCode.ALLOC_ADJUST_APPLY_RUNNING, custId);
        }
    }

    /** 草稿宽松校验：仅 custId/applicant 必填；allocDim 若填须合法。其余留空允许保存半成品。 */
    private void validateDraftBasic(SubmitAllocAdjustCmd cmd) {
        if (cmd == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cmd is null");
        }
        if (isBlank(cmd.getCustId())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "custId 为空");
        }
        if (!isBlank(cmd.getAllocDim()) && !ALLOWED_ALLOC_DIMS.contains(cmd.getAllocDim())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "allocDim 非法: " + cmd.getAllocDim());
        }
        if (isBlank(cmd.getApplicant())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "applicant 为空");
        }
    }

    /** 组装 apply 主表实体（提交/草稿共用，status 由调用方传入）. */
    private PerfAllocAdjustApply buildApply(String applyId, String applyNo,
                                            SubmitAllocAdjustCmd cmd, String status) {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId(applyId);
        apply.setApplyNo(applyNo);
        apply.setCustId(cmd.getCustId());
        // 客户名称 + 余额概览快照随保存入库，列表/详情直接读，不再实时反查
        apply.setCustName(cmd.getCustName());
        apply.setCurrBal(cmd.getCurrBal());
        apply.setMAvgBal(cmd.getMAvgBal());
        apply.setQAvgBal(cmd.getQAvgBal());
        apply.setYAvgBal(cmd.getYAvgBal());
        apply.setLoanCurrBal(cmd.getLoanCurrBal());
        apply.setLoanMAvgBal(cmd.getLoanMAvgBal());
        apply.setLoanQAvgBal(cmd.getLoanQAvgBal());
        apply.setLoanYAvgBal(cmd.getLoanYAvgBal());
        apply.setCustType(cmd.getCustType());
        apply.setAllocDim(cmd.getAllocDim());
        apply.setBizKind(cmd.getBizKind());
        apply.setAccountNo(cmd.getAccountNo());
        apply.setStatus(status);
        apply.setBusinessKey("ALLOC_ADJUST:" + applyId);
        apply.setProcessInstanceId(null);
        apply.setOwnerOrgId(cmd.getOwnerOrgId());
        apply.setRemark(cmd.getReason());
        apply.setCreatedBy(cmd.getApplicant());
        LocalDateTime now = LocalDateTime.now();
        apply.setCreatedTime(now);
        apply.setUpdatedBy(cmd.getApplicant());
        apply.setUpdatedTime(now);
        return apply;
    }

    /**
     * 落地明细（NEW + ORIGIN）。提交/草稿共用：
     * NEW 明细快照员工 username/中文名/部门，ORIGIN 由 {@link #buildOriginalItems} 组装。
     * cmd.items 为空（草稿半成品）时只落 ORIGIN（或都不落）。
     */
    private void persistItems(String applyId, SubmitAllocAdjustCmd cmd) {
        List<SubmitAllocAdjustCmd.Item> srcItems = cmd.getItems() != null
                ? cmd.getItems() : java.util.Collections.emptyList();
        java.util.LinkedHashSet<String> empIds = new java.util.LinkedHashSet<>();
        for (SubmitAllocAdjustCmd.Item it : srcItems) {
            if (!isBlank(it.getEmpId())) {
                empIds.add(it.getEmpId());
            }
        }
        java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> userMap = resolveUsersByTokens(empIds);
        List<PerfAllocAdjustItem> items = new ArrayList<>(srcItems.size());
        for (SubmitAllocAdjustCmd.Item it : srcItems) {
            if (isBlank(it.getEmpId())) {
                continue; // 草稿半成品空行跳过
            }
            PerfAllocAdjustItem entity = new PerfAllocAdjustItem();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setApplyId(applyId);
            entity.setEmpId(it.getEmpId());
            com.bank.branch.platform.auth.api.dto.UserDTO u = userMap.get(it.getEmpId());
            // username 解析不到时回退工号，避免空白；中文名/部门解析不到留空
            entity.setUsername((u != null && !isBlank(u.getUsername())) ? u.getUsername() : it.getEmpId());
            entity.setEmpChnName(u != null ? u.getDisplayName() : null);
            entity.setOrgCode(u != null ? u.getMainOrgCode() : null);
            entity.setOrgName(u != null ? u.getMainOrgName() : null);
            entity.setRatio(it.getRatio());
            entity.setRemark(it.getRemark());
            items.add(entity);
        }
        if (!items.isEmpty()) {
            itemMapper.batchInsert(items);
        }
        // 原业绩分配（手工录入）不再落 PERF_ALLOC_ADJUST_ITEM（item_kind 已废弃）；
        // 直接 seed 到 cust_alloc_relation（当前生效 is_original='2'）。buildOriginalItems 仅做工号→姓名/部门补全。
        List<PerfAllocAdjustItem> originItems = buildOriginalItems(applyId, cmd.getOriginalAllocList());
        if (!originItems.isEmpty()) {
            seedOriginalAllocRelations(cmd, originItems);
        }
    }

    /**
     * 手工录入的原业绩分配同步落库 {@code cust_alloc_relation}（当前生效，{@code is_original='2'}）.
     *
     * <p>仅当该客户当前<b>无</b> is_original='2' 分配时写入——即确为「手工录入」场景
     * （预填回写来自既有 is_original='2'，再写会重复），对应需求「如果没有数据手工输入」。
     *
     * <p>字段：{@code source_batch_id=null} / {@code source_process_date=null} / {@code is_original='2'}；
     * 姓名、部门取手工录入快照（fullname / dept_no / dept_name）；cust_type 取审批申请。
     */
    private void seedOriginalAllocRelations(SubmitAllocAdjustCmd cmd, List<PerfAllocAdjustItem> originItems) {
        List<CustAllocRelation> existing =
                allocRelationMapper.selectCurrentOriginalByCust(cmd.getCustId(), cmd.getAllocDim());
        if (existing != null && !existing.isEmpty()) {
            return; // 已有当前生效分配（预填场景），不重复写入
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        String operator = !isBlank(cmd.getApplicant()) ? cmd.getApplicant() : null;
        for (PerfAllocAdjustItem it : originItems) {
            CustAllocRelation rel = new CustAllocRelation();
            rel.setId(UUID.randomUUID().toString().replace("-", ""));
            rel.setCustId(cmd.getCustId());
            rel.setCustType(cmd.getCustType());
            rel.setAllocDim(cmd.getAllocDim());
            rel.setBizKind(cmd.getBizKind());
            rel.setAccountNo(!isBlank(it.getAcctNo()) ? it.getAcctNo() : cmd.getAccountNo());
            rel.setEmpId(it.getEmpId());
            rel.setFullname(it.getEmpChnName());
            rel.setDeptNo(it.getOrgCode());
            rel.setDeptName(it.getOrgName());
            rel.setRatio(it.getRatio());
            // 手工录入即当前生效分配
            rel.setIsOriginal("2");
            rel.setEffectiveDate(today);
            rel.setEndDate(null);
            // 手工录入无来源批次/业务日期
            rel.setSourceBatchId(null);
            rel.setSourceProcessDate(null);
            rel.setCreatedBy(operator);
            rel.setUpdatedBy(operator);
            allocRelationMapper.insert(rel);
        }
    }

    /**
     * 启动审批流程，返回 processInstanceId（提交/草稿提交共用）。
     * <p>businessKey 固定 {@code ALLOC_ADJUST:applyId}；流程变量含会签名单与单人兜底审批人。
     */
    private String startApprovalWorkflow(String applyId, String applyNo, String custId,
                                         SubmitAllocAdjustCmd cmd, List<String> originalOwnerEmpIds) {
        StartProcessCmd startCmd = new StartProcessCmd();
        startCmd.setBizType(BIZ_TYPE);
        startCmd.setBizId(applyId);
        startCmd.setBusinessKey("ALLOC_ADJUST:" + applyId);
        startCmd.setProcessDefinitionKey(resolveProcessKey(cmd.getCustType(), cmd.getBizKind()));
        startCmd.setStartUser(cmd.getApplicant());
        startCmd.setStartOrgId(cmd.getOwnerOrgId());
        // 标题用客户编号便于人工识别；流程变量 custId/custNo 均写客户编号（下游 BPMN/Listener 兼容读取）
        startCmd.setTitle("分配关系调整-" + custId + "-" + applyNo);
        Map<String, Object> vars = new HashMap<>();
        vars.put("applyId", applyId);
        vars.put("custId", custId);
        vars.put("custNo", custId);
        vars.put("bizKind", cmd.getBizKind());
        vars.put("allocDim", cmd.getAllocDim());
        // 单人指派候选（original_owner_approve 的 assignee 兜底）：历史当前分配优先，否则取会签名单首位。
        String originalOwnerEmpId = resolveOriginalOwnerEmpId(custId, cmd.getBizKind());
        if (isBlank(originalOwnerEmpId)) {
            originalOwnerEmpId = originalOwnerEmpIds.get(0);
        }
        vars.put("originalOwnerEmpId", originalOwnerEmpId);
        // 原业绩分配会签名单（corp_v1 并行多实例 collection），前面已校验非空。
        vars.put("originalOwnerEmpIds", originalOwnerEmpIds);
        startCmd.setVariables(vars);
        WorkflowLaunchResp resp = workflowApi.startProcess(startCmd);
        return resp.getProcessInstanceId();
    }

    /**
     * 草稿提交：按持久化的 apply + items 重建 SubmitAllocAdjustCmd，供完整审批校验/流程启动复用。
     * <p>NEW 明细 → items；ORIGIN 明细 → originalAllocList；applicant 取传入 operator，
     * 缺失时回退 apply.createdBy（保证发起人非空）。
     */
    private SubmitAllocAdjustCmd rebuildCmdFromPersisted(PerfAllocAdjustApply apply,
            List<PerfAllocAdjustItem> persisted, String operator) {
        List<SubmitAllocAdjustCmd.Item> items = new ArrayList<>();
        // PERF_ALLOC_ADJUST_ITEM 现仅存调整明细（item_kind 已废弃）；原业绩分配在 cust_alloc_relation，
        // 会签名单由 resolveOriginalOwnerEmpIds 改读 cust_alloc_relation，这里 originalAllocList 留空即可。
        List<SubmitAllocAdjustCmd.OriginalItem> origins = new ArrayList<>();
        if (persisted != null) {
            for (PerfAllocAdjustItem it : persisted) {
                items.add(SubmitAllocAdjustCmd.Item.builder()
                        .empId(it.getEmpId())
                        .ratio(it.getRatio())
                        .remark(it.getRemark())
                        .build());
            }
        }
        return SubmitAllocAdjustCmd.builder()
                .custType(apply.getCustType())
                .custId(apply.getCustId())
                .custName(apply.getCustName())
                .currBal(apply.getCurrBal())
                .mAvgBal(apply.getMAvgBal())
                .qAvgBal(apply.getQAvgBal())
                .yAvgBal(apply.getYAvgBal())
                .loanCurrBal(apply.getLoanCurrBal())
                .loanMAvgBal(apply.getLoanMAvgBal())
                .loanQAvgBal(apply.getLoanQAvgBal())
                .loanYAvgBal(apply.getLoanYAvgBal())
                .allocDim(apply.getAllocDim())
                .bizKind(apply.getBizKind())
                .accountNo(apply.getAccountNo())
                .ownerOrgId(apply.getOwnerOrgId())
                .reason(apply.getRemark())
                .applicant(!isBlank(operator) ? operator : apply.getCreatedBy())
                .items(items)
                .originalAllocList(origins)
                .build();
    }

    /**
     * 查询单条申请 + 明细.
     *
     * @param id 申请 ID
     * @return [apply, items] 二元组
     * @throws PerfException VALIDATION_FAILED 当申请不存在
     */
    public ApplyWithItems getById(String id) {
        if (isBlank(id)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "id 为空");
        }
        PerfAllocAdjustApply apply = applyMapper.selectByAllocApplyId(id);
        if (apply == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "申请不存在: " + id);
        }
        List<PerfAllocAdjustItem> items = itemMapper.selectByApplyId(id);
        return new ApplyWithItems(apply, items);
    }

    /**
     * 分页查询申请列表（不含 items）.
     *
     * @param status     状态过滤（nullable）
     * @param bizKind    业务种类过滤（nullable）
     * @param custId     客户 ID 过滤（nullable）
     * @param ownerOrgId 归属机构过滤（nullable）
     * @param createdBy  申请人过滤（nullable）
     * @param pageNo     页码（≥1）
     * @param pageSize   页大小（≥1）
     * @return 分页结果
     */
    public PageResult<PerfAllocAdjustApply> page(String status, String bizKind, String custId,
                                                 String ownerOrgId, String createdBy,
                                                 int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<PerfAllocAdjustApply> rows = applyMapper.selectByConditions(
                status, bizKind, custId, ownerOrgId, createdBy, offset, pageSize);
        long total = applyMapper.countByConditions(status, bizKind, custId, ownerOrgId, createdBy);
        return PageResult.of(pageNo, pageSize, total, rows);
    }

    /**
     * 撤回申请：IN_APPROVAL/DRAFT → WITHDRAWN.
     *
     * <p>撤回与驳回区分：撤回是申请人主动收回（状态 WITHDRAWN），驳回是审批人否决（状态 REJECTED）。
     * 生产完整方案需调 WorkflowApi.cancelProcess 同步取消 Flowable 流程（留待后续迭代）.
     *
     * @param id       申请 ID
     * @param reason   撤回原因
     * @param operator 操作人 empId
     * @throws PerfException VALIDATION_FAILED
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(String id, String reason, String operator) {
        if (isBlank(id)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "id 为空");
        }
        PerfAllocAdjustApply apply = applyMapper.selectByAllocApplyId(id);
        if (apply == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "申请不存在: " + id);
        }
        if (!"IN_APPROVAL".equals(apply.getStatus()) && !"DRAFT".equals(apply.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "申请状态不可撤回: " + apply.getStatus());
        }
        // 撤回置 WITHDRAWN（区别于审批驳回 REJECTED）；processInstanceId 传 null 避免覆写历史值
        applyMapper.updateStatus(id, "WITHDRAWN", null);
        // 同步取消 Flowable 流程实例，否则该流程的 active task 会一直留在「待我审批」
        // DRAFT 状态可能未启动流程（process_instance_id=null），需判空
        String pid = apply.getProcessInstanceId();
        if (pid != null && !pid.isBlank()) {
            try {
                workflowApi.cancelProcess(pid, reason);
            } catch (Exception ex) {
                log.warn("[AllocAdjustService.withdraw] cancelProcess 失败 pid={}, 业务侧已置 WITHDRAWN；err={}",
                        pid, ex.getMessage());
            }
        }
        log.info("[AllocAdjustService.withdraw] id={}, reason={}, operator={}", id, reason, operator);
    }

    /**
     * 渠道撤回申请（带越权校验）：仅申请创建人本人可撤回.
     *
     * <p>面向 callpu 等外部渠道（无平台登录态，operator 由报文 EmployeeNo 传入）。
     * 与 {@link #withdraw} 的差异：撤回前先校验 {@code operator == apply.createdBy}，
     * 防止他人越权撤回；状态/流程取消逻辑完全复用 {@link #withdraw}。</p>
     *
     * @param id       申请 ID（手机端 perfAdjustNo）
     * @param reason   撤回原因
     * @param operator 操作人 empId（必须等于申请创建人）
     * @throws PerfException VALIDATION_FAILED（申请不存在 / 非本人 / 状态不可撤回）
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdrawByApplicant(String id, String reason, String operator) {
        if (isBlank(id)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "id 为空");
        }
        PerfAllocAdjustApply apply = applyMapper.selectByAllocApplyId(id);
        if (apply == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "申请不存在: " + id);
        }
        // 越权校验：高危操作，仅申请创建人本人可撤回
        if (operator == null || !operator.equals(apply.getCreatedBy())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "无权撤回他人申请: " + id);
        }
        withdraw(id, reason, operator);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本 submit + 回显.
     */
    public Map<String, String> submitDto(SubmitAllocAdjustCmd cmd) {
        String id = submit(cmd);
        ApplyWithItems loaded = getById(id);
        return Map.of(
                "id", loaded.getApply().getId(),
                "applyNo", loaded.getApply().getApplyNo(),
                "status", loaded.getApply().getStatus());
    }

    /**
     * Controller 专用：保存为草稿 + 回显 {id, applyNo, status=DRAFT}.
     */
    public Map<String, String> saveDraftDto(SubmitAllocAdjustCmd cmd, String id) {
        String savedId = saveDraft(cmd, id);
        ApplyWithItems loaded = getById(savedId);
        return Map.of(
                "id", loaded.getApply().getId(),
                "applyNo", loaded.getApply().getApplyNo(),
                "status", loaded.getApply().getStatus());
    }

    /**
     * Controller 专用：草稿提交审批 + 回显 {id, applyNo, status=IN_APPROVAL}.
     */
    public Map<String, String> submitDraftDto(String id, String operator) {
        String submittedId = submitDraft(id, operator);
        ApplyWithItems loaded = getById(submittedId);
        return Map.of(
                "id", loaded.getApply().getId(),
                "applyNo", loaded.getApply().getApplyNo(),
                "status", loaded.getApply().getStatus());
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本详情查询（含 items）.
     * <p>cust_id/客户名称直接读 apply 提交时快照，不再反查客户主档（cust_no 字段已并入 cust_id）.
     */
    public AllocAdjustRespDTO getByIdDto(String id) {
        ApplyWithItems bundle = getById(id);
        AllocAdjustRespDTO dto = toRespDto(bundle.getApply(), bundle.getItems());
        // 展开申请人姓名 + 主机构名（仅详情，列表不展开避免 N+1）
        // 任何一项查询失败不阻塞主流程，对应字段留 null
        String createdBy = bundle.getApply().getCreatedBy();
        if (!isBlank(createdBy)) {
            try {
                dto.setCreatedByName(userApi.getUserName(createdBy));
            } catch (Exception e) {
                log.warn("[AllocAdjustService.getByIdDto] 申请人姓名查询失败 createdBy={}, err={}",
                        createdBy, e.toString());
            }
            try {
                // 申请人工号（PT_USER.username，展示用）：createdBy 为 USER_ID 内部主键
                com.bank.branch.platform.auth.api.dto.UserDTO u = userApi.getUserByEmpId(createdBy);
                if (u != null) {
                    dto.setCreatedByUsername(u.getUsername());
                }
            } catch (Exception e) {
                log.warn("[AllocAdjustService.getByIdDto] 申请人工号查询失败 createdBy={}, err={}",
                        createdBy, e.toString());
            }
            try {
                OrgDTO org = orgApi.getUserMainOrg(createdBy);
                if (org != null) {
                    dto.setCreatedByOrgName(org.getOrgName());
                }
            } catch (Exception e) {
                log.warn("[AllocAdjustService.getByIdDto] 申请人主机构查询失败 createdBy={}, err={}",
                        createdBy, e.toString());
            }
        }
        // 明细员工 username/中文名/部门 已在 toRespDto 直接读 item 快照字段，无需再关联 PT_USER/机构表
        // R2：「原业绩分配」从当前生效分配(is_original='2')反显；为空回退持久化 ORIGIN 快照
        reflectOriginalAllocFromCurrent(dto, bundle.getApply());
        return dto;
    }

    /**
     * 编辑/查看「原业绩分配」改从 {@code cust_alloc_relation} 当前生效分配（{@code is_original='2'}）反显.
     *
     * <p>命中当前生效分配时：保留 NEW 明细，ORIGIN 明细整体替换为当前生效分配；
     * 未命中（无 is_original='2'）时：保留持久化的 ORIGIN 快照不变（避免老数据视图空白）。
     */
    private void reflectOriginalAllocFromCurrent(AllocAdjustRespDTO dto, PerfAllocAdjustApply apply) {
        List<com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO> current =
                allocAdjustPreviewService.getLastApprovedAllocPreview(apply.getCustId(), apply.getAllocDim());
        if (current == null || current.isEmpty()) {
            return;
        }
        List<AllocAdjustRespDTO.Item> rebuilt = new ArrayList<>();
        if (dto.getItems() != null) {
            for (AllocAdjustRespDTO.Item it : dto.getItems()) {
                if (!"ORIGIN".equals(it.getItemKind())) {
                    rebuilt.add(it); // 保留 NEW
                }
            }
        }
        for (com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO src : current) {
            AllocAdjustRespDTO.Item iDto = new AllocAdjustRespDTO.Item();
            iDto.setItemKind("ORIGIN");
            iDto.setAcctNo(src.getAccountNo());
            iDto.setEmpId(src.getEmpId());
            iDto.setUsername(src.getUsername());
            iDto.setEmpChnName(src.getEmpChnName());
            iDto.setOrgCode(src.getOrgCode());
            iDto.setOrgName(src.getOrgName());
            iDto.setRatio(src.getRatio());
            rebuilt.add(iDto);
        }
        dto.setItems(rebuilt);
    }

    /**
     * 供审批详情等读取「原业绩分配」= 当前生效分配（cust_alloc_relation is_original='2'）.
     *
     * @param custId   客户编号
     * @param allocDim 分配维度 RULE/ACCOUNT/null
     * @return 原业绩分配预览项（可能为空）
     */
    public List<com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO>
            getOriginalAllocPreview(String custId, String allocDim) {
        return allocAdjustPreviewService.getLastApprovedAllocPreview(custId, allocDim);
    }

    /**
     * 按 token（工号或登录名）双解析员工，Key=原始 token.
     *
     * <p>先按工号(USER_ID)解析，未命中的 token 再按登录名(USERNAME)兜底；查询失败返回已解析部分。
     */
    private java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> resolveUsersByTokens(
            java.util.Collection<String> tokens) {
        java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> userMap = new java.util.HashMap<>();
        if (tokens == null || tokens.isEmpty()) {
            return userMap;
        }
        java.util.LinkedHashSet<String> distinct = new java.util.LinkedHashSet<>();
        for (String t : tokens) {
            if (!isBlank(t)) {
                distinct.add(t);
            }
        }
        if (distinct.isEmpty()) {
            return userMap;
        }
        try {
            List<com.bank.branch.platform.auth.api.dto.UserDTO> byId =
                    userApi.getUserByEmpIds(new java.util.ArrayList<>(distinct));
            if (byId != null) {
                for (com.bank.branch.platform.auth.api.dto.UserDTO u : byId) {
                    if (u != null && u.getEmpId() != null) {
                        userMap.put(u.getEmpId(), u);
                    }
                }
            }
            List<String> remaining = new java.util.ArrayList<>();
            for (String token : distinct) {
                if (!userMap.containsKey(token)) {
                    remaining.add(token);
                }
            }
            if (!remaining.isEmpty()) {
                List<com.bank.branch.platform.auth.api.dto.UserDTO> byName = userApi.getUsersByUsernames(remaining);
                if (byName != null) {
                    for (com.bank.branch.platform.auth.api.dto.UserDTO u : byName) {
                        if (u != null && u.getUsername() != null) {
                            userMap.putIfAbsent(u.getUsername(), u);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[AllocAdjustService.resolveUsersByTokens] 员工信息查询失败，err={}", e.toString());
        }
        return userMap;
    }

    /**
     * 计算「原业绩分配模块」会签名单：取该客户当前维度上次审批通过的分配明细员工，
     * 归一到工号(USER_ID) 去重，作为 corp_v1 {@code original_owner_approve} 并行多实例 collection.
     *
     * <p>emp_id 历史可能存登录名，归一到工号才能与待办（按当前用户工号匹配）对齐；解析不到则保留原值。
     *
     * @return 工号列表（可能为空）
     */
    private List<String> resolveOriginalOwnerEmpIds(String custId, String allocDim,
            List<SubmitAllocAdjustCmd.OriginalItem> manual) {
        List<com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO> owners =
                allocAdjustPreviewService.getLastApprovedAllocPreview(custId, allocDim);
        java.util.LinkedHashSet<String> rawEmpIds = new java.util.LinkedHashSet<>();
        if (owners != null) {
            for (com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO o : owners) {
                if (o != null && !isBlank(o.getEmpId())) {
                    rawEmpIds.add(o.getEmpId());
                }
            }
        }
        // 历史审批通过分配查不到 → 回退本次手工录入的原业绩分配工号
        if (rawEmpIds.isEmpty() && manual != null) {
            for (SubmitAllocAdjustCmd.OriginalItem m : manual) {
                if (m != null && !isBlank(m.getEmpId())) {
                    rawEmpIds.add(m.getEmpId());
                }
            }
        }
        if (rawEmpIds.isEmpty()) {
            return new ArrayList<>();
        }
        java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> userMap = resolveUsersByTokens(rawEmpIds);
        java.util.LinkedHashSet<String> userIds = new java.util.LinkedHashSet<>();
        for (String token : rawEmpIds) {
            com.bank.branch.platform.auth.api.dto.UserDTO u = userMap.get(token);
            userIds.add(u != null && u.getEmpId() != null ? u.getEmpId() : token);
        }
        return new ArrayList<>(userIds);
    }

    /**
     * 构建「原业绩分配」明细实体（item_kind=ORIGIN）.
     *
     * <p>机构号/名称用手工录入值（可能异于员工主机构）；员工登录名/中文名优先用前端下拉快照，
     * 缺失时按工号解析 PT_USER 兜底。
     */
    private List<PerfAllocAdjustItem> buildOriginalItems(String applyId,
            List<SubmitAllocAdjustCmd.OriginalItem> originals) {
        if (originals == null || originals.isEmpty()) {
            return new ArrayList<>();
        }
        java.util.LinkedHashSet<String> empIds = new java.util.LinkedHashSet<>();
        for (SubmitAllocAdjustCmd.OriginalItem o : originals) {
            if (o != null && !isBlank(o.getEmpId())) {
                empIds.add(o.getEmpId());
            }
        }
        java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> userMap = resolveUsersByTokens(empIds);
        List<PerfAllocAdjustItem> list = new ArrayList<>(originals.size());
        for (SubmitAllocAdjustCmd.OriginalItem o : originals) {
            if (o == null || isBlank(o.getEmpId())) {
                continue;
            }
            com.bank.branch.platform.auth.api.dto.UserDTO u = userMap.get(o.getEmpId());
            PerfAllocAdjustItem e = new PerfAllocAdjustItem();
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setApplyId(applyId);
            e.setAcctNo(o.getAcctNo());
            e.setEmpId(o.getEmpId());
            e.setUsername(!isBlank(o.getUsername()) ? o.getUsername()
                    : (u != null && !isBlank(u.getUsername()) ? u.getUsername() : o.getEmpId()));
            e.setEmpChnName(!isBlank(o.getEmpChnName()) ? o.getEmpChnName()
                    : (u != null ? u.getDisplayName() : null));
            e.setOrgCode(o.getOrgCode());
            e.setOrgName(o.getOrgName());
            e.setRatio(o.getRatio());
            list.add(e);
        }
        return list;
    }

    /**
     * V1.3 R4.1 / V1.4 S1.3：Controller 专用 DTO 版本分页（列表不含 items）.
     *
     * <p>V1.4 S1.3 增强：注入 PerfScopeHelper 5 参 overload，bizKeyCol="business_key"，
     * workflowPrefix="perf_alloc_adjust_"，使 WORKFLOW_PARTICIPANT 角色的用户可以看到
     * 自己参与过的分配调整申请（按 businessKey 过滤）。
     *
     * <p>ScopeColumns 映射（perf_alloc_adjust_apply 表）：
     * <ul>
     *   <li>ownerEmpCol = "created_by"（SELF 降级到 created_by，该表无独立 owner_emp_id）</li>
     *   <li>assigneeCol = "created_by"（无 assignee 列）</li>
     *   <li>createdByCol = "created_by"（SELF_CREATED 语义准确）</li>
     *   <li>ownerOrgCol = "owner_org_id"（ORG 对应列）</li>
     *   <li>bizKeyCol = "business_key"（V1.4 S1.3 新增：WORKFLOW_PARTICIPANT 对应列）</li>
     * </ul>
     */
    public PageResult<AllocAdjustRespDTO> pageDto(String status, String bizKind, String custId,
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
                status, bizKind, custId, ownerOrgId, createdBy,
                frag.getSql(), frag.getParams());
        List<PerfAllocAdjustApply> rows = applyMapper.selectByConditionsWithScope(
                status, bizKind, custId, ownerOrgId, createdBy, offset, pageSize,
                frag.getSql(), frag.getParams());

        List<AllocAdjustRespDTO> dtos = new ArrayList<>(rows.size());
        for (PerfAllocAdjustApply apply : rows) {
            dtos.add(toRespDto(apply, java.util.Collections.emptyList()));
        }
        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * V1.3 R4.1：entity → DTO 装配下沉到 Service. cust_id/客户名称直接读 apply 快照（cust_no 已并入 cust_id）.
     */
    private AllocAdjustRespDTO toRespDto(PerfAllocAdjustApply apply, List<PerfAllocAdjustItem> items) {
        AllocAdjustRespDTO dto = new AllocAdjustRespDTO();
        dto.setId(apply.getId());
        dto.setApplyNo(apply.getApplyNo());
        dto.setCustId(apply.getCustId());
        dto.setCustName(apply.getCustName());
        // 余额概览快照直接透传
        dto.setCurrBal(apply.getCurrBal());
        dto.setMAvgBal(apply.getMAvgBal());
        dto.setQAvgBal(apply.getQAvgBal());
        dto.setYAvgBal(apply.getYAvgBal());
        dto.setLoanCurrBal(apply.getLoanCurrBal());
        dto.setLoanMAvgBal(apply.getLoanMAvgBal());
        dto.setLoanQAvgBal(apply.getLoanQAvgBal());
        dto.setLoanYAvgBal(apply.getLoanYAvgBal());
        dto.setCustType(apply.getCustType());
        dto.setAllocDim(apply.getAllocDim());
        dto.setBizKind(apply.getBizKind());
        dto.setAccountNo(apply.getAccountNo());
        dto.setStatus(apply.getStatus());
        dto.setBusinessKey(apply.getBusinessKey());
        dto.setProcessInstanceId(apply.getProcessInstanceId());
        dto.setOwnerOrgId(apply.getOwnerOrgId());
        dto.setRemark(apply.getRemark());
        dto.setCreatedBy(apply.getCreatedBy());
        dto.setCreatedTime(apply.getCreatedTime());
        dto.setUpdatedBy(apply.getUpdatedBy());
        dto.setUpdatedTime(apply.getUpdatedTime());
        List<AllocAdjustRespDTO.Item> itemDtos = new ArrayList<>(items == null ? 0 : items.size());
        if (items != null) {
            for (PerfAllocAdjustItem it : items) {
                AllocAdjustRespDTO.Item iDto = new AllocAdjustRespDTO.Item();
                iDto.setId(it.getId());
                // PERF_ALLOC_ADJUST_ITEM 现仅存调整明细，统一标 NEW；原业绩分配(ORIGIN)由
                // getByIdDto 的 reflectOriginalAllocFromCurrent 从 cust_alloc_relation 反显追加
                iDto.setItemKind("NEW");
                iDto.setAcctNo(it.getAcctNo());
                iDto.setEmpId(it.getEmpId());
                // 直接读提交时快照的员工/部门字段，不再关联 PT_USER/机构表；
                // 历史无快照(旧数据)时 username 回退工号，避免空白
                iDto.setUsername(!isBlank(it.getUsername()) ? it.getUsername() : it.getEmpId());
                iDto.setEmpChnName(it.getEmpChnName());
                iDto.setOrgCode(it.getOrgCode());
                iDto.setOrgName(it.getOrgName());
                iDto.setRatio(it.getRatio());
                iDto.setRemark(it.getRemark());
                iDto.setCreatedTime(it.getCreatedTime());
                itemDtos.add(iDto);
            }
        }
        dto.setItems(itemDtos);
        return dto;
    }

    /**
     * apply + items 二元组（供 Facade/Controller 组装 DTO）.
     */
    public static final class ApplyWithItems {
        private final PerfAllocAdjustApply apply;
        private final List<PerfAllocAdjustItem> items;

        public ApplyWithItems(PerfAllocAdjustApply apply, List<PerfAllocAdjustItem> items) {
            this.apply = apply;
            this.items = items;
        }

        public PerfAllocAdjustApply getApply() {
            return apply;
        }

        public List<PerfAllocAdjustItem> getItems() {
            return items;
        }
    }

    /**
     * 主字段必填 + 枚举校验.
     */
    private void validateBasic(SubmitAllocAdjustCmd cmd) {
        if (cmd == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cmd is null");
        }
        if (isBlank(cmd.getCustId())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "custId 为空");
        }
        if (isBlank(cmd.getAllocDim()) || !ALLOWED_ALLOC_DIMS.contains(cmd.getAllocDim())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "allocDim 非法: " + cmd.getAllocDim());
        }
        // 按账号分配(ACCOUNT)时账号必填
        if ("ACCOUNT".equals(cmd.getAllocDim()) && isBlank(cmd.getAccountNo())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "按账号分配时账号必填");
        }
        if (isBlank(cmd.getBizKind())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "bizKind 为空");
        }
        if (isBlank(cmd.getOwnerOrgId())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "ownerOrgId 为空");
        }
        if (isBlank(cmd.getApplicant())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "applicant 为空");
        }
    }

    /**
     * items 校验：非空、empId 去重、ratio 合计 ≤ 100（RULE 维度）.
     */
    private void validateItems(SubmitAllocAdjustCmd cmd) {
        List<SubmitAllocAdjustCmd.Item> items = cmd.getItems();
        if (items == null || items.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "items 不能为空");
        }
        Set<String> empIds = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (SubmitAllocAdjustCmd.Item it : items) {
            if (isBlank(it.getEmpId())) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "item.empId 为空");
            }
            if (it.getRatio() == null || it.getRatio().signum() < 0) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "item.ratio 非法: " + it.getRatio());
            }
            if (!empIds.add(it.getEmpId())) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "item.empId 重复: " + it.getEmpId());
            }
            sum = sum.add(it.getRatio());
        }
        // RULE 维度要求总占比 ≤ 100；ACCOUNT 维度因单账号下多人分配可能等于或略小于 100，同样限制 ≤ 100 用于防呆.
        if (sum.compareTo(RULE_RATIO_MAX) > 0) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "items ratio 合计超过 100: " + sum);
        }
    }

    /**
     * 查询当前活跃分配关系中首条记录的 empId 作为"原业绩所属人".
     * <p>
     * 用于 BPMN 中 {@code original_owner_approve} 节点的单人指派：
     * 当公司部/零售部经办在 biz_dept_review 节点勾选 needsOriginalOwnerApprove
     * 时，流程会路由到该节点，由 flowable:assignee="${originalOwnerEmpId}" 直接指派.
     *
     * <p>多 owner 共担一个客户的场景：取首条（按 mapper 默认排序），后续如需多人会签
     * 可改 candidateUsers 写法.
     *
     * @return 当前活跃分配的首个 empId；无活跃分配时返回 null
     */
    private String resolveOriginalOwnerEmpId(String custId, String bizKind) {
        List<CustAllocRelation> current = allocRelationMapper.selectCurrentByCustAndBiz(
                custId, bizKind, java.time.LocalDate.now());
        if (current == null || current.isEmpty()) {
            return null;
        }
        for (CustAllocRelation rel : current) {
            if (!isBlank(rel.getEmpId())) {
                return rel.getEmpId();
            }
        }
        return null;
    }


    /**
     * 按 bizKind 前缀路由流程（对齐生产 DDL：不引入 adjust_type 字段，
     * 对公/零售完全靠 biz_kind 判定）：
     * <ul>
     *   <li>{@code CORP} / {@code CORP_*}（对公）→ {@link #PROCESS_KEY_CORP}
     *       覆盖场景：CORP_LOAN / CORP_DEPOSIT / CORP_FOREX 等</li>
     *   <li>{@code RETAIL} / {@code RETAIL_*} / {@code PER} / {@code PER_*}
     *       / {@code FEE_BIZ} / {@code FEE_*}（零售 + 个人 + 中间业务）→ {@link #PROCESS_KEY_RETAIL}
     *       覆盖场景：RETAIL_CARD / RETAIL_LOAN / PER_DEP / PER_LOAN / FEE_BIZ 等。
     *       业务上个人业务（PER_*）与中间业务（FEE_*）均由零售部门管，同条审批线。</li>
     *   <li>其他 → 抛 BIZ_KIND_INVALID（未知业务前缀应在字典层预防，
     *       此处作为最后防线）</li>
     * </ul>
     *
     * <p>大小写不敏感：内部统一 {@code toUpperCase} 后匹配.
     *
     * @param bizKind 业务种类（非空）
     * @return BPMN 流程定义 key
     * @throws PerfException BIZ_KIND_INVALID 当 bizKind 不属于 CORP / RETAIL / PER / FEE 任一族
     */
    private String resolveProcessKey(String custType, String bizKind) {
        // 优先按客户类型路由（对公→corp_v1，零售→retail_v1）
        if (custType != null && !custType.isBlank()) {
            String ct = custType.toUpperCase();
            if ("CORP".equals(ct)) return PROCESS_KEY_CORP;
            if ("RETAIL".equals(ct)) return PROCESS_KEY_RETAIL;
        }
        // 兼容旧数据：按 bizKind 前缀回退
        String upper = bizKind.toUpperCase();
        if (upper.startsWith("CORP_") || upper.equals("CORP")) {
            return PROCESS_KEY_CORP;
        }
        if (upper.startsWith("RETAIL_") || upper.equals("RETAIL")
                || upper.startsWith("PER_") || upper.equals("PER")
                || upper.startsWith("FEE_") || upper.equals("FEE_BIZ")) {
            return PROCESS_KEY_RETAIL;
        }
        throw new PerfException(PerfErrorCode.BIZ_KIND_INVALID, bizKind);
    }

    /**
     * 生成申请 ID（32 位无横线 UUID）.
     */
    private String genApplyId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成申请编号：AA{yyyyMMdd}{UUID 8 位}.
     */
    private String genApplyNo() {
        String datePart = LocalDateTime.now().format(APPLY_NO_DATE_FMT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "AA" + datePart + suffix;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
