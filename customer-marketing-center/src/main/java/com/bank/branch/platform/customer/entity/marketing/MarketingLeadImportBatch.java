package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** 线索批量导入批次头；批次只记录导入处理结果，不是整批审批单。 */
@Data
@TableName("MARKETING_LEAD_IMPORT_BATCH")
public class MarketingLeadImportBatch {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String sourceFileName;
    private String sourceFileId;
    private String fileChecksum;
    private Integer totalCount;
    private Integer validCount;
    private Integer warningCount;
    private Integer rejectedCount;
    private Integer errorCount;
    private Integer generatedLeadCount;
    private String importStatus;
    private String approvalSummaryStatus;
    private String errorFileId;
    private String confirmAction;
    private String confirmedBy;
    private LocalDateTime confirmedTime;
    private String confirmRemark;
    private String importEmpId;
    private String importOrgId;
    private LocalDateTime importTime;
    private String recordStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    /** 导入确认操作的乐观锁。 */
    @Version
    private Integer lockVersion;
}
