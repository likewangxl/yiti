package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量目标值 upsert 命令（V1.3 R4.1 引入）.
 *
 * <p>背景：V1.2 及之前 {@code TargetValueController.batch} 直接在 Controller 层
 * {@code new PerfTargetValue()} 构造 entity 列表再传入
 * {@code TargetValueService.upsertBatch(List<PerfTargetValue>, operator)}，导致
 * Controller 感知 entity（违反 {@code NoEntityInControllerLocalsArchTest}）。
 *
 * <p>V1.3 R4.1 做法：新增本 Cmd，Controller 只负责把 {@code UpsertTargetValueReqDTO}
 * 列表 + operator 装成 Cmd 交给 Service；Service 内部完成 entity 构造，彻底隔离
 * Controller 与 entity。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertTargetValueBatchCmd {

    /** 批量 upsert 项列表（非空 / 非空白由 Service 校验）. */
    private List<UpsertTargetValueCmd> items;

    /** 操作人 emp_id，用于强制覆盖 created_by（V1.3 I-2 安全契约继承自 V1.0）. */
    private String operator;
}
