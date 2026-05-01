package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户线索实体，对应 cust_lead 表。
 * <p>
 * 支持版本管理：同一线索链通过 prev_lead_id 链表追溯，is_latest=1 标记最新版本。
 * lead_op 区分操作类型：CREATE-新建/UPDATE-修改/DELETE-删除。
 * lead_status 状态机：DRAFT→SUBMITTED→IN_APPROVAL→APPROVED/REJECTED。
 * deleted 字段实现逻辑删除：0-未删除，1-已删除。
 * </p>
 */
@Data
@TableName("CUST_LEAD")
public class CustLead {

    /** 主键ID（UUID，32位去连字符），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 线索编号（对外展示，唯一），对应 lead_no */
    private String leadNo;

    /** 线索操作类型：CREATE-新建/UPDATE-修改/DELETE-删除，对应 lead_op */
    private String leadOp;

    /** 源客户ID（UPDATE/DELETE 时指向已有 cust_master.id），对应 source_cust_id */
    private String sourceCustId;

    /** 上一版本线索ID（UPDATE 时指向被修订的 cust_lead.id），对应 prev_lead_id */
    private String prevLeadId;

    /** 版本号（从1开始递增），对应 version_no */
    private Integer versionNo;

    /** 是否最新版本：0-否/1-是，对应 is_latest */
    private Integer isLatest;

    /** 客户名称，对应 cust_name */
    private String custName;

    /** 统一社会信用代码，对应 unified_credit_code */
    private String unifiedCreditCode;

    /** 标签ID列表（JSON数组，如 ["TAG_001","TAG_002"]），对应 tag_ids */
    private String tagIds;

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

    /** 线索来源（字典 LEAD_SOURCE），对应 lead_source */
    private String leadSource;

    /** 线索状态：DRAFT/SUBMITTED/IN_APPROVAL/APPROVED/REJECTED，对应 lead_status */
    private String leadStatus;

    /** 归属机构代码（来源，不承载可见性），对应 owner_org_id */
    private String ownerOrgId;

    /** 线索指派人（员工工号），对应 assigned_to */
    private String assignedTo;

    /** 创建人（员工工号），对应 created_by */
    private String createdBy;

    /** 流程业务键（格式 LEAD:{leadId}），对应 business_key */
    private String businessKey;

    /** 导入批次ID（关联 lead_import_batch.id），对应 import_batch_id */
    private String importBatchId;

    /** 流程实例ID（Flowable），对应 process_instance_id */
    private String processInstanceId;

    /** 备注，对应 remark */
    private String remark;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 最后更新人，对应 updated_by */
    private String updatedBy;

    /** 最后更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 逻辑删除：0-未删除/1-已删除，对应 deleted */
    private Integer deleted;
}
