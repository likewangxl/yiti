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

    /** 客户号 */
    private String clientNo;

    /** 客户名称 */
    private String clientName;

    /** 业务额度（万元），对应 notional_amount */
    private String notionalAmount;

    /** 剩余额度（万元），对应 occupy_notional_amount */
    private String occupyNotionalAmount;

    /** 融资额度（万元），对应 usablenominalsum */
    private String usableNominalSum;

    /** 授信到期日 */
    private String lastExpire;

    /** 经办人工号（属性名 userName，存 operator 列） */
    private String userName;

    /** 经办人姓名（由工号 userName 解析，列表展示主标题；离职/查无回退工号） */
    private String userDisplayName;

    /** 数据变动日期（yyyy-MM-dd HH:mm:ss） */
    private String createTime;

    /** 变更日期 */
    private String updateTime;
}
