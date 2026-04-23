package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
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

    /** 申请编号日期前缀格式（如 AA20260423xxxx）. */
    private static final DateTimeFormatter APPLY_NO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PerfAllocAdjustApplyMapper applyMapper;
    private final PerfAllocAdjustItemMapper itemMapper;
    private final CustomerQueryApi customerQueryApi;
    private final WorkflowApi workflowApi;

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
        validateCustomer(cmd.getCustId());

        String applyId = genApplyId();
        String applyNo = genApplyNo();
        String businessKey = "ALLOC_ADJUST:" + applyId;
        String processKey = resolveProcessKey(cmd.getBizKind());

        // 1. 落地主表（status=IN_APPROVAL，尚无 processInstanceId）
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId(applyId);
        apply.setApplyNo(applyNo);
        apply.setCustId(cmd.getCustId());
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

        // 2. 批量插入明细
        List<PerfAllocAdjustItem> items = new ArrayList<>(cmd.getItems().size());
        for (SubmitAllocAdjustCmd.Item it : cmd.getItems()) {
            PerfAllocAdjustItem entity = new PerfAllocAdjustItem();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setApplyId(applyId);
            entity.setEmpId(it.getEmpId());
            entity.setRatio(it.getRatio());
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
        startCmd.setTitle("分配关系调整-" + cmd.getCustId() + "-" + applyNo);
        Map<String, Object> vars = new HashMap<>();
        vars.put("applyId", applyId);
        vars.put("custId", cmd.getCustId());
        vars.put("bizKind", cmd.getBizKind());
        vars.put("allocDim", cmd.getAllocDim());
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
        PerfAllocAdjustApply apply = applyMapper.selectById(id);
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
        PerfAllocAdjustApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "申请不存在: " + id);
        }
        if (!"IN_APPROVAL".equals(apply.getStatus()) && !"DRAFT".equals(apply.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "申请状态不可撤回: " + apply.getStatus());
        }
        // processInstanceId 传 null 避免覆写历史值
        applyMapper.updateStatus(id, "REJECTED", null);
        log.info("[AllocAdjustService.withdraw] id={}, reason={}, operator={}", id, reason, operator);
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
     * 客户存在性校验：调 {@code CustomerQueryApi.getCustomer}.
     */
    private void validateCustomer(String custId) {
        Optional<CustomerDTO> opt = customerQueryApi.getCustomer(custId);
        if (opt.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "客户不存在: " + custId);
        }
    }

    /**
     * 按 bizKind 前缀路由流程（对齐生产 DDL：不引入 adjust_type 字段，
     * 对公/零售完全靠 biz_kind 判定）：
     * <ul>
     *   <li>{@code CORP} / {@code CORP_*}（对公）→ {@link #PROCESS_KEY_CORP}
     *       覆盖场景：CORP_LOAN / CORP_DEPOSIT / CORP_FOREX 等</li>
     *   <li>{@code RETAIL} / {@code RETAIL_*}（零售）→ {@link #PROCESS_KEY_RETAIL}
     *       覆盖场景：RETAIL_CARD / RETAIL_LOAN / RETAIL_MORTGAGE 等</li>
     *   <li>其他 → 抛 BIZ_KIND_INVALID（未知业务前缀应在字典层预防，
     *       此处作为最后防线）</li>
     * </ul>
     *
     * <p>大小写不敏感：内部统一 {@code toUpperCase} 后匹配.
     *
     * @param bizKind 业务种类（非空）
     * @return BPMN 流程定义 key
     * @throws PerfException BIZ_KIND_INVALID 当 bizKind 不以 CORP_ / RETAIL_ 开头
     */
    private String resolveProcessKey(String bizKind) {
        String upper = bizKind.toUpperCase();
        if (upper.startsWith("CORP_") || upper.equals("CORP")) {
            return PROCESS_KEY_CORP;
        }
        if (upper.startsWith("RETAIL_") || upper.equals("RETAIL")) {
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
