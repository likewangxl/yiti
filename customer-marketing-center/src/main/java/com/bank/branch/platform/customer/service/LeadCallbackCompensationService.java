package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 孤儿 lead 数据补偿 Service。
 * <p>
 * <strong>背景（FU-14）</strong>：Phase 2.5 FU-2 已为
 * {@code WorkflowCallbackListener.onProcessCompleted} 加了整体 try-catch
 * 兜底（避免冒泡到 Spring AFTER_COMMIT 链触发框架级 ERROR 日志）；副作用是
 * listener 异常被自吞后，{@code cust_lead.lead_status} 可能停留在 IN_APPROVAL，
 * 但 Flowable 流程实际已 COMPLETED，形成孤儿数据。
 * </p>
 * <p>
 * <strong>本 Service 职责</strong>：以 5 分钟为粒度（{@code fixedDelay} 可配）
 * 巡检 stuck IN_APPROVAL leads（超过 stuckThresholdMinutes 未推进），
 * 通过 {@link WorkflowApi} 反查流程真实状态：
 * <ul>
 *   <li>流程仍 RUNNING / 流程不存在 → 跳过；</li>
 *   <li>流程已 COMPLETED 且变量 {@code approved=true} → 调
 *       {@link LeadCallbackReconcileService#reconcileApproved}；</li>
 *   <li>流程已 COMPLETED 且变量 {@code approved=false} → 调
 *       {@link LeadCallbackReconcileService#reconcileRejected}（reason
 *       占位 "由补偿任务推进，原因不明"）。</li>
 * </ul>
 * </p>
 * <p>
 * <strong>幂等</strong>：补偿路径调用的两个 reconcile 方法内部使用
 * {@code conditionalUpdateStatus} 实现 CAS-like 推进，与 listener 主路径竞态
 * 无害（先到者抢到状态推进权 + 事件发布权）。
 * </p>
 * <p>
 * <strong>红 commit skeleton</strong>：{@link #scanAndCompensate()} 抛
 * {@link UnsupportedOperationException}，让 LeadCallbackCompensationIT 在红
 * 阶段 fail；绿 commit 中替换为完整实现。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadCallbackCompensationService {

    private final CustLeadMapper leadMapper;
    private final WorkflowApi workflowApi;
    private final LeadCallbackReconcileService reconcileService;

    /**
     * stuck 阈值（分钟）。lead.updated_time < now - stuckThresholdMinutes
     * 视为 stuck 候选。默认 10 分钟，足以覆盖正常 listener AFTER_COMMIT 链路
     * 的最坏耗时（含 Redis 缓存重试 + 网络抖动）。
     */
    @Value("${customer.lead-compensation.stuck-threshold-minutes:10}")
    private int stuckThresholdMinutes;

    /**
     * 单次扫描的 batch 上限。生产环境上 IN_APPROVAL 量级有限（小时级 < 100），
     * 保守取 100 防长事务；如发现 batch 频繁打满，应优先排查 listener 异常根因
     * 而非加大 batch。
     */
    @Value("${customer.lead-compensation.batch-size:100}")
    private int batchSize;

    /**
     * 巡检入口（同步，可被 IT 直接调用）。
     * <p>
     * 流程：
     * <ol>
     *   <li>计算 cutoff = now - stuckThresholdMinutes</li>
     *   <li>查 stuck IN_APPROVAL leads（按 updated_time asc，limit batchSize）</li>
     *   <li>遍历每条：
     *     <ul>
     *       <li>调 {@code workflowApi.getProcessByBizTypeAndBizId("LEAD", leadId)}
     *           拿 BizProcessMapDTO；流程不存在或异常 → log.warn 跳过；</li>
     *       <li>processStatus != COMPLETED → log.debug 跳过（流程仍在跑）；</li>
     *       <li>processStatus == COMPLETED → 调 {@code workflowApi.getProcessOutcome}：
     *         <ul>
     *           <li>"APPROVED" → reconcileApproved；</li>
     *           <li>"REJECTED" → reconcileRejected；</li>
     *           <li>empty / 未知 → log.warn 跳过。</li>
     *         </ul>
     *       </li>
     *     </ul>
     *   </li>
     * </ol>
     * </p>
     */
    public void scanAndCompensate() {
        throw new UnsupportedOperationException("FU-14 scanAndCompensate not implemented yet");
    }

    /**
     * Spring 调度入口 —— 默认每 5 分钟（{@code 300_000ms}）触发一次
     * {@link #scanAndCompensate()}。
     * <p>
     * 整体 try-catch 防止补偿异常冒泡到调度器导致后续触发被禁用 —— Spring
     * {@code @Scheduled} 默认会因连续异常停止调度该 bean 的方法。
     * </p>
     */
    @Scheduled(fixedDelayString = "${customer.lead-compensation.interval-ms:300000}")
    public void scheduledScan() {
        try {
            scanAndCompensate();
        } catch (Exception e) {
            log.error("[LeadCallbackCompensationService.scheduledScan] 补偿任务执行异常", e);
        }
    }
}
