package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线索导入批次实体，对应 lead_import_batch 表。
 * <p>
 * 记录每次批量导入线索的批次信息，包含校验状态、错误统计和流程关联。
 * status 状态机：CREATED→PENDING_APPROVAL→APPROVED/REJECTED。
 * error_summary 存储 JSON 格式的错误类型统计与样例。
 * 该表无 deleted 字段，使用 status 管理生命周期。
 * </p>
 */
@Data
@TableName("lead_import_batch")
public class LeadImportBatch {

    /** 主键ID（UUID，32位去连字符），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 批次号（对外展示，唯一），对应 batch_no */
    private String batchNo;

    /** 源文件名，对应 source_file_name */
    private String sourceFileName;

    /** 文件MD5（用于去重校验），对应 file_md5 */
    private String fileMd5;

    /** 批次状态：CREATED/PENDING_APPROVAL/APPROVED/REJECTED，对应 status */
    private String status;

    /** 总行数，对应 total_row_count */
    private Integer totalRowCount;

    /** 错误行数，对应 error_row_count */
    private Integer errorRowCount;

    /** 错误摘要（JSON），对应 error_summary */
    private String errorSummary;

    /** 错误明细文件对象ID（MinIO），对应 error_file_object_id */
    private String errorFileObjectId;

    /** 流程业务键（格式 LEAD:IMP_{batchId}），对应 business_key */
    private String businessKey;

    /** 流程实例ID（Flowable），对应 process_instance_id */
    private String processInstanceId;

    /** 归属机构代码，对应 owner_org_id */
    private String ownerOrgId;

    /** 创建人（员工工号），对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 最后更新人，对应 updated_by */
    private String updatedBy;

    /** 最后更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
