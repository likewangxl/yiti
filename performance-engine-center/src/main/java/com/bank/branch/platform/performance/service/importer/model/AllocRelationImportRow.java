package com.bank.branch.platform.performance.service.importer.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分配关系 Excel 导入行模型（V1.1 Task P5.4）.
 *
 * <p>用于 cust_alloc_relation 导入。列与 DDL 字段一一对应：
 * <ul>
 *   <li>{@code custId} / {@code empId} 必填（核心维度）</li>
 *   <li>{@code orgCode} 可空，V1.0 cust_alloc_relation 无 org_code 列，保留字段用于前端模板兼容；本策略不回写</li>
 *   <li>{@code effectiveDate} 必填，yyyy-MM-dd</li>
 *   <li>{@code allocType} 对齐 {@code alloc_dim}：RULE / ACCOUNT，默认 RULE</li>
 *   <li>{@code bizKind} 可空；{@code accountNo} 当 allocType=ACCOUNT 时必填</li>
 *   <li>{@code ratio} 可空，默认 100.00（decimal(5,2)，业务层 0-100 值域）</li>
 * </ul>
 *
 * <p>重复行（DB 主键冲突或唯一约束冲突）→ 记录到 errorSummary，不整批回滚。
 */
@Data
@NoArgsConstructor
public class AllocRelationImportRow {

    /** 客户号. */
    @ExcelProperty("客户号")
    private String custId;

    /** 员工编号. */
    @ExcelProperty("员工编号")
    private String empId;

    /** 机构编码（V1.0 cust_alloc_relation 无此列，保留字段兼容模板）. */
    @ExcelProperty("机构编码")
    private String orgCode;

    /** 生效日期 yyyy-MM-dd. */
    @ExcelProperty("生效日期")
    private String effectiveDate;

    /** 分配类型：RULE / ACCOUNT. */
    @ExcelProperty("分配类型")
    private String allocType;

    /** 业务种类（可空）. */
    @ExcelProperty("业务种类")
    private String bizKind;

    /** 账号（allocType=ACCOUNT 时必填）. */
    @ExcelProperty("账号")
    private String accountNo;

    /** 分配比例 0-100（可空，默认 100.00）. */
    @ExcelProperty("分配比例")
    private BigDecimal ratio;
}
