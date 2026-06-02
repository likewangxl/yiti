package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
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
    private final CustomerQueryApi customerQueryApi;
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
        // 按客户编号(cust_no)查找客户主档；apply.cust_id 列保存客户内部主键 id，与现有跨模块 join 保持兼容
        String internalCustId = resolveInternalCustIdByCustNo(cmd.getCustNo());

        // 同客户同维度去重：同一客户编号(cust_no) + 同一分配维度(alloc_dim)已存在审批中(IN_APPROVAL)的调整申请时，
        // 不允许重复提交，避免并行多笔调整审批落地后相互覆盖分配关系。
        // 按 cust_no 去重（而非 cust_id）：手工录入客户 cust_id 为 null，用业务客户编号才能正确去重。
        // 去重粒度精确到维度：RULE 审批中不阻塞 ACCOUNT 的新提交，反之亦然。
        // 注意：PerfException 的 errorCode.format 已自动拼「基础消息 + ": " + arg」，此处只传 custNo，避免消息重复。
        if (applyMapper.countInApprovalByCustAndDim(cmd.getCustNo(), cmd.getAllocDim()) > 0) {
            throw new PerfException(PerfErrorCode.ALLOC_ADJUST_APPLY_RUNNING, cmd.getCustNo());
        }

        String applyId = genApplyId();
        String applyNo = genApplyNo();
        String businessKey = "ALLOC_ADJUST:" + applyId;
        String processKey = resolveProcessKey(cmd.getCustType(), cmd.getBizKind());

        // 1. 落地主表（status=IN_APPROVAL，尚无 processInstanceId）
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId(applyId);
        apply.setApplyNo(applyNo);
        apply.setCustId(internalCustId);
        // 提交时把前端反显的客户编号/名称 + 余额概览快照入库，列表/详情直接读，不再实时反查
        apply.setCustNo(cmd.getCustNo());
        apply.setCustName(cmd.getCustName());
        apply.setCurrBal(cmd.getCurrBal());
        apply.setMAvgBal(cmd.getMAvgBal());
        apply.setQAvgBal(cmd.getQAvgBal());
        apply.setYAvgBal(cmd.getYAvgBal());
        apply.setCustType(cmd.getCustType());
        apply.setAllocDim(cmd.getAllocDim());
        apply.setBizKind(cmd.getBizKind());
        apply.setAccountNo(cmd.getAccountNo());
        apply.setStatus("IN_APPROVAL");
        apply.setBusinessKey(businessKey);
        apply.setProcessInstanceId(null);
        apply.setOwnerOrgId(cmd.getOwnerOrgId());
        apply.setRemark(cmd.getReason());
        apply.setCreatedBy(cmd.getApplicant());
        LocalDateTime now = LocalDateTime.now();
        apply.setCreatedTime(now);
        apply.setUpdatedBy(cmd.getApplicant());
        apply.setUpdatedTime(now);
        applyMapper.insert(apply);

        // 2. 批量插入明细。提交时快照员工 username/中文姓名/所属部门号/部门名称存入明细，
        //    后续预览/详情查询直接读这些列，不再实时关联 PT_USER / 机构表（口径冻结在提交时点）。
        java.util.LinkedHashSet<String> empIds = new java.util.LinkedHashSet<>();
        for (SubmitAllocAdjustCmd.Item it : cmd.getItems()) {
            if (!isBlank(it.getEmpId())) {
                empIds.add(it.getEmpId());
            }
        }
        java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> userMap = resolveUsersByTokens(empIds);
        List<PerfAllocAdjustItem> items = new ArrayList<>(cmd.getItems().size());
        for (SubmitAllocAdjustCmd.Item it : cmd.getItems()) {
            PerfAllocAdjustItem entity = new PerfAllocAdjustItem();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setApplyId(applyId);
            entity.setEmpId(it.getEmpId());
            com.bank.branch.platform.auth.api.dto.UserDTO u = it.getEmpId() != null ? userMap.get(it.getEmpId()) : null;
            // username 解析不到时回退工号，避免空白；中文名/部门解析不到留空
            entity.setUsername((u != null && !isBlank(u.getUsername())) ? u.getUsername() : it.getEmpId());
            entity.setEmpChnName(u != null ? u.getDisplayName() : null);
            entity.setOrgCode(u != null ? u.getMainOrgCode() : null);
            entity.setOrgName(u != null ? u.getMainOrgName() : null);
            entity.setRatio(it.getRatio());
            entity.setRemark(it.getRemark());
            items.add(entity);
        }
        itemMapper.batchInsert(items);

        // 3. 启动 Flowable 流程 —— 在 apply 持久化之后、status 回写之前
        //    异常冒泡回滚事务，避免 apply 残留无 processInstanceId
        StartProcessCmd startCmd = new StartProcessCmd();
        startCmd.setBizType(BIZ_TYPE);
        startCmd.setBizId(applyId);
        startCmd.setBusinessKey(businessKey);
        startCmd.setProcessDefinitionKey(processKey);
        startCmd.setStartUser(cmd.getApplicant());
        startCmd.setStartOrgId(cmd.getOwnerOrgId());
        // 标题用业务编号(custNo)便于人工识别；流程变量 custId 写内部主键，下游 BPMN/Listener 已按此使用
        startCmd.setTitle("分配关系调整-" + cmd.getCustNo() + "-" + applyNo);
        Map<String, Object> vars = new HashMap<>();
        vars.put("applyId", applyId);
        vars.put("custId", internalCustId);
        vars.put("custNo", cmd.getCustNo());
        vars.put("bizKind", cmd.getBizKind());
        vars.put("allocDim", cmd.getAllocDim());
        // 原业绩所属人：按 (custId, bizKind) 查当前有效分配，取首条 empId 作为
        // BPMN original_owner_approve 节点的 flowable:assignee 单人指派候选；
        // 查不到（新客户或历史分配空）时不写此键，需要勾选"原业绩所属人审批"前请前端做防呆.
        String originalOwnerEmpId = resolveOriginalOwnerEmpId(internalCustId, cmd.getBizKind());
        if (originalOwnerEmpId != null) {
            vars.put("originalOwnerEmpId", originalOwnerEmpId);
        }
        // 原业绩分配会签名单（corp_v1 多实例）：取「原业绩分配模块」该客户当前维度上次审批通过明细的员工，
        // 归一到工号(USER_ID) 去重，作为 original_owner_approve 并行多实例 collection（每人一个子任务）。
        // 为空时不写此键（前端已禁止"交原业绩所属人审批"，此处兜底）。
        List<String> originalOwnerEmpIds = resolveOriginalOwnerEmpIds(cmd.getCustNo(), cmd.getAllocDim());
        if (!originalOwnerEmpIds.isEmpty()) {
            vars.put("originalOwnerEmpIds", originalOwnerEmpIds);
        }
        startCmd.setVariables(vars);
        WorkflowLaunchResp resp = workflowApi.startProcess(startCmd);

        // 4. 回写 processInstanceId（状态已 IN_APPROVAL，此次仅补 processInstanceId）
        applyMapper.updateStatus(applyId, "IN_APPROVAL", resp.getProcessInstanceId());
        log.info("[AllocAdjustService.submit] applyId={}, applyNo={}, processKey={}, pid={}",
                applyId, applyNo, processKey, resp.getProcessInstanceId());
        return applyId;
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
     * 撤回申请：IN_APPROVAL → REJECTED.
     *
     * <p>V1.2 简化实现：仅将本地 apply 状态置为 REJECTED，保留流程实例不做取消。
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
        // processInstanceId 传 null 避免覆写历史值
        applyMapper.updateStatus(id, "REJECTED", null);
        // 同步取消 Flowable 流程实例，否则该流程的 active task 会一直留在「待我审批」
        // DRAFT 状态可能未启动流程（process_instance_id=null），需判空
        String pid = apply.getProcessInstanceId();
        if (pid != null && !pid.isBlank()) {
            try {
                workflowApi.cancelProcess(pid, reason);
            } catch (Exception ex) {
                log.warn("[AllocAdjustService.withdraw] cancelProcess 失败 pid={}, 业务侧已置 REJECTED；err={}",
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
     * V1.3 R4.1：Controller 专用 DTO 版本详情查询（含 items）.
     * <p>响应回填 custNo：按 apply.custId(内部主键) 反查 cust_master.cust_no；客户已删除时 custNo=null.
     */
    public AllocAdjustRespDTO getByIdDto(String id) {
        ApplyWithItems bundle = getById(id);
        // 一次反查 cust_master 同时拿 custNo + custName（客户已删/查不到时均为 null）
        String custId = bundle.getApply().getCustId();
        CustomerDTO cust = isBlank(custId) ? null : customerQueryApi.getCustomer(custId).orElse(null);
        String custNo = cust != null ? cust.getCustNo() : null;
        String custName = cust != null ? cust.getCustName() : null;
        AllocAdjustRespDTO dto = toRespDto(bundle.getApply(), bundle.getItems(), custNo, custName);
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
        return dto;
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
    private List<String> resolveOriginalOwnerEmpIds(String custNo, String allocDim) {
        List<com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO> owners =
                allocAdjustPreviewService.getLastApprovedAllocPreview(custNo, allocDim);
        if (owners == null || owners.isEmpty()) {
            return new ArrayList<>();
        }
        java.util.LinkedHashSet<String> rawEmpIds = new java.util.LinkedHashSet<>();
        for (com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO o : owners) {
            if (o != null && !isBlank(o.getEmpId())) {
                rawEmpIds.add(o.getEmpId());
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
     * 单条 custId(内部主键) → custNo 反查，客户不存在返回 null.
     */
    private String lookupCustNo(String internalCustId) {
        if (isBlank(internalCustId)) {
            return null;
        }
        return customerQueryApi.getCustomer(internalCustId)
                .map(CustomerDTO::getCustNo)
                .orElse(null);
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

        // 批量反查 cust_master 拿 custNo + custName，避免循环单查；空 rows 跳过避免无谓 mapper 调用
        Map<String, CustomerDTO> custMap = batchLookupCustomers(rows);
        List<AllocAdjustRespDTO> dtos = new ArrayList<>(rows.size());
        for (PerfAllocAdjustApply apply : rows) {
            CustomerDTO c = custMap.get(apply.getCustId());
            dtos.add(toRespDto(apply, java.util.Collections.emptyList(),
                    c != null ? c.getCustNo() : null, c != null ? c.getCustName() : null));
        }
        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * 收集 rows 中所有非空 custId 一次性 listCustomers，返回内部主键 → custNo 映射；
     * rows 为空或全部 custId 为空时返回空 Map，不触发跨模块调用.
     */
    private Map<String, CustomerDTO> batchLookupCustomers(List<PerfAllocAdjustApply> rows) {
        if (rows == null || rows.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Set<String> ids = new HashSet<>();
        for (PerfAllocAdjustApply r : rows) {
            if (!isBlank(r.getCustId())) {
                ids.add(r.getCustId());
            }
        }
        if (ids.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<CustomerDTO> customers = customerQueryApi.listCustomers(new ArrayList<>(ids));
        Map<String, CustomerDTO> map = new HashMap<>(customers.size());
        for (CustomerDTO c : customers) {
            map.put(c.getId(), c);
        }
        return map;
    }

    /**
     * V1.3 R4.1：entity → DTO 装配下沉到 Service.
     * <p>{@code custNo} 由调用方按内部主键反查后传入（单条 lookupCustNo / 批量 batchLookupCustNos），
     * 查不到时传 null；此时 DTO 的 custNo 兜底回退用 apply.custId 展示（84f227e0 custNo兜底，
     * 不抛错以兼容历史已删客户的 apply 行）.
     */
    private AllocAdjustRespDTO toRespDto(PerfAllocAdjustApply apply, List<PerfAllocAdjustItem> items,
                                        String custNo, String custName) {
        AllocAdjustRespDTO dto = new AllocAdjustRespDTO();
        dto.setId(apply.getId());
        dto.setApplyNo(apply.getApplyNo());
        dto.setCustId(apply.getCustId());
        // 优先读提交时快照的客户编号/名称；快照为空（历史行）回退到反查值，再兜底 custId
        dto.setCustNo(!isBlank(apply.getCustNo()) ? apply.getCustNo()
                : (custNo != null ? custNo : apply.getCustId()));
        dto.setCustName(!isBlank(apply.getCustName()) ? apply.getCustName() : custName);
        // 余额概览快照直接透传
        dto.setCurrBal(apply.getCurrBal());
        dto.setMAvgBal(apply.getMAvgBal());
        dto.setQAvgBal(apply.getQAvgBal());
        dto.setYAvgBal(apply.getYAvgBal());
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
        if (isBlank(cmd.getCustNo())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "custNo 为空");
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
     * 按客户编号(cust_no)查 cust_master 主档，命中则返回内部主键 id；
     * <p>不做提交期存在性校验——客户编号在前端填写时已基于 XAN_M98 统计表反显校验过，
     * 这里查不到主档（如客户主档与统计表口径不一致、或尚未建档）时不再抛错，
     * 直接回退用 custNo 本身作为 cust_id 落库，保证申请可正常提交。
     * 下游 resolveOriginalOwnerEmpId 查不到有效分配会返回 null（"新客户或历史分配空"分支已优雅处理）。
     */
    private String resolveInternalCustIdByCustNo(String custNo) {
        // 客户主档命中→cust_id 存内部主键；未命中（手工录入客户）→返回 null，客户编号只存入 cust_no，
        // 不再把业务客户编号回填到 cust_id（避免 cust_id/cust_no 语义混淆）。
        Optional<CustomerDTO> opt = customerQueryApi.getCustomerByCustNo(custNo);
        return opt.map(CustomerDTO::getId).orElse(null);
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
