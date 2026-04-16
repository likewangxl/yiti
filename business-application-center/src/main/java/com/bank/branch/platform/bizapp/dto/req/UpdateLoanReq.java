package com.bank.branch.platform.bizapp.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新贷款申请请求 DTO（所有字段可选）。
 */
@Data
public class UpdateLoanReq {

    /** 项目类型（字典PROJECT_TYPE，可空） */
    private String projectType;

    /** 业务类型（字典BIZ_TYPE，可空） */
    private String bizType;

    /** 担保方式（字典GUARANTEE_TYPE，可空） */
    private String guaranteeType;

    /** 授信金额（可空） */
    private BigDecimal creditAmount;

    /** 敞口金额（可空） */
    private BigDecimal creditExposureAmount;
}
