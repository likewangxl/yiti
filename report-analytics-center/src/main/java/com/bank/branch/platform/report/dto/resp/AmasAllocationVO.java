package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/**
 * 业绩分配明细视图（AMAS_PERFORMANCE_ALLOCATION）.
 */
@Data
public class AmasAllocationVO {

    /** 业绩调整编号. */
    private String perfAdjustNo;
    /** 分配人工号. */
    private String username;
    /** 姓名. */
    private String fullname;
    /** 部门. */
    private String dept;
    /** 部门名称. */
    private String deptName;
    /** 分配比例. */
    private String ratio;
    /** 是否原分配关系：1,是；2,否. */
    private String isOriginal;
    /** 审核状态：0,待审批；1,同意；2,拒绝. */
    private String apprStatus;
}
