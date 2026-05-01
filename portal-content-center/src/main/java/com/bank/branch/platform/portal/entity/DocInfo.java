package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档信息实体，对应 doc_info 表。
 * <p>
 * 用于门户文档下载中心，管理各类文档资料。
 * status 字段使用字符串枚举：ACTIVE-启用，DISABLED-禁用。
 * </p>
 */
@Data
@TableName("doc_info")
public class DocInfo {

    /** 文档ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 文档标题，对应 doc_title */
    private String docTitle;

    /** 文档分类，对应 doc_category */
    private String docCategory;

    /** 文件对象ID，对应 file_object_id */
    private String fileObjectId;

    /** 状态：ACTIVE-启用，DISABLED-禁用，对应 status */
    private String status;

    /** 创建人，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
