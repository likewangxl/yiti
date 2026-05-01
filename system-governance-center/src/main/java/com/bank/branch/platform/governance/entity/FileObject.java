package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件对象实体，对应 file_object 表。
 * <p>
 * 存储上传到 MinIO 对象存储的文件元数据信息。
 * 使用 md5_hash 字段实现文件去重，避免相同文件重复上传。
 * </p>
 */
@Data
@TableName("FILE_OBJECT")
public class FileObject {

    /** 文件对象ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 文件名，对应 file_name */
    private String fileName;

    /** 文件大小（字节），对应 file_size */
    private Long fileSize;

    /** 文件类型（MIME类型或扩展名），对应 file_type */
    private String fileType;

    /** 存储路径（MinIO 对象存储），对应 storage_path */
    private String storagePath;

    /** 存储桶名称，对应 bucket_name */
    private String bucketName;

    /** MD5 哈希值（用于去重校验），对应 md5_hash */
    private String md5Hash;

    /** 上传人工号，对应 uploaded_by */
    private String uploadedBy;

    /** 上传时间，对应 uploaded_time */
    private LocalDateTime uploadedTime;
}
