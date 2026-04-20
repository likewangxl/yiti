package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.service.cmd.CreateTargetPlanCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateTargetPlanCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 目标方案服务骨架 (TDD 红阶段, 实现推迟到绿阶段).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetPlanService {

    private final PerfTargetPlanMapper targetPlanMapper;
    private final KpiSchemeService kpiSchemeService;
    private final CacheManager cacheManager;

    /** 新建目标方案. */
    public PerfTargetPlan create(CreateTargetPlanCmd cmd) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /** 按主键选择性更新. */
    public PerfTargetPlan updateById(String id, UpdateTargetPlanCmd cmd) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /** 禁用目标方案 (reason 必填). */
    public void disable(String id, String reason, String operator) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /** 按主键查询, 不存在抛 NotFound. */
    public PerfTargetPlan getById(String id) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /** 按主键查询, 不存在返回 Optional.empty (供 Facade 缓存友好使用). */
    public Optional<PerfTargetPlan> getByIdOrNull(String id) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /** 按方案编码查询, 不存在返回 Optional.empty. */
    public Optional<PerfTargetPlan> getByCodeOrNull(String planCode) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /** 条件分页查询. */
    public PageResult<PerfTargetPlan> page(String kpiSchemeId, String status, String keyword,
                                           int pageNo, int pageSize) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }
}
