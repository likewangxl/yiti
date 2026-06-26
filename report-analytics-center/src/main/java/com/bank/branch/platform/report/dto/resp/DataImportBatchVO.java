package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/**
 * 数据导入查询 - 批次列表行视图（来自 amas_dt_import_sup 按批次聚合）。
 */
@Data
public class DataImportBatchVO {

    /** 数据批次号. */
    private String batchNum;
    /** 数据名称. */
    private String dataName;
    /** 创建时间. */
    private String createTime;
    /** 创建人工号. */
    private String createUsername;
    /** 创建人姓名. */
    private String createFullname;
    /** 说明. */
    private String dtExplain;
    /** 列数（该批次表头列数）. */
    private Integer columnCount;
}
