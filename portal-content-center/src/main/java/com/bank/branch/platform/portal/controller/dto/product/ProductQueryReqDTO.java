package com.bank.branch.platform.portal.controller.dto.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNo = 1;

    /** 每页大小（默认 20，最大 100） */
    @Min(value = 1, message = "每页大小最小为 1")
    @Max(value = 100, message = "每页大小最大为 100")
    private Integer pageSize = 20;
}
