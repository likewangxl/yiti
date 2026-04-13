package com.bank.branch.platform.portal.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品详情 DTO（D.2 详情聚合响应）
 *
 * <p>包含产品列表项的全部字段，以及额外的描述、附件下载URL、编辑权限标识。
 * 不继承 ProductDTO 以避免 Lombok @Data 继承带来的问题。</p>
 */
@Data
public class ProductDetailDTO {

    // ===== ProductDTO 中的全部字段 =====

    /** 产品ID */
    private String id;

    /** 产品代码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 产品类别 */
    private String productCategory;

    /** 产品类别描述（字典翻译后） */
    private String productCategoryDesc;

    /** 产品部门ORG_CODE */
    private String productDeptOrgCode;

    /** 产品部门名称（组织翻译后） */
    private String productDeptOrgName;

    /** 是否支持中场支持请求 */
    private Boolean supportForSupportRequest;

    /** 产品状态：ACTIVE-启用，DISABLED-禁用 */
    private String status;

    /** 主附件文件名 */
    private String fileName;

    /** 负责人列表 */
    private List<ResponsibleEmpDTO> responsibleEmps;

    /** 最后更新人姓名 */
    private String updatedByName;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;

    // ===== 详情页额外字段 =====

    /** 产品描述 */
    private String description;

    /** 附件下载URL */
    private String fileDownloadUrl;

    /** 当前用户是否可编辑 */
    private Boolean canEdit;
}
