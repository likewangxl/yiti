package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 触达工作日志图片，对应 MARKETING_TOUCH_WORKLOG_PICTURE。 */
@Data
@TableName("MARKETING_TOUCH_WORKLOG_PICTURE")
public class TouchWorklogPictureRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long worklogId;
    private String pictureType;
    private String fileObjectId;
    private Integer sortNo;
    private String createdBy;
    private LocalDateTime createdTime;
    private String recordStatus;
}
