package com.bank.branch.platform.customer.dto.req;

import lombok.Data;

/**
 * 更新标签请求 DTO。
 * <p>
 * tagCode 字段用于做服务端一致性校验（编码不可修改）。
 * 前端传入的 tagCode 必须与原值相同，否则服务端抛出 CUST-40906 错误。
 * </p>
 */
@Data
public class TagUpdateReqDTO {

    /** 标签名称 */
    private String tagName;

    /** 标签编码（不可修改，必须与原值一致） */
    private String tagCode;

    /** 标签描述 */
    private String description;

    /** 标签分类 */
    private String tagCategory;

    /** 标签优先级 */
    private Integer tagPriority;
}
