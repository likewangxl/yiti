package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;

/**
 * 线索详情响应 DTO。
 * <p>
 * 对外暴露线索信息，屏蔽内部字段（如 deleted 等）。
 * </p>
 */
@Data
public class LeadRespDTO {

    /** 线索ID */
    private String id;

    /** 线索编号（对外展示） */
    private String leadNo;

    /** 线索操作类型：CREATE/UPDATE/DELETE */
    private String leadOp;

    /** 线索类型：NEW_ACCOUNT/EXISTING_MARKETING */
    private String leadType;

    /** 源客户ID（UPDATE/DELETE 时有值） */
    private String sourceCustId;

    /** 上一版本线索ID */
    private String prevLeadId;

    /** 版本号 */
    private Integer versionNo;

    /** 是否最新版本：0-否/1-是 */
    private Integer isLatest;

    /** CCRM客户号 */
    private String custNo;

    /** 客户名称 */
    private String custName;

    /** 统一社会信用代码 */
    private String unifiedCreditCode;

    /** 标签ID列表（JSON数组） */
    private String tagIds;

    /** 联系人姓名 */
    private String contactPerson;

    /** 联系人手机号 */
    private String contactMobile;

    /** 行业分类 */
    private String industry;

    /** 集团类型 */
    private String groupType;

    /** 客户类型 */
    private String customerType;

    /** 是否重点客户：0-否/1-是 */
    private Integer isKeystone;

    /** 企业类型 */
    private String enterpriseType;

    /** 所属集团名称 */
    private String groupName;

    /** 是否已开户：0-否/1-是 */
    private Integer isAccountOpened;

    /** 客户描述 */
    private String customerDesc;

    /** 授信金额（元） */
    private BigDecimal creditAmount;

    /** 授信敞口金额（元） */
    private BigDecimal creditExposureAmount;

    /** 线索来源 */
    private String leadSource;

    /** 分配方式：PUBLIC/SCOPE/OWNER */
    private String distributionMode;

    /** 主办客户经理工号 */
    private String mainManagerId;

    private String mainManagerName;

    private String mainManagerOrgId;

    private String mainManagerOrgName;

    /** 指定范围/主办专属人员快照 */
    private List<LeadManagerScopeRespDTO> managerScopes;

    /** 标签名称快照 */
    private List<LeadTagRespDTO> tags;

    /** 线索附件 */
    private List<FileObjectDTO> attachments;

    /** 线索状态 */
    private String leadStatus;

    /** 归属机构代码 */
    private String ownerOrgId;

    /** 线索指派人 */
    private String assignedTo;

    /** 创建人 */
    private String createdBy;

    private String createdByName;

    private String submittedBy;

    private LocalDateTime submittedTime;

    /** 流程业务键 */
    private String businessKey;

    /** 导入批次ID */
    private String importBatchId;

    private Integer batchRowNo;

    /** 流程实例ID */
    private String processInstanceId;

    private String reviewedBy;

    private String reviewedByName;

    private LocalDateTime reviewedTime;

    private String rejectReason;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;
}
