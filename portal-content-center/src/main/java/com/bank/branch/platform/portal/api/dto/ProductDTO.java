package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品信息传输对象（不可变）
 *
 * <p>跨模块 API 返回类型，由 ProductApi 对外提供。
 * 同时也用于本模块 Controller 层列表/详情的统一返回。</p>
 *
 * @see com.bank.branch.platform.portal.convert.ProductConverter
 */
@Value
@Builder
public class ProductDTO {

    /** 产品ID */
    String id;

    /** 产品代码 */
    String productCode;

    /** 产品名称 */
    String productName;

    /** 产品类别代码 */
    String productCategory;

    /** 产品类别显示名（字典翻译后，Service 层填充） */
    String productCategoryDesc;

    /** 产品描述 */
    String description;

    /** 是否支持中场支持 */
    Boolean supportForSupportRequest;

    /** 产品部门机构编码（维护组织） */
    String productDeptOrgCode;

    /** 产品部门机构名称（Service 层填充） */
    String productDeptOrgName;

    /** 附件对象ID */
    String fileObjectId;

    /** 产品负责人工号列表 */
    List<String> responsibleEmpIds;

    /** 状态 ACTIVE/DISABLED */
    String status;

    /** 创建时间 */
    LocalDateTime createdTime;

    /** 最后更新时间 */
    LocalDateTime updatedTime;
}
