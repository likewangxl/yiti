package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 新建 KPI 方案命令 (父子聚合写入).
 *
 * <p>Service 层单事务内先 INSERT scheme, 再遍历 {@link #items} 调用
 * {@code KpiItemService.addItem}, 任何一项失败整体回滚。
 *
 * <p>items 可为空 (先建空方案后续追加), 但非空时每项的 metricCode 在方案内不可重复。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateKpiSchemeCmd {

    /** 方案编码 (唯一, 业务层维持 UK). */
    private String schemeCode;

    /** 方案名称. */
    private String schemeName;

    /** 周期类型: MONTHLY / QUARTERLY. */
    private String cycleType;

    /** 是否向员工开放明细 (0=否, 1=是). */
    private Integer openDetail;

    /** 员工角色范围 (角色编码 CSV, 可空=不限定). */
    private String empRoleScope;

    /** 方案项列表 (可空, 表示先建空方案). */
    private List<AddKpiItemCmd> items;

    /** 操作人. */
    private String operator;
}
