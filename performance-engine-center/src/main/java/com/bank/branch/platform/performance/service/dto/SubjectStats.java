package com.bank.branch.platform.performance.service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主体明细统计（V1.7）.
 *
 * <p>由 MetricCalcService 在 EXPR/GROOVY 多主体计算后产出，
 * 透传到 perf_run_task.params_json 与 MetricCalcCompletedEvent.
 *
 * <p>V1.7 升级：新增 jobKey（任务键，格式 PERF_METRIC_{metricCode}）与
 * triggerType（触发类型：SCHEDULED/MANUAL/RECALC）字段，
 * 与 spec § 4.6 params_json 契约对齐。
 */
@Slf4j
public record SubjectStats(
    int total,
    int success,
    int failed,
    List<String> failedSamples,
    String jobKey,       // V1.7：透传到 perf_run_task.params_json
    String triggerType   // V1.7：SCHEDULED/MANUAL/RECALC
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 空统计（主体集合为空时使用）. */
    public static SubjectStats empty(String jobKey, String triggerType) {
        return new SubjectStats(0, 0, 0, List.of(), jobKey, triggerType);
    }

    /** 全部成功统计（SQL 类型指标使用）. */
    public static SubjectStats allSuccess(int total, String jobKey, String triggerType) {
        return new SubjectStats(total, total, 0, List.of(), jobKey, triggerType);
    }

    /**
     * 序列化成 perf_run_task.params_json 片段.
     *
     * <p>包含 jobKey / triggerType / subjectTotal / subjectSuccess / subjectFailed / failedSamples，
     * 与 spec § 4.6 对齐。
     */
    public String toJson() {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("jobKey", jobKey);
            map.put("triggerType", triggerType);
            map.put("subjectTotal", total);
            map.put("subjectSuccess", success);
            map.put("subjectFailed", failed);
            map.put("failedSamples", failedSamples);
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[SubjectStats.toJson] 序列化失败", e);
            return "{}";
        }
    }
}
