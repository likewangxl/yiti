package com.bank.branch.platform.performance.service.result;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * V1.11：批量按名称 upsert 结果.
 *
 * <p>整批 {@code @Transactional} 模式：任一行抛异常整批回滚，本对象仅在事务成功
 * commit 后由调用方拿到。
 *
 * <p>{@code insertedRows + updatedRows == defs.size()}，
 * 顺序与入参 {@code List<CreateMetricDefCmd>} 同序。
 */
@Data
@AllArgsConstructor
public class BatchUpsertMetricDefResult {

    /** 新增行数. */
    private final int insertedRows;

    /** 更新行数. */
    private final int updatedRows;

    /** 处理完成的指标定义列表（与入参同序）. */
    private final List<PerfMetricDef> defs;
}
