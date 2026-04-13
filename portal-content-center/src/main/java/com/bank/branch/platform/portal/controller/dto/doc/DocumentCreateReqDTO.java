package com.bank.branch.platform.portal.controller.dto.doc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文档新增请求 DTO（E.1）
 */
@Data
public class DocumentCreateReqDTO {

    /** 文档标题 */
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 255, message = "文档标题最长255字符")
    private String docTitle;

    /** 文档分类 */
    @NotBlank(message = "文档分类不能为空")
    @Size(max = 50, message = "文档分类最长50字符")
    private String docCategory;

    /** 文件对象ID */
    @NotBlank(message = "文件对象ID不能为空")
    @Size(max = 64, message = "文件对象ID最长64字符")
    private String fileObjectId;
}
