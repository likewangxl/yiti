package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 文档信息传输对象（不可变）
 *
 * <p>跨模块 API 返回类型，由 DocumentApi 对外提供。</p>
 */
@Value
@Builder
public class DocumentDTO {

    /** 文档ID */
    String id;

    /** 文档标题 */
    String docTitle;

    /** 分类代码 */
    String docCategory;

    /** 分类显示名（字典翻译后，Service 层填充） */
    String docCategoryDesc;

    /** 文件对象ID */
    String fileObjectId;

    /** 文件名 */
    String fileName;

    /** 状态 ACTIVE/DISABLED */
    String status;

    /** 文件大小（字节，来自关联 FileObject，Service/Controller 层填充） */
    Long fileSize;

    /** 更新人工号 */
    String updatedBy;

    /** 更新时间 */
    LocalDateTime updatedTime;
}
