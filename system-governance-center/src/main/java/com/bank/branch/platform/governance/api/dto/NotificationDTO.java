package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 通知传输对象
 */
@Data
public class NotificationDTO {

    /** 通知ID */
    private String id;

    /** 接收人工号 */
    private String empId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型 SYSTEM/WORKFLOW/BUSINESS */
    private String notifyType;

    /** 通知类型显示名称 */
    private String notifyTypeLabel;

    /** 关联业务类型 */
    private String bizType;

    /** 关联业务ID */
    private String bizId;

    /** 跳转链接 */
    private String linkUrl;

    /** 是否已读 */
    private Boolean isRead;

    /** 阅读时间（ISO 8601） */
    private String readTime;

    /** 创建时间（ISO 8601） */
    private String createdTime;
}
