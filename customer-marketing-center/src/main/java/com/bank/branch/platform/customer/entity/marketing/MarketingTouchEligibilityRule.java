package com.bank.branch.platform.customer.entity.marketing;

import lombok.Data;

/**
 * 触达资格校验所需的正式标签规则快照。
 *
 * <p>该对象由 {@code MARKETING_CUSTOMER_TAG_REL}、
 * {@code MARKETING_CUSTOMER_TAG} 和 {@code MARKETING_TOUCH_LIMIT_RULE}
 * 联查返回，不对应独立物理表。</p>
 */
@Data
public class MarketingTouchEligibilityRule {

    /** 正式标签 ID。 */
    private Long tagId;

    /** 正式标签名称。 */
    private String tagName;

    /** 周期单位：DAY/WEEK/MONTH/QUARTER/YEAR。 */
    private String cycleType;

    /** 自然周期内最多有效触达次数。 */
    private Integer maxTouchCount;
}
