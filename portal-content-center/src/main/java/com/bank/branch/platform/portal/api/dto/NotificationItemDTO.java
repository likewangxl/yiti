package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 工作台通知项 DTO（不可变）
 *
 * <p>用于工作台数据聚合，从 NotifyApi 适配而来。</p>
 */
@Value
@Builder
public class NotificationItemDTO {

    /** 通知ID */
    String notificationId;

    /** 标题 */
    String title;

    /** 摘要 */
    String summary;

    /** 发送时间 */
    LocalDateTime sentTime;

    /** 读状态 READ/UNREAD */
    String readStatus;

    /** 关联业务类型 */
    String bizType;

    /** 关联业务ID */
    String bizId;

    /** 业务详情页URL */
    String bizDetailUrl;
}
