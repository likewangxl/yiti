package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新 KPI 方案命令.
 *
 * <p>选择性 patch: 非空字段才更新 (Mapper {@code updateByIdSelective} 负责
 * {@code <if>} 分支渲染). 方案编码不可修改 (UK 约束不允许).
 *
 * <p>方案项的增删改走 {@code KpiItemService.addItem/updateItem/deleteItem},
 * 不在本 Cmd 内嵌。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateKpiSchemeCmd {

    /** 方案名称 (可空). */
    private String schemeName;

    /** 周期类型 (可空, 发布后是否允许修改由 Service 判断). */
    private String cycleType;

    /** 是否向员工开放明细 (可空). */
    private Integer openDetail;

    /** 操作人. */
    private String operator;
}
