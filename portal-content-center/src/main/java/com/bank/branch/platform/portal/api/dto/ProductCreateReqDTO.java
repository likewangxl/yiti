package com.bank.branch.platform.portal.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/**
 * D.4 新增产品请求 DTO
 */
@Data
public class ProductCreateReqDTO {
    @NotBlank @Size(max = 64)
    private String productCode;

    @NotBlank @Size(max = 255)
    private String productName;

    @NotBlank @Size(max = 64)
    private String productCategory;

    @Size(max = 5000)
    private String description;

    @NotNull
    private Boolean supportForSupportRequest;

    @NotBlank @Size(max = 50)
    private String productDeptOrgCode;

    @Size(max = 64)
    private String fileObjectId;

    @Size(max = 10, message = "负责人最多 10 个")
    private List<String> responsibleEmpIds;
}
