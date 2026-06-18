package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.event.AllocationAdjustmentApprovedEvent;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 分配调整流程完成监听器 (V1.2 Q2.3 + Q2.6).
 *
 * <p>订阅 workflow-center 的
 * {@link ProcessCompletedEvent}，
 * 按 businessKey 前缀 {@code ALLOC_ADJUST:} 筛选本模块关心的调整审批流程。
 *
 * <p>APPROVED 动作：
 * <ol>
 *   <li>读 perf_alloc_adjust_item 得到调整明细</li>
 *   <li>为每个 item 插入一条新的 cust_alloc_relation（effective_date=今天，end_date=null），
 *       旧记录的 end_date 由导入或后续批处理负责切分（V1.2 简化：新旧共存，查询时按时间线选择）</li>
 *   <li>updateStatus(APPROVED)（不覆写 processInstanceId）</li>
 *   <li>发布 {@link AllocationAdjustmentApprovedEvent}（供 cust_alloc_relation 缓存失效、
 *       报表快照重算、客户经理通知等下游消费）</li>
 * </ol>
 *
 * <p>REJECTED 动作：仅 updateStatus(REJECTED)，不改 cust_alloc_relation，不发事件。
 *
 * <p>apply 不存在：幂等跳过（防止流程重投递重复处理）。
 *
 * <p>事务策略：@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)
 * 保证 workflow-center 事件事务提交后执行，同时兼容无事务场景；
 * 本 Listener 处理方法自身用 @Transactional 包裹，批量写入要么全部生效要么全部回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllocAdjustCompletedListener {

    /** 本模块关心的 businessKey 前缀. */
    public static final String BIZ_KEY_PREFIX = "ALLOC_ADJUST:";

    /** workflow outcome 语义：审批通过. */
    private static final String OUTCOME_APPROVED = "APPROVED";

    /** workflow outcome 语义：审批拒绝. */
    private static final String OUTCOME_REJECTED = "REJECTED";

    /** is_original：新分配默认值 2（当前生效，非原分配；存量被新分配取代时置 1）. */
    private static final String IS_ORIGINAL_NO = "2";

    private final PerfAllocAdjustApplyMapper applyMapper;
    private final PerfAllocAdjustItemMapper itemMapper;
    private final CustAllocRelationMapper allocRelationMapper;
    private final PerfEventPublisher eventPublisher;
    private final com.bank.branch.platform.governance.api.NotifyApi notifyApi;
    /** 审批通过插入分配关系时，按工号回填姓名/部门快照. */
    private final com.bank.branch.platform.auth.api.UserApi userApi;

    /**
     * 监听流程完成事件入口.
     *
     * @param event workflow-center 发布的流程完成事件（含 outcome 语义）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onProcessCompleted(ProcessCompletedEvent event) {
        String businessKey = event.businessKey();
        if (businessKey == null || !businessKey.startsWith(BIZ_KEY_PREFIX)) {
            // 非分配调整流程：交给其他监听器处理
            return;
        }

        log.info("[AllocAdjustCompletedListener] businessKey={}, outcome={}, pid={}",
                businessKey, event.outcome(), event.processInstanceId());

        PerfAllocAdjustApply apply = applyMapper.selectByBusinessKey(businessKey);
        if (apply == null) {
            // 幂等保护：流程重投递 / 测试环境脏数据 场景直接跳过
            log.warn("[AllocAdjustCompletedListener] apply 不存在，跳过 businessKey={}", businessKey);
            return;
        }

        if (OUTCOME_APPROVED.equals(event.outcome())) {
            handleApproved(apply);
        } else if (OUTCOME_REJECTED.equals(event.outcome())) {
            handleRejected(apply);
        } else {
            // 未识别的 outcome（CANCELLED 等）：保守不改数据，打日志
            log.warn("[AllocAdjustCompletedListener] 未识别 outcome={}，businessKey={}",
                    event.outcome(), businessKey);
        }
    }

    /**
     * 审批通过：插入新分配关系 + 更新状态 + 发事件.
     */
    private void handleApproved(PerfAllocAdjustApply apply) {
        // PERF_ALLOC_ADJUST_ITEM 现仅存调整明细（item_kind 已废弃，原业绩分配在 cust_alloc_relation），
        // 全部落地为生效分配关系。
        List<PerfAllocAdjustItem> items = itemMapper.selectByApplyId(apply.getId());
        LocalDate effectiveDate = LocalDate.now();

        // cust_id 即客户编号（cust_no 字段已并入 cust_id），提交侧恒有值，直接作为分配关系客户键。
        String relCustId = apply.getCustId();

        if (!items.isEmpty()) {
            // 插入新分配前：按 cust_id + cust_type + alloc_dim + account_no 命中的存量分配置为「原分配」
            // （is_original=1）并把失效日期 end_date 置为当天，让本次新插入的 is_original=2 行成为
            // 唯一当前分配。cust_type 取自审批申请。
            allocRelationMapper.markOriginalByKey(
                    relCustId, apply.getCustType(), apply.getAllocDim(), apply.getAccountNo(),
                    effectiveDate);
        }

        // 按工号批量解析 姓名/部门，插入时回填 fullname/dept_no/dept_name（明细快照为空时兜底）
        java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> userMap = resolveUserMap(items);

        for (PerfAllocAdjustItem it : items) {
            com.bank.branch.platform.auth.api.dto.UserDTO u = userMap.get(it.getEmpId());
            CustAllocRelation rel = new CustAllocRelation();
            rel.setId(UUID.randomUUID().toString().replace("-", ""));
            rel.setCustId(relCustId);
            rel.setCustType(apply.getCustType());
            rel.setAllocDim(apply.getAllocDim());
            rel.setBizKind(apply.getBizKind());
            rel.setAccountNo(apply.getAccountNo());
            rel.setEmpId(it.getEmpId());
            // 姓名/部门：优先 UserApi 解析（工号→姓名/机构），解析不到回退调整明细快照
            rel.setFullname(firstNonBlank(u == null ? null : u.getDisplayName(), it.getEmpChnName()));
            rel.setDeptNo(firstNonBlank(u == null ? null : u.getMainOrgCode(), it.getOrgCode()));
            rel.setDeptName(firstNonBlank(u == null ? null : u.getMainOrgName(), it.getOrgName()));
            rel.setRatio(it.getRatio());
            // 新分配默认 is_original=2（当前生效，非原分配）
            rel.setIsOriginal(IS_ORIGINAL_NO);
            rel.setEffectiveDate(effectiveDate);
            rel.setEndDate(null);
            // 关联调整申请作为追溯标记
            rel.setSourceBatchId(apply.getApplyNo());
            rel.setSourceProcessDate(effectiveDate);
            rel.setCreatedBy(apply.getCreatedBy());
            rel.setUpdatedBy(apply.getCreatedBy());
            allocRelationMapper.insert(rel);
        }

        // 更新主表状态，processInstanceId 传 null 避免覆写历史值
        applyMapper.updateStatus(apply.getId(), "APPROVED", null);

        // 发布领域事件（afterCommit 后异步投递）
        AllocationAdjustmentApprovedEvent event = new AllocationAdjustmentApprovedEvent(
                MDC.get("traceId"),
                apply.getId(),
                apply.getCustId(),
                apply.getAllocDim(),
                apply.getBizKind(),
                items.size(),
                apply.getCreatedBy());
        eventPublisher.publish(event);

        log.info("[AllocAdjustCompletedListener] APPROVED applyId={}, itemCount={}",
                apply.getId(), items.size());

        notifyApplicant(apply, "通过", "您的业绩分配调整申请已通过，调整已生效。");
    }

    /**
     * 按调整明细的工号批量解析用户（工号→姓名/机构），键为 empId 与 username 双登记，
     * 供插入分配关系时回填 fullname/dept_no/dept_name。UserApi 异常不阻断审批落地（返回已解析部分）。
     */
    private java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> resolveUserMap(
            List<PerfAllocAdjustItem> items) {
        java.util.Map<String, com.bank.branch.platform.auth.api.dto.UserDTO> map = new java.util.HashMap<>();
        java.util.LinkedHashSet<String> empIds = new java.util.LinkedHashSet<>();
        for (PerfAllocAdjustItem it : items) {
            if (it.getEmpId() != null && !it.getEmpId().isBlank()) {
                empIds.add(it.getEmpId());
            }
        }
        if (empIds.isEmpty()) {
            return map;
        }
        try {
            List<com.bank.branch.platform.auth.api.dto.UserDTO> byId =
                    userApi.getUserByEmpIds(new java.util.ArrayList<>(empIds));
            if (byId != null) {
                for (com.bank.branch.platform.auth.api.dto.UserDTO u : byId) {
                    if (u != null && u.getEmpId() != null) {
                        map.put(u.getEmpId(), u);
                    }
                }
            }
            // 工号若是登录名(username) 未命中 empId，回退按 username 解析
            List<String> remaining = new java.util.ArrayList<>();
            for (String token : empIds) {
                if (!map.containsKey(token)) {
                    remaining.add(token);
                }
            }
            if (!remaining.isEmpty()) {
                List<com.bank.branch.platform.auth.api.dto.UserDTO> byName =
                        userApi.getUsersByUsernames(remaining);
                if (byName != null) {
                    for (com.bank.branch.platform.auth.api.dto.UserDTO u : byName) {
                        if (u != null && u.getUsername() != null) {
                            map.putIfAbsent(u.getUsername(), u);
                        }
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("[AllocAdjustCompletedListener] 工号解析姓名/部门失败，回退明细快照: {}", ex.getMessage());
        }
        return map;
    }

    /** 取首个非空白字符串. */
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    /**
     * 审批拒绝：仅更新状态.
     */
    private void handleRejected(PerfAllocAdjustApply apply) {
        applyMapper.updateStatus(apply.getId(), "REJECTED", null);
        log.info("[AllocAdjustCompletedListener] REJECTED applyId={}", apply.getId());

        notifyApplicant(apply, "驳回", "您的业绩分配调整申请已被驳回，分配关系保持不变。");
    }

    /**
     * 审批结束后给申请人发通知（失败不阻断主流程）.
     */
    private void notifyApplicant(PerfAllocAdjustApply apply, String result, String content) {
        try {
            notifyApi.sendNotification(com.bank.branch.platform.governance.api.dto.NotificationCmd.builder()
                    .targetEmpId(apply.getCreatedBy())
                    .title("业绩分配调整审批" + result)
                    .content(content + "（申请编号：" + apply.getBusinessKey() + "）")
                    .notifyType("WORKFLOW")
                    .bizType("ALLOC_ADJUST")
                    .bizId(apply.getId())
                    .build());
        } catch (Exception e) {
            log.warn("[AllocAdjustCompletedListener] 发送通知失败 applyId={}, err={}",
                    apply.getId(), e.getMessage());
        }
    }
}
