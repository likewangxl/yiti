package com.bank.branch.platform.performance.service.importer.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 基础指标数据 Excel 导入行模型（V1.1 Task P5.3）.
 *
 * <p>用于一次性导入 emp_index_result / org_index_result / cust_index_result 三张宽表的
 * 原始值（对齐 D §BASE_DATA 通道）。
 *
 * <p>路由原则：
 * <ul>
 *   <li>subjectKey 字段语义由 metricCode 对应的 {@code perf_metric_def.base_dim} 决定</li>
 *   <li>baseDim=EMP → subjectKey 作 emp_id</li>
 *   <li>baseDim=ORG → subjectKey 作 org_code</li>
 *   <li>baseDim=CUST → subjectKey 作 cust_id</li>
 * </ul>
 *
 * <p>行级校验：subjectKey / metricCode / dataDate / value 必填；
 * metricCode 必须存在于 perf_metric_def 且已分配 val_slot；dataDate 可解析为 LocalDate。
 * 校验不通过的行累计到 errorSummary，不整批抛异常。
 */
@Data
@NoArgsConstructor
public class BaseDataImportRow {

    /** 维度键：empId / orgCode / custId（按 metric_def.base_dim 路由）. */
    @ExcelProperty("维度键")
    private String subjectKey;

    /** 指标编码. */
    @ExcelProperty("指标编码")
    private String metricCode;

    /** 数据日期 yyyy-MM-dd 字符串. */
    @ExcelProperty("数据日期")
    private String dataDate;

    /** 数据版本. */
    @ExcelProperty("版本")
    private String version;

    /** 数值. */
    @ExcelProperty("数值")
    private BigDecimal value;
}
