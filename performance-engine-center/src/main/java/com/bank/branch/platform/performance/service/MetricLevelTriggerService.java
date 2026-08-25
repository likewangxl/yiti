package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.performance.controller.dto.MetricLevelTriggerReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 指标级别手动重算提交服务。
 *
 * <p>该服务只负责级别白名单映射和通过治理 {@link JobApi} 提交 Quartz 触发，不在请求线程执行指标计算。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricLevelTriggerService {

    private static final Map<Integer, String> LEVEL_JOB_KEYS = Map.of(
            1, "LEVEL1_METRIC_CALC",
            2, "LEVEL2_METRIC_CALC",
            3, "LEVEL3_METRIC_CALC");

    private final JobApi jobApi;

    /**
     * 提交页面发起的手动级别重算。
     *
     * @param req 请求参数
     * @param operatorEmpId 当前登录人工号
     * @return 治理中心返回的 Quartz 触发结果
     */
    public JobTriggerRespDTO trigger(MetricLevelTriggerReqDTO req, String operatorEmpId) {
        if (req == null) {
            throw new IllegalArgumentException("重算请求不能为空");
        }
        return trigger(req.getLevel(), req.getDataDate(), req.getReason(), req.getAllocDate(),
                "MANUAL", operatorEmpId);
    }

    /**
     * 按级别提交 Quartz 任务，供手动入口和数据就绪协调器共享固定任务白名单。
     *
     * @param level 指标级别 1/2/3
     * @param dataDate 数据日期
     * @param reason 触发原因，可由自动协调器为空
     * @param allocDate 业绩分配日期，仅 1 级允许
     * @param triggerType MANUAL/AUTO
     * @param operatorEmpId 操作人工号，自动触发可为空
     * @return 治理中心返回的 Quartz 触发结果
     */
    public JobTriggerRespDTO trigger(Integer level, LocalDate dataDate, String reason, LocalDate allocDate,
                                     String triggerType, String operatorEmpId) {
        String jobKey = resolveJobKey(level);
        if (dataDate == null) {
            throw new IllegalArgumentException("dataDate 不能为空");
        }
        if (level != 1 && allocDate != null) {
            throw new IllegalArgumentException("allocDate 仅 1 级指标允许提供");
        }
        log.info("[MetricLevelTriggerService] submit jobKey={}, level={}, dataDate={}, allocDate={}, triggerType={}",
                jobKey, level, dataDate, allocDate, triggerType);
        return jobApi.triggerJobByKey(jobKey, triggerType, reason, dataDate.toString(),
                allocDate == null ? null : allocDate.toString(), operatorEmpId);
    }

    /**
     * 解析受限级别白名单，禁止外部传入任意治理 jobKey。
     */
    public String resolveJobKey(Integer level) {
        String jobKey = LEVEL_JOB_KEYS.get(level);
        if (jobKey == null) {
            throw new IllegalArgumentException("指标级别必须为 1/2/3，当前值: " + level);
        }
        return jobKey;
    }
}
