package com.bank.branch.platform.governance.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 通知发送命令
 * 用于 NotifyApi.sendNotification() 方法的入参
 */
@Data
@Builder
public class NotificationCmd {

    /** 接收人工号（必填） */
    private String targetEmpId;

    /** 通知标题（必填） */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型 SYSTEM/WORKFLOW/BUSINESS */
    private String notifyType;

    /** 关联业务类型（用于跳转） */
    private String bizType;

    /** 关联业务ID（用于跳转） */
    private String bizId;

    /** 跳转链接 */
    private String linkUrl;
}
