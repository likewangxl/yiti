package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入批次详情响应 DTO（GET /api/leads/import/batches/{batchId}）
 *
 * <p>字段名严格对齐 LeadImportBatch entity 真实字段（无 successCount，
 * 实际 status 而非 batchStatus，totalRowCount/errorRowCount 而非 totalCount/failCount）。</p>
 */
@Data
public class LeadImportBatchDetailRespDTO {

    private String id;
    private String batchNo;
    private String sourceFileName;
    private String fileMd5;

    /** 批次状态：CREATED/PROCESSING/COMPLETED/FAILED 等（取自 entity.status） */
    private String status;

    private Integer totalRowCount;
    private Integer errorRowCount;
    private String errorSummary;

    /** 错误明细 Excel 文件 MinIO objectId */
    private String errorFileObjectId;

    /** 流程业务键（LEAD:IMP_{batchId} 格式） */
    private String businessKey;

    /** Flowable 流程实例 ID（如已发起审批） */
    private String processInstanceId;

    private String ownerOrgId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
