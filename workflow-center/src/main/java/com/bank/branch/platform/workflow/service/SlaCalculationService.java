package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.governance.api.CalendarApi;
import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import com.bank.branch.platform.workflow.enums.SlaStatus;
import com.bank.branch.platform.workflow.mapper.TimeoutRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SLA（服务等级协议）状态计算服务。
 * <p>
 * 根据流程节点的超时规则和已耗工作时间，计算当前任务的红绿灯状态：
 * GREEN（正常）、YELLOW（预警）、RED（超时）。
 * V1 版本使用整工作日 * 8小时 的简化算法。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaCalculationService {

    /** 每个工作日的标准工时（小时） */
    private static final int HOURS_PER_WORKING_DAY = 8;

    private final TimeoutRuleMapper timeoutRuleMapper;
    private final CalendarApi calendarApi;

    /**
     * 计算指定流程节点任务的 SLA 状态。
     *
     * @param processDefinitionKey 流程定义KEY
     * @param nodeKey              节点KEY
     * @param taskCreateTime       任务创建时间
     * @return SLA 状态：GREEN / YELLOW / RED
     */
    public SlaStatus calculateSlaStatus(String processDefinitionKey, String nodeKey,
                                        LocalDateTime taskCreateTime) {
        // 1. 查询超时规则
        WfTimeoutRule rule = timeoutRuleMapper.selectByProcessDefKeyAndNodeKey(
                processDefinitionKey, nodeKey);

        // 2. 无规则配置时默认绿灯（安全状态）
        if (rule == null) {
            log.debug("流程[{}]节点[{}]未配置超时规则，返回GREEN",
                    processDefinitionKey, nodeKey);
            return SlaStatus.GREEN;
        }

        // 3. 计算已耗工作小时数（V1：整工作日 * 8小时）
        int workingDays = calendarApi.countWorkingDays(
                taskCreateTime.toLocalDate(), LocalDate.now());
        int elapsedHours = workingDays * HOURS_PER_WORKING_DAY;

        log.debug("流程[{}]节点[{}] 工作日={}, 已耗工时={}h, 预警阈值={}h, 超时阈值={}h",
                processDefinitionKey, nodeKey, workingDays, elapsedHours,
                rule.getWarningHours(), rule.getTimeoutHours());

        // 4. 与阈值比较，判定红绿灯状态
        if (elapsedHours >= rule.getTimeoutHours()) {
            return SlaStatus.RED;
        } else if (elapsedHours >= rule.getWarningHours()) {
            return SlaStatus.YELLOW;
        } else {
            return SlaStatus.GREEN;
        }
    }
}
