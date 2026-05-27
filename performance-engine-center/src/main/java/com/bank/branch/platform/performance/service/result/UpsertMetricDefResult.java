package com.bank.branch.platform.performance.service.result;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * V1.11：单条指标 upsert 结果.
 *
 * <p>{@code inserted=true} 表示新增路径（DB 无 {@code metric_name} 命中），
 * {@code false} 表示更新路径（命中既有 {@code metric_name}，{@code id} / {@code metric_code} 保留 DB 原值）.
 */
@Data
@AllArgsConstructor
public class UpsertMetricDefResult {

    /** 是否新增（true=新增，false=更新）. */
    private final boolean inserted;

    /** 命令处理后的指标定义（新增路径含新生成 id；更新路径含 DB 原 id+metric_code）. */
    private final PerfMetricDef def;
}
