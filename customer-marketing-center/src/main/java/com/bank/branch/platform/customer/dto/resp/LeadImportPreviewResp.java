package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

/**
 * 线索导入预览响应 DTO。
 * <p>
 * 返回导入文件的解析结果，供前端展示预览信息后确认执行导入。
 * 不入库，仅创建批次记录。
 * </p>
 */
@Data
public class LeadImportPreviewResp {

    /** 批次ID，用于后续执行导入 */
    private String batchId;

    /** 批次号（对外展示） */
    private String batchNo;

    /** 总数据行数（不含表头） */
    private int totalRows;

    /** 错误行数 */
    private int errorRows;

    /** 错误摘要（JSON格式，描述错误类型和样例） */
    private String errorSummary;
}
