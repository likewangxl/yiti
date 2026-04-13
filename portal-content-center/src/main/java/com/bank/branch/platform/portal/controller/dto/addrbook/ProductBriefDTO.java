package com.bank.branch.platform.portal.controller.dto.addrbook;

import lombok.Data;

/**
 * 产品简要信息 DTO（通讯录详情页中展示的关联产品）
 */
@Data
public class ProductBriefDTO {

    /** 产品ID */
    private String id;

    /** 产品代码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 产品类别 */
    private String productCategory;
}
