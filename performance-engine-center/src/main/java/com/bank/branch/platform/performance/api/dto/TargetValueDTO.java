package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 目标值 DTO.
 * <p>v1.2: id / planId 统一 String.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetValueDTO {
    /** 目标值 ID (varchar 32). */
    private String id;
    /** 目标方案 ID (varchar 32). */
    private String planId;
    /** 对象类型: EMP/ORG. */
    private String subjectType;
    /** 对象 ID (emp_id / org_code). */
    private String subjectId;
    /**
     * 对象名称：EMP 为员工姓名（按 subjectId=工号 反查 PT_USER.username 得 USERCHNNAME），
     * ORG 不解析、恒为 null（机构名由前端机构树映射承担）。
     * <p>
     * 2026-07-21 新增。此前前端 TargetValues.vue 自行调管理员接口 {@code /api/admin/users}
     * 拉全量用户建「工号→姓名」映射，而该接口资源 A_USER_LIST 只授予少数角色，
     * 资财部经办人等角色进页面即 403「没有权限」。姓名解析下沉后端后前端不再需要该调用。
     * </p>
     * <p>查不到对应用户时留空（不塞工号冒充姓名），由前端兜底显示工号。</p>
     */
    private String subjectName;
    /** 周期键: 2026 / 2026Q1 / 202604. */
    private String cycleKey;
    private String metricCode;
    private BigDecimal targetValue;
    private BigDecimal baseValue;
    /** 阶段名称（2026-06-17 新增）. */
    private String stageName;
    /** 起始日期（2026-06-17 新增）. */
    private LocalDate startDate;
    /** 截止日期（2026-06-17 新增）. */
    private LocalDate endDate;
}
