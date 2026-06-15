package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/**
 * 业绩调整申请 列表行 / 详情主信息 视图（字段名与实体一致，便于 BeanUtils 拷贝）.
 */
@Data
public class AllocAdjustApplyRowVO {

    /** 申请ID. */
    private String id;
    /** 申请编号. */
    private String applyNo;
    /** 客户号. */
    private String custId;
    /** 客户名称. */
    private String custName;
    /** 客户类型：CORP 公司 / RETAIL 零售. */
    private String custType;
    /** 调整方式：RULE 规则 / ACCOUNT 账号. */
    private String allocDim;
    /** 业务种类. */
    private String bizKind;
    /** 账号. */
    private String accountNo;
    /** 状态. */
    private String status;
    /** 备注. */
    private String remark;
    /** 申请人（created_by，存的是 PT_USER.USER_ID，非工号）. */
    private String createdBy;
    /** 申请人姓名（按 USER_ID 解析 PT_USER.USERCHNNAME，解析不到为 null）. */
    private String createdByName;
    /** 申请人工号（PT_USER.USERNAME，按 USER_ID 解析，解析不到为 null）. */
    private String createdByNo;
    /** 申请时间. */
    private String createdTime;
}
