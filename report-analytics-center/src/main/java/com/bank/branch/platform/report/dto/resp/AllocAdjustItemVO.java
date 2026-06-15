package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 业绩分配明细视图（PERF_ALLOC_ADJUST_ITEM）—— 详情页「业绩分配数据」.
 */
@Data
public class AllocAdjustItemVO {

    /** 申请ID. */
    private String applyId;
    /** 账号. */
    private String acctNo;
    /** 分配人工号. */
    private String empId;
    /** 用户名. */
    private String username;
    /** 分配人姓名. */
    private String empChnName;
    /** 机构编码. */
    private String orgCode;
    /** 机构名称. */
    private String orgName;
    /** 分配比例. */
    private BigDecimal ratio;
    /** 备注. */
    private String remark;
}
