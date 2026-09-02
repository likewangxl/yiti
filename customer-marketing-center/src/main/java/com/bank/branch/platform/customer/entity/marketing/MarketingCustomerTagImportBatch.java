package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** 营销客户标签追加/全量替换导入批次，对应 MARKETING_CUSTOMER_TAG_IMPORT_BATCH。 */
@Data
@TableName("MARKETING_CUSTOMER_TAG_IMPORT_BATCH")
public class MarketingCustomerTagImportBatch {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private Long tagId;
    private String tagNameSnapshot;
    private String importMode;
    private String sourceFileName;
    private String sourceFileId;
    private String fileChecksum;
    private Integer totalCount;
    private Integer validCount;
    private Integer errorCount;
    private Integer pendingApprovalCount;
    private Integer approvedCount;
    private Integer rejectedCount;
    private Integer loadedCount;
    private Integer tagApprovalRequired;
    private String customerApprovalStatus;
    private String status;
    private String replaceBlockReason;
    private String importEmpId;
    private String importOrgId;
    private LocalDateTime importTime;
    private LocalDateTime completedTime;
    private String recordStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    @Version
    private Integer lockVersion;

    /** 便于列表页直接展示标签名称，实际字段来自 tag_name_snapshot。 */
    @TableField(exist = false)
    private String statusName;
}
