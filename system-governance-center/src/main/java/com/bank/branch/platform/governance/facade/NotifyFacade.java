package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;
import com.bank.branch.platform.governance.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知消息 Facade 实现
 * <p>
 * 实现 NotifyApi 接口，委托 NotificationService 完成通知的发送与查询。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyFacade implements NotifyApi {

    private final NotificationService notificationService;

    /**
     * 发送通知给指定用户
     * 委托给 NotificationService.sendNotification()
     *
     * @param cmd 通知发送命令
     */
    @Override
    public void sendNotification(NotificationCmd cmd) {
        notificationService.sendNotification(cmd);
    }

    /**
     * 批量发送通知
     * 委托给 NotificationService.batchSendNotifications()
     *
     * @param cmds 通知发送命令列表
     */
    @Override
    public void batchSendNotifications(List<NotificationCmd> cmds) {
        notificationService.batchSendNotifications(cmds);
    }

    /**
     * 查询用户未读通知数量
     * 委托给 NotificationService.countUnread()
     *
     * @param empId 用户工号
     * @return 未读通知数量
     */
    @Override
    public int countUnread(String empId) {
        return notificationService.countUnread(empId);
    }

    /**
     * 查询用户通知列表（分页）
     * 委托给 NotificationService.queryNotifications()
     *
     * @param empId  用户工号
     * @param isRead 是否已读
     * @param page   分页参数
     * @return 分页通知列表
     */
    @Override
    public PageResult<NotificationDTO> queryNotifications(String empId, Boolean isRead, PageRequest page) {
        return notificationService.queryNotifications(empId, isRead, page.getPageNo(), page.getPageSize());
    }
}
