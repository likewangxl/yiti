package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 绩效任务执行日志服务（V1.0 只读）.
 *
 * <p>骨架阶段（红）：所有方法抛 {@link UnsupportedOperationException}，
 * UT 先通过 mock 验证接口契约与依赖注入。绿阶段再落实实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfRunTaskService {

    private final PerfRunTaskMapper runTaskMapper;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;

    /** 按主键查询. */
    public Optional<PerfRunTask> getById(String id) {
        throw new UnsupportedOperationException("red phase");
    }

    /** 按任务编号（task_key）查询. */
    public Optional<PerfRunTask> getByTaskNo(String taskNo) {
        throw new UnsupportedOperationException("red phase");
    }

    /** 条件分页（含数据范围过滤）. */
    public PageResult<PerfRunTask> page(String taskType, String taskKey, String status,
                                        LocalDate dataDate, int pageNo, int pageSize) {
        throw new UnsupportedOperationException("red phase");
    }

    /** 按类型 + 日期统计. */
    public long countByTypeAndDate(String taskType, LocalDate dataDate) {
        throw new UnsupportedOperationException("red phase");
    }
}
