package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 红色引擎-上报附件 */
@Data
@TableName("RE_SUBMIT_FILE")
public class ReSubmitFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 上报ID */
    private Long submitId;
    /** 平台文件ID(FILE_OBJECT,经 governance FileApi 上传) */
    private String fileObjectId;
    /** 文件名 */
    private String fileName;
    /** 旧文件路径(仅迁移数据兼容,新数据置空) */
    private String filePath;
    /** 文件大小 */
    private Long fileSize;
    /** 文件类型 */
    private String fileType;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
