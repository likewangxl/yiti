package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.service.cmd.CreateKpiSchemeCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateKpiSchemeCmd;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * KPI 方案服务 (骨架, 实现在 Step 4 填充).
 */
@Service
@RequiredArgsConstructor
public class KpiSchemeService {

    private final PerfKpiSchemeMapper schemeMapper;
    private final KpiItemService kpiItemService;
    private final MetricDefService metricDefService;

    /** 新建方案 (单事务 INSERT scheme + 遍历 items). */
    public PerfKpiScheme create(CreateKpiSchemeCmd cmd) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** 更新方案 (选择性 patch). */
    public PerfKpiScheme updateById(String id, UpdateKpiSchemeCmd cmd) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** 禁用方案 (高危, reason 必填). */
    public void disable(String id, String reason, String operator) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** 发布方案 (DRAFT/ACTIVE → ACTIVE, 校验所有 item 的 metric 可用). */
    public PerfKpiScheme publish(String id, String operator) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** 按主键查询, 不存在抛异常. */
    public PerfKpiScheme getById(String id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** 按主键查询, 不存在返回 Optional.empty. */
    public Optional<PerfKpiScheme> getByIdOrNull(String id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** 条件分页. */
    public PageResult<PerfKpiScheme> page(String cycleType, String status, String keyword, int pageNo, int pageSize) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
