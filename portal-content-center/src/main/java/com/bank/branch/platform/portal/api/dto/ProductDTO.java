package com.bank.branch.platform.portal.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品列表项 DTO（D.1 分页查询响应）
 *
 * <p>包含产品基本信息、分类描述、状态、负责人列表等，
 * 用于产品管理页面的分页列表展示。</p>
 */
@Data
public class ProductDTO {

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
}
