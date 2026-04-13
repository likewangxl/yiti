package com.bank.branch.platform.portal.service.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 新增产品的 Service 层内部命令对象
 */
@Data
@Builder
public class ProductCreateCmd {
    private String productCode;
    private String productName;
    private String productCategory;
    private String description;
    private Boolean supportForSupportRequest;
    private String productDeptOrgCode;
    private String fileObjectId;
    private List<String> responsibleEmpIds;
}
