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
 */
@Slf4j
public record SubjectStats(
    int total,
    int success,
    int failed,
    List<String> failedSamples
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 空统计（主体集合为空时使用）. */
    public static SubjectStats empty() {
        return new SubjectStats(0, 0, 0, List.of());
    }

    /** 全部成功统计（SQL 类型指标使用）. */
    public static SubjectStats allSuccess(int total) {
        return new SubjectStats(total, total, 0, List.of());
    }

    /** 序列化成 perf_run_task.params_json 片段（仅 subjectStats 部分）. */
    public String toJson() {
        try {
            Map<String, Object> map = new HashMap<>();
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
