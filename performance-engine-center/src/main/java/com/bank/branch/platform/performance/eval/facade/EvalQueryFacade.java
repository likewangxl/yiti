package com.bank.branch.platform.performance.eval.facade;

import com.bank.branch.platform.performance.api.EvalQueryApi;
import com.bank.branch.platform.performance.api.dto.EvalTaskSummaryDto;
import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskTargetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 评价查询对外 API 实现.
 * <p>实现 {@link EvalQueryApi}，汇总评价任务列表及各任务的被评价人数量，
 * 供 report-analytics-center 等只读消费方调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalQueryFacade implements EvalQueryApi {

    private final EvalTaskMapper evalTaskMapper;
    private final EvalTaskTargetMapper evalTaskTargetMapper;

    /**
     * 查询所有评价任务概要（不分页，全量）.
     *
     * <p>对每条任务查询其 target 数量（{@link EvalTaskTargetMapper#selectByTaskId}），
     * 组装为 {@link EvalTaskSummaryDto} 列表返回。
     *
     * @return 任务概要列表
     */
    @Override
    public List<EvalTaskSummaryDto> listTaskSummaries() {
        log.debug("[EvalQueryFacade.listTaskSummaries] 查询全量评价任务概要");
        // 全量查询（无状态过滤），不分页
        List<EvalTask> tasks = evalTaskMapper.selectByCondition(null, null, 0, Integer.MAX_VALUE);
        return tasks.stream()
                .map(task -> {
                    int targetCount = evalTaskTargetMapper.selectByTaskId(task.getTaskId()).size();
                    return EvalTaskSummaryDto.builder()
                            .taskId(task.getTaskId())
                            .taskName(task.getTaskName())
                            .status(task.getStatus())
                            .endTime(task.getEndTime())
                            .targetCount(targetCount)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
