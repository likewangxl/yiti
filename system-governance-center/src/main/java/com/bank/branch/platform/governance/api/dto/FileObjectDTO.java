package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 文件对象传输对象
 */
@Data
public class FileObjectDTO {

    /** 文件对象ID */
    private String id;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型 */
    private String fileType;

    /** MD5 哈希值 */
    private String md5Hash;

    /** 文件用途（来自 biz_file_rel.file_role） */
    private String fileRole;

    /** 上传人工号 */
    private String uploadedBy;

    /** 上传时间（ISO 8601） */
    private String uploadedTime;
}
