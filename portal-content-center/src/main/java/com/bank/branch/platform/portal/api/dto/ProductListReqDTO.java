package com.bank.branch.platform.portal.api.dto;

import com.bank.branch.platform.common.web.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品列表查询请求 DTO（Controller 层入参）
 * <p>继承 PageRequest 获得分页参数 (pageNo, pageSize, sortBy, sortDir)</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductListReqDTO extends PageRequest {
    /** 关键字搜索 */
    private String keyword;
    /** 产品类别 */
    private String category;
    /** 状态过滤：ACTIVE / DISABLED，默认 ACTIVE */
    private String status;
    /** 产品部门机构编码 */
    private String productDeptOrgCode;
    /** 是否支持中场支持 */
    private Boolean supportForSupportRequest;
}
