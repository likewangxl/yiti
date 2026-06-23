package com.bank.branch.platform.portal.controller.dto.guarantee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 担保信息新增/编辑入参（新增与编辑共用，字段一致）。
 *
 * <p>金额/日期均以字符串承载，对齐物理表 varchar 列，保留业务方录入原值。</p>
 */
@Data
public class GuaranteeSaveReqDTO {

    /** 客户名称（必填） */
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 100, message = "客户名称长度不能超过100")
    private String clientName;

    /** 业务额度（万元） */
    @Size(max = 50, message = "业务额度长度不能超过50")
    private String amountManage;

    /** 剩余额度（万元） */
    @Size(max = 20, message = "剩余额度长度不能超过20")
    private String usableExposureSum;

    /** 融资额度（万元） */
    @Size(max = 30, message = "融资额度长度不能超过30")
    private String exposureAmount;

    /** 授信到期日（yyyy-MM-dd） */
    @Size(max = 30, message = "授信到期日长度不能超过30")
    private String lastExpire;

    /** 经办人 */
    @Size(max = 20, message = "经办人长度不能超过20")
    private String operator;
}
