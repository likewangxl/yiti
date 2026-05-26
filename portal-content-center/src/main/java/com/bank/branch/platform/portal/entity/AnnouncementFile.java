package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告附件实体
 */
@Data
@TableName("ANNOUNCEMENT_FILE")
public class AnnouncementFile {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 所属公告ID */
    private String announcementId;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 服务端存储路径 */
    private String filePath;

    /** 上传人 */
    private String uploadedBy;

    /** 上传时间 */
    private LocalDateTime uploadTime;
}
