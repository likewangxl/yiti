package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户过滤条件 DTO
 * <p>
 * 用于跨模块传递客户列表查询的过滤条件，支持关键词、行业、客户类型、机构范围等多维过滤。
 * 此 DTO 作为 CustomerQueryApi 的入参，调用方需自行填充数据范围限制字段。
 * </p>
 */
@Data
public class CustomerFilterDTO {

    /** 关键词 (匹配客户名称或统一社会信用代码) */
    private String keyword;

    /** 行业代码列表 */
    private List<String> industries;

    /** 客户类型列表 */
    private List<String> customerTypes;

    /** 是否重点客户 */
    private Boolean isKeystone;

    /** 客户状态: VALID / DELETED */
    private String status;

    /** 机构 ID 列表 (数据范围过滤) */
    private List<String> orgIds;

    /** 创建时间范围 - 开始 */
    private LocalDateTime createdStart;

    /** 创建时间范围 - 结束 */
    private LocalDateTime createdEnd;
}
