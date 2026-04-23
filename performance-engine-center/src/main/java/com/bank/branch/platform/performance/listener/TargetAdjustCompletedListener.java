package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.performance.entity.PerfTargetAdjustApply;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.event.TargetAdjustmentApprovedEvent;
import com.bank.branch.platform.performance.mapper.PerfTargetAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 目标修正流程完成监听器 (V1.2 Q3.2b + Q3.3).
 *
 * <p>订阅 workflow-center 的
 * {@link ProcessCompletedListener.ProcessCompletedEvent}，
 * 按 businessKey 前缀 {@code TARGET_ADJUST:} 筛选本模块关心的目标修正流程。
 *
 * <p>APPROVED 动作：
 * <ol>
 *   <li>从 apply.remark（JSON）解析出 adjustments 列表（metricCode/newValue/oldValue）</li>
 *   <li>把 (planId, subjectType, subjectId, cycleKey, metricCode, newValue) 组装成
 *       {@link PerfTargetValue} 并调 {@code PerfTargetValueMapper.upsertBatch} 落地
 *       （复用 V1.0 已有的 upsert 能力，避免引入新 update 方法）</li>
 *   <li>updateStatus(APPROVED)（不覆写 processInstanceId，传 null）</li>
 *   <li>发布 {@link TargetAdjustmentApprovedEvent}（供目标缓存失效、报表快照重算、通知推送等下游消费）</li>
 * </ol>
 *
 * <p>REJECTED 动作：仅 updateStatus(REJECTED)，不改 perf_target_value，不发事件。
 *
 * <p>apply 不存在：幂等跳过（防止流程重投递重复处理）。
 *
 * <p>未识别 outcome（CANCELLED 等）：保守不改数据，打日志。
 *
 * <p>事务策略：@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)
 * 保证 workflow-center 事件事务提交后执行，同时兼容无事务场景；
 * 本 Listener 处理方法自身用 @Transactional(REQUIRES_NEW) 包裹，
 * 批量写入要么全部生效要么全部回滚。
 */
@Slf4j
@Component
public class TargetAdjustCompletedListener {

    /** 本模块关心的 businessKey 前缀. */
    public static final String BIZ_KEY_PREFIX = "TARGET_ADJUST:";

    /** workflow outcome 语义：审批通过. */
    private static final String OUTCOME_APPROVED = "APPROVED";

    /** workflow outcome 语义：审批拒绝. */
    private static final String OUTCOME_REJECTED = "REJECTED";

