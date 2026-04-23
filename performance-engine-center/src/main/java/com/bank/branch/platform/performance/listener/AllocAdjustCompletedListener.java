package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.event.AllocationAdjustmentApprovedEvent;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
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
 * {@link ProcessCompletedListener.ProcessCompletedEvent}，
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

    private final PerfAllocAdjustApplyMapper applyMapper;
    private final PerfAllocAdjustItemMapper itemMapper;
    private final CustAllocRelationMapper allocRelationMapper;
    private final PerfEventPublisher eventPublisher;

    /**
     * 监听流程完成事件入口.
     *
     * @param event workflow-center 发布的流程完成事件（含 outcome 语义）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onProcessCompleted(ProcessCompletedListener.ProcessCompletedEvent event) {
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
        List<PerfAllocAdjustItem> items = itemMapper.selectByApplyId(apply.getId());
        LocalDate effectiveDate = LocalDate.now();

        for (PerfAllocAdjustItem it : items) {
            CustAllocRelation rel = new CustAllocRelation();
            rel.setId(UUID.randomUUID().toString().replace("-", ""));
            rel.setCustId(apply.getCustId());
            rel.setAllocDim(apply.getAllocDim());
            rel.setBizKind(apply.getBizKind());
            rel.setAccountNo(apply.getAccountNo());
            rel.setEmpId(it.getEmpId());
            rel.setRatio(it.getRatio());
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
    }

    /**
     * 审批拒绝：仅更新状态.
     */
    private void handleRejected(PerfAllocAdjustApply apply) {
        applyMapper.updateStatus(apply.getId(), "REJECTED", null);
        log.info("[AllocAdjustCompletedListener] REJECTED applyId={}", apply.getId());
    }
}
