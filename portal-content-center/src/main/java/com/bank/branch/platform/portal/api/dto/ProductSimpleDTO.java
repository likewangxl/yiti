package com.bank.branch.platform.portal.api.dto;

import lombok.Data;

/**
 * 产品简要信息 DTO（D.3 支持产品列表用）
 *
 * <p>仅包含产品核心标识字段，用于中场支持等场景下的产品选择列表。</p>
 */
@Data
public class ProductSimpleDTO {

    /** 产品ID */
    private String id;

    /** 产品代码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 产品类别 */
    private String productCategory;

    /** 产品部门ORG_CODE */
    private String productDeptOrgCode;
}
