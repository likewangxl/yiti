package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据导入查询 - 某批次的透视数据视图。
 *
 * <p>{@code columns} 为动态表头（来自 sup，按 DT_TITLE_SNO 排序，{@code [{key:列编号, label:列名}]}）；
 * {@code rows} 为透视后的数据行（details 按 DT_FLAG 分组，每行 {@code {列编号: 单元格值}}）。</p>
 */
@Data
public class DataImportDataVO {

    /** 数据批次号. */
    private String batchNum;
    /** 数据名称. */
    private String dataName;
    /** 动态表头：[{key, label}]. */
    private List<Map<String, String>> columns;
    /** 数据行（当前页）：[{列编号: 值}]. */
    private List<Map<String, Object>> rows;
    /** 总行数（按 DT_FLAG 去重，供前端分页）. */
    private Long total;
}
