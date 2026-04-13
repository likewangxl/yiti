package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductDetailDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;

import java.util.List;

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

    /**
     * Entity -> ProductDTO（D.1 列表项）
     * <p>V1 简化版：categoryDesc/orgName/fileName/responsibleEmps/updatedByName 暂留空，Phase 5+ 补齐</p>
     *
     * @param entity 产品实体
     * @return 列表 DTO，entity 为 null 时返回 null
     */
    public static ProductDTO toListItem(ProductInfo entity) {
        if (entity == null) return null;
        ProductDTO dto = new ProductDTO();
        dto.setId(entity.getId());
        dto.setProductCode(entity.getProductCode());
        dto.setProductName(entity.getProductName());
        dto.setProductCategory(entity.getProductCategory());
        dto.setProductDeptOrgCode(entity.getProductDeptOrgCode());
        dto.setSupportForSupportRequest(entity.getSupportForSupportRequest());
        dto.setStatus(entity.getStatus());
        dto.setUpdatedTime(entity.getUpdatedTime());
        // V1 simplified: these fields require cross-module calls, filled in later phases
        // dto.setProductCategoryDesc(...);  // needs DictApi
        // dto.setProductDeptOrgName(...);   // needs OrgApi
        // dto.setFileName(...);             // needs FileApi
        // dto.setResponsibleEmps(...);      // needs AddrbookQueryService
        // dto.setUpdatedByName(...);        // needs auth lookup
        return dto;
    }

    /**
     * Entity -> ProductDetailDTO（D.2 详情聚合响应）
     *
     * @param entity          产品实体
     * @param categoryDesc    字典翻译后的产品类别描述
     * @param orgName         机构翻译后的产品部门名称
     * @param fileDownloadUrl 附件下载链接
     * @param responsibleEmps 负责人列表（含脱敏）
     * @param canEdit         当前用户是否可编辑
     * @return 产品详情 DTO，entity 为 null 时返回 null
     */
    public static ProductDetailDTO toDetail(
            ProductInfo entity,
            String categoryDesc,
            String orgName,
            String fileDownloadUrl,
            List<ResponsibleEmpDTO> responsibleEmps,
            boolean canEdit) {
        if (entity == null) return null;
        ProductDetailDTO dto = new ProductDetailDTO();
        dto.setId(entity.getId());
        dto.setProductCode(entity.getProductCode());
        dto.setProductName(entity.getProductName());
        dto.setProductCategory(entity.getProductCategory());
        dto.setProductCategoryDesc(categoryDesc);
        dto.setProductDeptOrgCode(entity.getProductDeptOrgCode());
        dto.setProductDeptOrgName(orgName);
        dto.setSupportForSupportRequest(entity.getSupportForSupportRequest());
        dto.setStatus(entity.getStatus());
        dto.setUpdatedTime(entity.getUpdatedTime());
        dto.setDescription(entity.getDescription());
        dto.setFileDownloadUrl(fileDownloadUrl);
        dto.setResponsibleEmps(responsibleEmps);
        dto.setCanEdit(canEdit);
        return dto;
    }
}