    private final PerfTargetAdjustApplyMapper applyMapper;
    private final PerfTargetValueMapper targetValueMapper;
    private final PerfEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public TargetAdjustCompletedListener(PerfTargetAdjustApplyMapper applyMapper,
                                         PerfTargetValueMapper targetValueMapper,
                                         PerfEventPublisher eventPublisher) {
        this.applyMapper = applyMapper;
        this.targetValueMapper = targetValueMapper;
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper();
    }

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
            // 非目标修正流程：交给其他监听器处理
            return;
        }

        log.info("[TargetAdjustCompletedListener] businessKey={}, outcome={}, pid={}",
                businessKey, event.outcome(), event.processInstanceId());

        PerfTargetAdjustApply apply = applyMapper.selectByBusinessKey(businessKey);
        if (apply == null) {
            // 幂等保护：流程重投递 / 测试环境脏数据 场景直接跳过
            log.warn("[TargetAdjustCompletedListener] apply 不存在，跳过 businessKey={}", businessKey);
            return;
        }

        if (OUTCOME_APPROVED.equals(event.outcome())) {
            handleApproved(apply);
        } else if (OUTCOME_REJECTED.equals(event.outcome())) {
            handleRejected(apply);
        } else {
            // 未识别的 outcome（CANCELLED 等）：保守不改数据，打日志
            log.warn("[TargetAdjustCompletedListener] 未识别 outcome={}，businessKey={}",
                    event.outcome(), businessKey);
        }
    }

    /**
     * 审批通过：解析 remark JSON → upsert perf_target_value → 更新状态 → 发事件.
     */
    private void handleApproved(PerfTargetAdjustApply apply) {
        List<Adjustment> adjustments = parseAdjustments(apply.getRemark());

        if (!adjustments.isEmpty()) {
            // 组装批量 upsert 入参
            List<PerfTargetValue> list = new ArrayList<>(adjustments.size());
            LocalDateTime now = LocalDateTime.now();
            String operator = apply.getCreatedBy();
            for (Adjustment a : adjustments) {
                PerfTargetValue tv = new PerfTargetValue();
                tv.setId(UUID.randomUUID().toString().replace("-", ""));
                tv.setPlanId(apply.getPlanId());
                tv.setSubjectType(apply.getSubjectType());
                tv.setSubjectId(apply.getSubjectId());
                tv.setCycleKey(apply.getCycleKey());
                tv.setMetricCode(a.metricCode);
                tv.setTargetValue(a.newValue);
                // V1.2 简化：base_value 不改动（adjustment 只含 newValue 语义）
                tv.setBaseValue(null);
                tv.setCreatedBy(operator);
                tv.setCreatedTime(now);
                tv.setUpdatedBy(operator);
                tv.setUpdatedTime(now);
                list.add(tv);
            }
            // 复用 V1.0 已有的 upsertBatch（ON DUPLICATE KEY UPDATE）能力
            targetValueMapper.upsertBatch(list);
        } else {
            log.warn("[TargetAdjustCompletedListener] apply.remark 解析为空 adjustments，applyId={}",
                    apply.getId());
        }

        // 更新主表状态，processInstanceId 传 null 避免覆写历史值
        applyMapper.updateStatus(apply.getId(), "APPROVED", null);

        // 发布领域事件（afterCommit 后异步投递）
        TargetAdjustmentApprovedEvent event = new TargetAdjustmentApprovedEvent(
                MDC.get("traceId"),
                apply.getId(),
                apply.getPlanId(),
                apply.getSubjectType(),
                apply.getSubjectId(),
                apply.getCycleKey(),
                // V1.2 简化：审批通过人 approvedBy 暂以申请人 created_by 作为占位，
                // 与 Q2 AllocAdjust 的处理保持一致；生产需从 workflow 事件附带的 taskOperator 读取
                apply.getCreatedBy());
        eventPublisher.publish(event);

        log.info("[TargetAdjustCompletedListener] APPROVED applyId={}, adjustmentCount={}",
                apply.getId(), adjustments.size());
    }

    /**
     * 审批拒绝：仅更新状态.
     */
    private void handleRejected(PerfTargetAdjustApply apply) {
        applyMapper.updateStatus(apply.getId(), "REJECTED", null);
        log.info("[TargetAdjustCompletedListener] REJECTED applyId={}", apply.getId());
    }

    /**
     * 解析 remark JSON 中的 adjustments 数组.
     *
     * <p>容错：
     * <ul>
     *   <li>remark 为空 → 返回空列表（记 warn 日志）</li>
     *   <li>JSON 结构异常 / 缺失 adjustments 字段 → 返回空列表（记 error 日志）</li>
     *   <li>adjustment 单条字段缺失 → 跳过该条</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<Adjustment> parseAdjustments(String remark) {
        if (remark == null || remark.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(
                    remark, new TypeReference<Map<String, Object>>() {
                    });
            Object raw = root.get("adjustments");
            if (!(raw instanceof List)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> items = (List<Map<String, Object>>) raw;
            List<Adjustment> result = new ArrayList<>(items.size());
            for (Map<String, Object> it : items) {
                Object mc = it.get("metricCode");
                Object nv = it.get("newValue");
                Object ov = it.get("oldValue");
                if (mc == null || nv == null) {
                    continue;
                }
                Adjustment a = new Adjustment();
                a.metricCode = String.valueOf(mc);
                a.newValue = toBigDecimal(nv);
                a.oldValue = toBigDecimal(ov);
                if (a.newValue != null) {
                    result.add(a);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[TargetAdjustCompletedListener] remark JSON 解析失败: {}", remark, e);
            return Collections.emptyList();
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return new BigDecimal(v.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 内部用的 adjustment 项（metricCode + newValue + oldValue）.
     */
    private static class Adjustment {
        String metricCode;
        BigDecimal newValue;
        BigDecimal oldValue;
    }
}
