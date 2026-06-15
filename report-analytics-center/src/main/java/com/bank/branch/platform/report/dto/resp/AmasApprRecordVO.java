package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/**
 * 审批流程流转记录视图（AMAS_APPR_RECORD）.
 */
@Data
public class AmasApprRecordVO {

    /** 记录编号. */
    private String recordId;
    /** 模块编号. */
    private String moduleId;
    /** 源数据编号（关联 PERF_ADJUST_NO）. */
    private String regionDtId;
    /** 审批名称（节点名）. */
    private String apprName;
    /** 序号. */
    private Long apprSeq;
    /** 类型：1,判断；2,审批. */
    private String confType;
    /** 是否多人审批：1,是；2,否. */
    private String isMultiAppr;
    /** 创建时间. */
    private String createTime;
    /** 审批角色. */
    private Long apprRole;
    /** 审批人工号. */
    private String apprUsername;
    /** 审批人姓名. */
    private String apprFullname;
    /** 审批时间. */
    private String apprTime;
    /** 审批状态：0,待审批；1,通过；2,未通过. */
    private String apprStatus;
    /** 审批意见. */
    private String apprOpinion;
    /** 是否系统执行：1,是；2,否. */
    private String isSysAppr;
    /** 系统备注. */
    private String sysMessage;
    /** 跳过步骤. */
    private String skipStep;
}
