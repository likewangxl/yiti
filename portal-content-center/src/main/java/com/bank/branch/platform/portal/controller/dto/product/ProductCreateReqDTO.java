package com.bank.branch.platform.portal.controller.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 产品新增请求 DTO（D.4）
 */
@Data
public class ProductCreateReqDTO {

    /** 产品代码（全局唯一） */
    @NotBlank(message = "产品代码不能为空")
    @Size(max = 64, message = "产品代码最长64字符")
    private String productCode;

    /** 产品名称 */
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 200, message = "产品名称最长200字符")
    private String productName;

    /** 产品类别 */
    @Size(max = 50, message = "产品类别最长50字符")
    private String productCategory;

    /** 产品描述 */
    @Size(max = 1000, message = "产品描述最长1000字符")
    private String description;

    /** 是否支持中场支持 */
    private Boolean supportForSupportRequest;

    /** 维护部门机构编码 */
    @NotBlank(message = "维护部门机构编码不能为空")
    private String productDeptOrgCode;

    /** 附件对象ID */
    private String fileObjectId;

    /** 产品负责人工号列表 */
    private List<String> responsibleEmpIds;
}
