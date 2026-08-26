package com.bank.branch.platform.customer.dto.marketing.tag;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 标签客户文件导入请求。一个批次只能对应一个标签。 */
@Data
public class TagImportCreateRequest {
    private Long tagId;
    private String tagName;
    private String tagCategory;
    private String tagType;
    private Integer tagPriority;
    private String tagDescription;

    @NotBlank
    private String importMode;
}
