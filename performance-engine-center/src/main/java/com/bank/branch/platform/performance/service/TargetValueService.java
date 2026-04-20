package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 目标值服务骨架 (TDD 红阶段, 实现推迟到绿阶段).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetValueService {

    private final PerfTargetValueMapper targetValueMapper;

    /**
     * 批量 upsert 骨架.
     *
     * @param list     目标值列表
     * @param operator 操作人
     * @return 受影响行数
     */
    public int upsertBatch(List<PerfTargetValue> list, String operator) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /**
     * 单值 upsert 骨架.
     *
     * @param cmd 单值命令
     * @return 受影响行数
     */
    public int upsertOne(UpsertTargetValueCmd cmd) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /**
     * 按 UK 查询单条目标值.
     *
     * @param planId      方案ID
     * @param subjectType 对象类型
     * @param subjectId   对象ID
     * @param cycleKey    周期键
     * @param metricCode  指标编码
     * @return Optional 包装的目标值
     */
    public Optional<PerfTargetValue> getByUniqueKey(String planId, String subjectType, String subjectId,
                                                    String cycleKey, String metricCode) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }

    /**
     * 按方案分页查询目标值.
     *
     * @param planId      方案ID (必填)
     * @param subjectType 对象类型 (可空)
     * @param subjectId   对象ID (可空)
     * @param cycleKey    周期键 (可空)
     * @param pageNo      页码 (从 1 起)
     * @param pageSize    页大小
     * @return 分页结果
     */
    public PageResult<PerfTargetValue> listByPlan(String planId, String subjectType, String subjectId,
                                                  String cycleKey, int pageNo, int pageSize) {
        throw new UnsupportedOperationException("Task 3.2 绿阶段实现");
    }
}
