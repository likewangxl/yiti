package com.bank.branch.platform.portal.controller.dto.product;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 产品更新请求 DTO（D.5）
 *
 * <p>所有字段均为可选，仅传入需要更新的字段。</p>
 */
@Data
public class ProductUpdateReqDTO {

    /** 产品名称 */
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

    /** 附件对象ID */
    private String fileObjectId;

    /** 产品负责人工号列表 */
    private List<String> responsibleEmpIds;
}
