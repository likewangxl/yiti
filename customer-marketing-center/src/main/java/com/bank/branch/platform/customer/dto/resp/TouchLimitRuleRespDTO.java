package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

/** 客户标签触达周期规则分页响应。 */
@Data
public class TouchLimitRuleRespDTO {

    /** 标签 ID。 */
    private String tagId;

    /** 标签名称。 */
    private String tagName;

    /** 标签启停状态。 */
    private String tagStatus;

    /** 标签审核状态。 */
    private String approvalStatus;

    /** 触达周期单位。 */
    private String cycleUnit;

    /** 周期内最多触达次数。 */
    private Integer maxTouches;
}
