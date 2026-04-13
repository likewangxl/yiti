package com.bank.branch.platform.portal.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 编辑产品请求 DTO（D.5）
 * <p>productCode 和 productDeptOrgCode 不可修改（编译期不出现在 DTO 中）</p>
 */
@Data
public class ProductUpdateReqDTO {
    @Size(max = 255)
    private String productName;

    @Size(max = 64)
    private String productCategory;

    @Size(max = 5000)
    private String description;

    private Boolean supportForSupportRequest;

    @Size(max = 64)
    private String fileObjectId;

    @Pattern(regexp = "ACTIVE|DISABLED")
    private String status;

    @Size(max = 10, message = "负责人最多 10 个")
    private List<String> responsibleEmpIds;
}
