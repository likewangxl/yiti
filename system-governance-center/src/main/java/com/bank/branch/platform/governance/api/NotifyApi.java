package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;

import java.util.List;

/**
 * 通知消息对外API
 * 提供通知的发送与查询能力
 * sendNotification 允许异步（事务提交后执行）
 */
public interface NotifyApi {

    /**
     * 发送通知给指定用户
     * 允许在事务提交后异步执行（使用 @TransactionalEventListener）
     *
     * @param cmd 通知发送命令
     * @throws IllegalArgumentException targetEmpId 或 title 为空时
     */
    void sendNotification(NotificationCmd cmd);

    /**
     * 批量发送通知
     * 用于流程审批后通知多个相关人员
     *
     * @param cmds 通知发送命令列表
     */
    void batchSendNotifications(List<NotificationCmd> cmds);

    /**
     * 查询用户未读通知数量
     * 用于工作台未读通知卡片
     *
     * @param empId 用户工号
     * @return 未读通知数量
     */
    int countUnread(String empId);

    /**
     * 查询用户通知列表（分页）
     *
     * @param empId  用户工号
     * @param isRead 是否已读（null=全部, true=已读, false=未读）
     * @param page   分页参数
     * @return 分页通知列表
     */
    PageResult<NotificationDTO> queryNotifications(String empId, Boolean isRead, PageRequest page);
}
