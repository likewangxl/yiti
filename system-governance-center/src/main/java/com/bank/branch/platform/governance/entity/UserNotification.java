package com.bank.branch.platform.governance.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户通知实体，对应 user_notification 表。
 * <p>
 * 通知表没有 updated_by / updated_time 字段（DDL 设计如此）。
 * is_read 和 read_time 通过单独的更新语句修改。
 * </p>
 */
@Data
public class UserNotification {

    /** 通知ID（UUID主键），对应 id */
    private String id;

    /** 接收人工号，对应 emp_id */
    private String empId;

    /** 通知标题，对应 title */
    private String title;

    /** 通知内容（TEXT类型），对应 content */
    private String content;

    /** 通知类型：SYSTEM-系统, WORKFLOW-流程, BUSINESS-业务，对应 notify_type */
    private String notifyType;

    /** 业务类型（如 LEAD/LOAN 等），对应 biz_type */
    private String bizType;

    /** 业务ID，对应 biz_id */
    private String bizId;

    /** 跳转链接，对应 link_url */
    private String linkUrl;

    /** 是否已读：1-已读, 0-未读，对应 is_read */
    private Integer isRead;

    /** 阅读时间，对应 read_time */
    private LocalDateTime readTime;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;
}
