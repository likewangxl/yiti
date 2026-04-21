package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

/**
 * 客户标签对外 DTO
 * <p>
 * 用于跨模块传递标签基础信息，包含标签分类、优先级及启用状态。
 * 标签用于对客户进行多维度分类，支持营销策略的精细化运营。
 * </p>
 */
@Data
public class TagDTO {

    /** 标签 ID */
    private String id;

    /** 标签名称 */
    private String tagName;

    /** 标签编码 */
    private String tagCode;

    /** 标签分类 */
    private String tagCategory;

    /** 标签优先级 */
    private Integer tagPriority;

    /** 标签状态: ENABLED / DISABLED */
    private String status;

    /** 标签描述 */
    private String description;
}
