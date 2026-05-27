package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.EvalTaskSummaryDto;

import java.util.List;

/**
 * 评价查询对外 API（供 report-analytics-center 未来引用）.
 * <p>只读接口，禁止被业务模块写操作依赖。
 * 实现类：{@code com.bank.branch.platform.performance.eval.facade.EvalQueryFacade}
 */
public interface EvalQueryApi {

    /**
     * 查询所有评价任务概要.
     *
     * @return 任务概要列表（含 taskId、taskName、status、endTime、targetCount）
     */
    List<EvalTaskSummaryDto> listTaskSummaries();
}
