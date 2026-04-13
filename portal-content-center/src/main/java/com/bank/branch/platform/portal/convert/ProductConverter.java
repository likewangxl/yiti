package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;

/**
 * ProductInfo Entity -> DTO 转换器
 * <p>纯静态方法，无业务逻辑。toListItem / toDetail 在 Phase 4/5 按需补充。</p>
 */
public final class ProductConverter {

    private ProductConverter() {}

    /**
     * Entity -> ProductSimpleDTO（D.3 支持产品列表）
     *
     * @param entity 产品实体
     * @return 简要 DTO，entity 为 null 时返回 null
     */
    public static ProductSimpleDTO toSimple(ProductInfo entity) {
        if (entity == null) return null;
        ProductSimpleDTO dto = new ProductSimpleDTO();
        dto.setId(entity.getId());
        dto.setProductCode(entity.getProductCode());
        dto.setProductName(entity.getProductName());
        dto.setProductCategory(entity.getProductCategory());
        dto.setProductDeptOrgCode(entity.getProductDeptOrgCode());
        return dto;
    }

    // toListItem and toDetail will be added in Phase 4/5
}
