package com.bank.branch.platform.portal.controller.dto.product;

import lombok.Data;

/**
 * 产品查询请求 DTO（D.1 分页查询）
 */
@Data
public class ProductQueryReqDTO {

    /** 关键字搜索（产品名/产品代码模糊匹配） */
    private String keyword;

    /** 产品类别过滤 */
    private String category;

    /** 产品部门机构编码过滤 */
    private String productDeptOrgCode;

    /** 是否支持中场支持 */
    private Boolean supportForSupportRequest;

    /** 状态过滤：ACTIVE / DISABLED */
    private String status;

    /** 页码（默认 1） */
    private Integer pageNo = 1;

    /** 每页大小（默认 20） */
    private Integer pageSize = 20;
}
