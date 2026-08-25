package com.bank.branch.platform.customer.dto.req;

import lombok.Data;

/** 更新标签请求 DTO。 */
@Data
public class TagUpdateReqDTO {

    /** 标签名称 */
    private String tagName;

    /** 标签描述 */
    private String description;

    /** 标签分类 */
    private String tagCategory;

    /** 标签优先级 */
    private Integer tagPriority;
}
