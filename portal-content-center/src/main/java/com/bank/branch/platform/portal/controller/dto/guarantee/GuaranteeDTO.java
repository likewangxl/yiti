package com.bank.branch.platform.portal.controller.dto.guarantee;

import lombok.Builder;
import lombok.Data;

/**
 * 担保信息响应 DTO（列表与详情共用）。
 */
@Data
@Builder
public class GuaranteeDTO {

    /** 主键 */
    private Long id;

    /** 客户名称 */
    private String clientName;

    /** 业务额度（万元） */
    private String amountManage;

    /** 剩余额度（万元） */
    private String usableExposureSum;

    /** 融资额度（万元） */
    private String exposureAmount;

    /** 授信到期日 */
    private String lastExpire;

    /** 经办人 */
    private String operator;

    /** 数据变动日期（yyyy-MM-dd HH:mm:ss） */
    private String createTime;

    /** 变更日期 */
    private String updateTime;
}
