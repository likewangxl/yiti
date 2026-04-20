package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 新建目标方案命令.
 *
 * <p>配合 {@code TargetPlanService.create} 使用: INSERT perf_target_plan 单表,
 * 目标值通过 {@code TargetValueService.upsertBatch} 异步提交 (Task 3.4 Controller 层)。
 *
 * <p>**DDL 现状说明** (Task 3.2 选项 A): 当前 DDL 只有 {@code effective_date} 列,
 * 没有 {@code expire_date}。Plan 文档 L1366-1367 钦定的
 * {@code effectiveAfterExpire}/{@code effectiveEqualsExpire} 场景实际退化为
 * {@code effectiveDate != null} 的单字段非空校验。expire_date (业务周期上限)
 * 列入 V1.1 DDL 规划, 由架构师统一处理; 本 Task 不引入 expireDate 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTargetPlanCmd {

    /** 方案编码 (唯一). */
    private String planCode;

    /** 方案名称. */
    private String planName;

    /** 关联 KPI 方案ID (必填, Service 层校验对应 scheme.status=ACTIVE). */
    private String kpiSchemeId;

    /** 目标维度: EMP/ORG. */
    private String targetDim;

    /** 目标周期: YEAR/QUARTER. */
    private String targetCycle;

    /** 生效日期 (必填). */
    private LocalDate effectiveDate;

    /** 操作人. */
    private String operator;
}
