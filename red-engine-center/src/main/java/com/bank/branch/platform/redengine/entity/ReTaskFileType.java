package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务允许上传的文件类型。 */
@Data
@TableName("RE_TASK_FILE_TYPE")
public class ReTaskFileType {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String fileTypeCode;
    private String fileTypeName;
    private String fileExtension;
    private String mimeType;
    private Long maxSizeBytes;
    private Integer sortNo;
    private Integer enabled;
    private String createdBy;
    private LocalDateTime createTime;
    private String updatedBy;
    private LocalDateTime updateTime;
}
