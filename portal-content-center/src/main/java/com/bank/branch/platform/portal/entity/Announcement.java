package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告实体，对应 ANNOUNCEMENT 表。
 */
@Data
@TableName("ANNOUNCEMENT")
public class Announcement {

    /** 公告ID（UUID主键） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 公告标题 */
    private String title;

    /** 公告内容 */
    private String content;

    /** 发布人ID */
    private String publisherId;

    /** 发布人姓名 */
    private String publisherName;

    /** 发布日期 */
    private LocalDateTime publishDate;

    /** 置顶：0-否，1-是 */
    private Integer isPinned;

    /** 逻辑删除：0-未删除，1-已删除 */
    private Integer isDeleted;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
