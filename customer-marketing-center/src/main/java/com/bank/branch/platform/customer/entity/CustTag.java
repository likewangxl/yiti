package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 客户标签实体，对应 cust_tag 表。
 * <p>
 * status 字段枚举：ACTIVE-启用，DISABLED-停用。
 * deleted 字段实现逻辑删除：0-未删除，1-已删除。
 * </p>
 */
@Data
@TableName("CUST_TAG")
public class CustTag {

    /** 主键ID（UUID，32位去连字符），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 标签名称（唯一），对应 tag_name */
    private String tagName;

    /** 标签分类（如：价值类/行业类/风险类），对应 tag_category */
    private String tagCategory;

    /** 标签优先级（数字越大优先级越高，用于排序），对应 tag_priority */
    private Integer tagPriority;

    /** 标签描述，对应 description */
    private String description;

    /** 状态：ACTIVE-启用/DISABLED-停用，对应 status */
    private String status;

    /** 标签类型：PROJECT-项目类/CERTIFICATION-认定类。 */
    private String tagType;

    /** 审核状态：PENDING/APPROVED/REJECTED。 */
    private String approvalStatus;

    /** 失效日期，空表示长期有效。 */
    private LocalDate expiresAt;

    /** 标签创建机构。 */
    private String ownerOrgId;

    /** 审核人员工工号。 */
    private String reviewedBy;

    /** 审核人姓名，仅用于接口展示，不对应数据库字段。 */
    @TableField(exist = false)
    private String reviewedByName;

    /** 审核时间。 */
    private LocalDateTime reviewedTime;

    /** 退回原因。 */
    private String rejectReason;

    /** 创建人（员工工号），对应 created_by */
    private String createdBy;

    /** 创建人姓名，仅用于接口展示，不对应数据库字段。 */
    @TableField(exist = false)
    private String createdByName;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 最后更新人，对应 updated_by */
    private String updatedBy;

    /** 最后更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 逻辑删除：0-未删除/1-已删除，对应 deleted */
    private Integer deleted;
}
