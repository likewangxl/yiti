package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户主档实体，对应 cust_master 表。
 * <p>
 * 客户主档由审批通过的 CREATE 类线索生成，后续通过 UPDATE/DELETE 类线索维护。
 * owner_org_id 仅作来源属性，不承载可见性，客户可见性通过 cust_claim 认领关系判定。
 * status 枚举：ACTIVE-正常/INACTIVE-非活跃。
 * deleted 字段实现逻辑删除：0-未删除，1-已删除。
 * 注意：该表无 created_by/updated_by 字段。
 * </p>
 */
@Data
@TableName("CUST_MASTER")
public class CustMaster {

    /** 主键ID（UUID，32位去连字符），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 客户编号（对外展示，唯一），对应 cust_no */
    private String custNo;

    /** 客户名称（唯一），对应 cust_name */
    private String custName;

    /** 统一社会信用代码，对应 unified_credit_code */
    private String unifiedCreditCode;

    /** 联系人姓名，对应 contact_person */
    private String contactPerson;

    /** 联系人手机号，对应 contact_mobile */
    private String contactMobile;

    /** 行业分类（字典 INDUSTRY），对应 industry */
    private String industry;

    /** 集团类型（字典 GROUP_TYPE），对应 group_type */
    private String groupType;

    /** 客户类型（字典 CUSTOMER_TYPE），对应 customer_type */
    private String customerType;

    /** 是否重点客户：0-否/1-是，对应 is_keystone */
    private Integer isKeystone;

    /** 企业类型（字典 ENTERPRISE_TYPE），对应 enterprise_type */
    private String enterpriseType;

    /** 所属集团名称，对应 group_name */
    private String groupName;

    /** 是否已开户：0-否/1-是，对应 is_account_opened */
    private Integer isAccountOpened;

    /** 客户描述，对应 customer_desc */
    private String customerDesc;

    /** 授信金额（元），对应 credit_amount */
    private BigDecimal creditAmount;

    /** 授信敞口金额（元），对应 credit_exposure_amount */
    private BigDecimal creditExposureAmount;

    /** 来源机构代码（不承载可见性，仅作来源属性），对应 owner_org_id */
    private String ownerOrgId;

    /** 来源线索ID（关联 cust_lead.id），对应 lead_id */
    private String leadId;

    /** 客户状态：ACTIVE/INACTIVE，对应 status */
    private String status;

    /** 逻辑删除：0-未删除/1-已删除，对应 deleted */
    private Integer deleted;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 最后更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
