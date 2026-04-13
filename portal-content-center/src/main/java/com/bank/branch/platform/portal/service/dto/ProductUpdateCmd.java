package com.bank.branch.platform.portal.service.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 编辑产品的 Service 层内部命令对象
 */
@Data
@Builder
public class ProductUpdateCmd {
    private String productName;
    private String productCategory;
    private String description;
    private Boolean supportForSupportRequest;
    private String fileObjectId;
    private String status;
    private List<String> responsibleEmpIds;
}
