package com.bank.branch.platform.portal.controller.dto.doc;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文档更新请求 DTO（E.2）
 *
 * <p>所有字段均为可选，仅传入需要更新的字段。</p>
 */
@Data
public class DocumentUpdateReqDTO {

    /** 文档标题 */
    @Size(max = 255, message = "文档标题最长255字符")
    private String docTitle;

    /** 文档分类 */
    @Size(max = 50, message = "文档分类最长50字符")
    private String docCategory;

    /** 文件对象ID */
    @Size(max = 64, message = "文件对象ID最长64字符")
    private String fileObjectId;

    /** 状态 ACTIVE/DISABLED */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "状态只能为 ACTIVE 或 DISABLED")
    private String status;
}
