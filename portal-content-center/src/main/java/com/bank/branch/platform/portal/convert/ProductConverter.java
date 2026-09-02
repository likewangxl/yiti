package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductDetailDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;

import java.util.List;

/**
 * ProductInfo Entity -> DTO 转换器
 *
 * <p>纯静态方法，无业务逻辑。productCategoryDesc / productDeptOrgName 留 null，
 * 由 Service 层通过 DictApi / OrgApi 填充。</p>
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
     * Entity -> ProductDTO（跨模块 API 返回 / D.1 列表项）
     *
     * <p>productCategoryDesc / productDeptOrgName 留 null，由 Service 层补充。</p>
     *
     * @param entity 产品实体
     * @return ProductDTO，entity 为 null 时返回 null
     */
    public static ProductDTO toDTO(ProductInfo entity) {
        return toDTO(entity, null);
    }

    /**
     * 将产品实体和关系表派生的负责人列表转换为跨模块 DTO。
     *
     * @param entity 产品实体
     * @param responsibleEmpIds {@code PORTAL_USER_PRODUCT_REL} 派生的负责人 ID
     * @return 产品 DTO
     */
    public static ProductDTO toDTO(ProductInfo entity, List<String> responsibleEmpIds) {
        if (entity == null) return null;
        return ProductDTO.builder()
                .id(entity.getId())
                .productCode(entity.getProductCode())
                .productName(entity.getProductName())
                .productCategory(entity.getProductCategory())
                .productCategoryDesc(null) // 需要 DictApi 翻译，Service 层填充
                .description(entity.getDescription())
                .supportForSupportRequest(entity.getSupportForSupportRequest())
                .productDeptOrgCode(entity.getProductDeptOrgCode())
                .productDeptOrgName(null) // 需要 OrgApi 翻译，Service 层填充
                .fileObjectId(entity.getFileObjectId())
                .responsibleEmpIds(responsibleEmpIds)
                .status(entity.getStatus())
                .createdTime(entity.getCreatedTime())
                .updatedTime(entity.getUpdatedTime())
                .build();
    }

    /**
     * Entity -> ProductDTO（D.1 列表项，别名方法兼容旧调用）
     *
     * @param entity 产品实体
     * @return ProductDTO，entity 为 null 时返回 null
     */
    public static ProductDTO toListItem(ProductInfo entity) {
        return toDTO(entity);
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
