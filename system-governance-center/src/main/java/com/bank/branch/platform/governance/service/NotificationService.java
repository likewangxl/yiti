package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;
import com.bank.branch.platform.governance.entity.UserNotification;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.enums.NotifyType;
import com.bank.branch.platform.governance.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 通知服务
 * <p>
 * 负责用户通知的发送、查询、已读状态管理。
 * 通知发送为同步写入，允许调用方通过 @TransactionalEventListener 实现异步。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    /** 日期时间格式化器，用于 DTO 输出 */
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 发送通知给指定用户。
     * <p>
     * 根据 NotificationCmd 创建通知实体，生成 UUID 主键后插入数据库。
     * notifyType 为空时默认使用 SYSTEM。
     * </p>
     *
     * @param cmd 通知发送命令
     */
    public void sendNotification(NotificationCmd cmd) {
        log.info("[NotificationService.sendNotification] targetEmpId={}, title={}, notifyType={}",
                cmd.getTargetEmpId(), cmd.getTitle(), cmd.getNotifyType());

        UserNotification entity = new UserNotification();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setEmpId(cmd.getTargetEmpId());
        entity.setTitle(cmd.getTitle());
        entity.setContent(cmd.getContent());
        // notifyType 为空时默认 SYSTEM
        entity.setNotifyType(cmd.getNotifyType() != null ? cmd.getNotifyType() : NotifyType.SYSTEM.getCode());
        entity.setBizType(cmd.getBizType());
        entity.setBizId(cmd.getBizId());
        entity.setLinkUrl(cmd.getLinkUrl());
        entity.setIsRead(0);
        entity.setCreatedTime(LocalDateTime.now());

        notificationMapper.insert(entity);
        log.info("[NotificationService.sendNotification] 通知发送成功 id={}", entity.getId());
    }

    /**
     * 批量发送通知。
     * <p>
     * 逐条调用 sendNotification，单条失败不影响其他通知的发送。
     * </p>
     *
     * @param cmds 通知发送命令列表
     */
    public void batchSendNotifications(List<NotificationCmd> cmds) {
        log.info("[NotificationService.batchSendNotifications] size={}", cmds.size());
        for (NotificationCmd cmd : cmds) {
            try {
                sendNotification(cmd);
            } catch (Exception e) {
                // 单条失败不影响其他通知，记录错误日志后继续
                log.error("[NotificationService.batchSendNotifications] 通知发送失败 targetEmpId={}, title={}",
                        cmd.getTargetEmpId(), cmd.getTitle(), e);
            }
        }
    }

    /**
     * 查询指定用户的未读通知数量。
     *
     * @param empId 接收人工号
     * @return 未读通知数量
     */
    public int countUnread(String empId) {
        log.debug("[NotificationService.countUnread] empId={}", empId);
        return notificationMapper.countUnread(empId);
    }

    /**
     * 分页查询用户通知列表。
     *
     * @param empId    接收人工号
     * @param isRead   是否已读（null=全部, true=已读, false=未读）
     * @param pageNo   当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页通知列表
     */
    public PageResult<NotificationDTO> queryNotifications(String empId, Boolean isRead, int pageNo, int pageSize) {
        log.debug("[NotificationService.queryNotifications] empId={}, isRead={}, pageNo={}, pageSize={}",
                empId, isRead, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        // Boolean 转 Integer：null→null, true→1, false→0
        Integer isReadInt = isRead != null ? (isRead ? 1 : 0) : null;

        long total = notificationMapper.countByEmpId(empId, isReadInt);
        List<UserNotification> records = notificationMapper.selectByEmpId(empId, isReadInt, offset, pageSize);

        List<NotificationDTO> dtos = records.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * 将单条通知标记为已读。
     *
     * @param id 通知ID
     * @throws BizException GOV-40006 通知不存在
     */
    public void markAsRead(String id) {
        log.info("[NotificationService.markAsRead] id={}", id);

        UserNotification existing = notificationMapper.selectById(id);
        if (existing == null) {
            throw new BizException(GovErrorCode.NOTICE_NOT_FOUND.getCode(),
                    GovErrorCode.NOTICE_NOT_FOUND.getMessage());
        }

        // 已读状态幂等处理：已读通知重复标记不报错
        if (existing.getIsRead() != null && existing.getIsRead() == 1) {
            log.debug("[NotificationService.markAsRead] 通知已是已读状态，跳过 id={}", id);
            return;
        }

        notificationMapper.updateReadStatus(id, 1, LocalDateTime.now());
        log.info("[NotificationService.markAsRead] 通知标记已读成功 id={}", id);
    }

    /**
     * 将指定用户的所有未读通知标记为已读。
     *
     * @param empId 接收人工号
     * @return 本次标记已读的通知条数
     */
    public int markAllAsRead(String empId) {
        log.info("[NotificationService.markAllAsRead] empId={}", empId);
        int count = notificationMapper.markAllAsRead(empId, LocalDateTime.now());
        log.info("[NotificationService.markAllAsRead] 标记已读 {} 条通知", count);
        return count;
    }

    /**
     * 根据ID查询通知详情。
     *
     * @param id 通知ID
     * @return 通知 DTO
     * @throws BizException GOV-40006 通知不存在
     */
    public NotificationDTO getById(String id) {
        log.debug("[NotificationService.getById] id={}", id);

        UserNotification existing = notificationMapper.selectById(id);
        if (existing == null) {
            throw new BizException(GovErrorCode.NOTICE_NOT_FOUND.getCode(),
                    GovErrorCode.NOTICE_NOT_FOUND.getMessage());
        }
        return toDTO(existing);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 将 UserNotification 实体转换为 NotificationDTO
     *
     * @param entity 通知实体
     * @return NotificationDTO
     */
    private NotificationDTO toDTO(UserNotification entity) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(entity.getId());
        dto.setEmpId(entity.getEmpId());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setNotifyType(entity.getNotifyType());
        dto.setNotifyTypeLabel(resolveNotifyTypeLabel(entity.getNotifyType()));
        dto.setBizType(entity.getBizType());
        dto.setBizId(entity.getBizId());
        dto.setLinkUrl(entity.getLinkUrl());
        dto.setIsRead(entity.getIsRead() != null && entity.getIsRead() == 1);
        dto.setReadTime(entity.getReadTime() != null ? entity.getReadTime().format(DT_FMT) : null);
        dto.setCreatedTime(entity.getCreatedTime() != null ? entity.getCreatedTime().format(DT_FMT) : null);
        return dto;
    }

    /**
     * 根据通知类型编码获取显示名称
     *
     * @param notifyTypeCode 通知类型编码
     * @return 显示名称，未匹配到时返回编码本身
     */
    private String resolveNotifyTypeLabel(String notifyTypeCode) {
        if (notifyTypeCode == null) {
            return null;
        }
        for (NotifyType type : NotifyType.values()) {
            if (type.getCode().equals(notifyTypeCode)) {
                return type.getDescription();
            }
        }
        return notifyTypeCode;
    }
}
