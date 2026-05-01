package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
 * <strong>本 Service 职责</strong>：由 Quartz job
 * {@code LeadCallbackCompensateQuartzJob}（V1.8 起，
 * job_key={@code LEAD_CALLBACK_COMPENSATE}，cron 默认 5 分钟）
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadCallbackCompensationService {

    /**
     * 补偿任务给 reconcileRejected 填的占位 reason —— 真实 reason 已丢失（listener 异常吞掉）。
     *
     * <p><strong>FU-20（2026-04-29）评估保留</strong>：reviewer 建议挪到 i18n 或 enum。
     * 决定保留为本类 private 常量，不挪到全局：
     * <ul>
     *   <li>项目当前无 i18n 基础设施（中文项目，无 .properties resource bundle）；</li>
     *   <li>该字面量仅在补偿场景的 Spring 事件 reason 字段使用，作用域窄，不需要全局共享；</li>
     *   <li>挪到 enum 不语义匹配（这不是状态码而是一段补偿场景的占位文案）；</li>
     *   <li>未来若产品要求多语言或运维定制 reason 文案，再升级到 governance 字典 / i18n。</li>
     * </ul>
     * </p>
     */
    private static final String COMPENSATION_REJECT_REASON = "由补偿任务推进，原因不明";

    /**
     * Flowable 流程已结束的两个终态码（与 {@code workflow-center} 的
     * {@code ProcessStatus} 枚举常量保持一致，但为避免跨模块依赖内部枚举，
     * 此处以 String 字面量声明）。
     */
    private static final String PROCESS_STATUS_COMPLETED = "COMPLETED";
    private static final String PROCESS_STATUS_CANCELLED = "CANCELLED";

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
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(stuckThresholdMinutes);
        List<CustLead> stuckLeads = leadMapper.selectStuckInApproval(cutoff, batchSize);

        if (stuckLeads.isEmpty()) {
            log.debug("[LeadCallbackCompensationService.scanAndCompensate] 无 stuck IN_APPROVAL 线索，跳过");
            return;
        }

        log.info("[LeadCallbackCompensationService.scanAndCompensate] 发现 {} 条 stuck IN_APPROVAL 线索，开始补偿巡检",
                stuckLeads.size());

        int approvedCount = 0;
        int rejectedCount = 0;
        int skippedCount = 0;

        for (CustLead lead : stuckLeads) {
            String leadId = lead.getId();
            try {
                // 1. 反查流程真实状态
                BizProcessMapDTO processMap;
                try {
                    processMap = workflowApi.getProcessByBizTypeAndBizId("LEAD", leadId);
                } catch (Exception e) {
                    // BizException(WF-40402) 或其他异常 → 视作"流程不存在"
                    log.warn("[LeadCallbackCompensationService] 线索 {} 反查流程映射失败，跳过: {}",
                            leadId, e.getMessage());
                    skippedCount++;
                    continue;
                }

                if (processMap == null) {
                    log.warn("[LeadCallbackCompensationService] 线索 {} 无流程映射记录，跳过", leadId);
                    skippedCount++;
                    continue;
                }

                String processStatus = processMap.getProcessStatus();
                String processInstanceId = processMap.getProcessInstanceId();

                // 2. 流程仍在跑 → 跳过（让正常路径处理）
                if (!PROCESS_STATUS_COMPLETED.equals(processStatus)
                        && !PROCESS_STATUS_CANCELLED.equals(processStatus)) {
                    log.debug("[LeadCallbackCompensationService] 线索 {} 流程仍 {}，跳过", leadId, processStatus);
                    skippedCount++;
                    continue;
                }

                // 3. 流程已结束 → 反查 outcome
                Optional<String> outcomeOpt = workflowApi.getProcessOutcome(processInstanceId);
                if (outcomeOpt.isEmpty()) {
                    log.warn("[LeadCallbackCompensationService] 线索 {} 流程 {} 已结束但 outcome 未知，跳过",
                            leadId, processInstanceId);
                    skippedCount++;
                    continue;
                }

                // 4. 按 outcome 委托 reconcile
                String outcome = outcomeOpt.get();
                if ("APPROVED".equals(outcome)) {
                    reconcileService.reconcileApproved(lead, processInstanceId);
                    approvedCount++;
                } else if ("REJECTED".equals(outcome)) {
                    reconcileService.reconcileRejected(lead, processInstanceId, COMPENSATION_REJECT_REASON);
                    rejectedCount++;
                } else {
                    log.warn("[LeadCallbackCompensationService] 线索 {} outcome={} 非预期值，跳过", leadId, outcome);
                    skippedCount++;
                }
            } catch (Exception e) {
                // 单条线索异常不影响其它线索的补偿
                log.error("[LeadCallbackCompensationService] 线索 {} 补偿异常，跳过本条", leadId, e);
                skippedCount++;
            }
        }

        log.info("[LeadCallbackCompensationService.scanAndCompensate] 补偿巡检完成 total={} approved={} rejected={} skipped={}",
                stuckLeads.size(), approvedCount, rejectedCount, skippedCount);
    }

}
