package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

import java.util.List;

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

    /**
     * 校验通过的行数。
     * <p>
     * TODO: 行级校验逻辑将在后续补齐，当前简化实现下等于 totalRows。
     * </p>
     */
    private int successCount;

    /**
     * 校验失败的行数。
     * <p>
     * TODO: 行级校验逻辑将在后续补齐，当前简化实现下固定为 0。
     * </p>
     */
    private int failCount;

    /**
     * 错误样本（最多 10 条），供前端预览具体错误信息。
     * <p>
     * TODO: 行级校验逻辑将在后续补齐，当前简化实现下为空列表。
     * </p>
     */
    private List<LeadImportErrorVO> errorSamples;

    /**
     * 单行导入错误信息 VO。
     */
    @Data
    public static class LeadImportErrorVO {
        /** 行号（从 1 开始，不含表头） */
        private Integer rowIndex;
        /** 字段名 */
        private String field;
        /** 错误描述 */
        private String message;
    }
}
