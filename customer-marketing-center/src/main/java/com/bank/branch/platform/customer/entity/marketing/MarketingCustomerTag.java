package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 营销客户正式标签实体，对应 MARKETING_CUSTOMER_TAG。
 *
 * <p>标签先以 {@code PENDING + DISABLED} 保存，审核通过后才可以参与客户导入和正式客户群查询。</p>
 */
@Data
@TableName("MARKETING_CUSTOMER_TAG")
public class MarketingCustomerTag {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String tagName;
    private String tagCategory;
    private String tagType;
    private Integer tagPriority;
    private String description;
    private String status;
    private String approvalStatus;
    private LocalDate expiresAt;
    private String ownerOrgId;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String rejectReason;
    private String recordStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    @Version
    private Integer lockVersion;

    /** 当前有效客户数，列表查询通过聚合查询回填，不落表。 */
    @TableField(exist = false)
    private Long customerCount;
}
