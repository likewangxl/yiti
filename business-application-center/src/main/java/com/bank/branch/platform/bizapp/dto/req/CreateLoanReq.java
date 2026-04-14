package com.bank.branch.platform.bizapp.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建贷款申请请求 DTO。
 */
@Data
public class CreateLoanReq {

    /** 客户ID（必填） */
    @NotBlank(message = "客户ID不能为空")
    private String custId;

    /** 来源触达任务ID（可空） */
    private String sourceTouchTaskId;

    /** 项目类型（字典PROJECT_TYPE） */
    private String projectType;

    /** 业务类型（字典BIZ_TYPE） */
    private String bizType;

    /** 担保方式（字典GUARANTEE_TYPE） */
    private String guaranteeType;

    /** 授信金额 */
    private BigDecimal creditAmount;

    /** 敞口金额 */
    private BigDecimal creditExposureAmount;
}
