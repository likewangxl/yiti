package com.bank.branch.platform.report.dto.req;

import lombok.Data;

/**
 * 数据导入查询 - 批次列表条件（页面上部查询项，全部可选）。列表按创建时间倒序。
 */
@Data
public class DataImportQueryReqDTO {

    /** 数据批次号（模糊匹配）. */
    private String batchNum;

    /** 数据名称（模糊匹配）. */
    private String dataName;

    /** 创建人工号（精确匹配）. */
    private String createUsername;

    /** 创建时间起（含）. */
    private String createTimeStart;

    /** 创建时间止（含）. */
    private String createTimeEnd;
}
