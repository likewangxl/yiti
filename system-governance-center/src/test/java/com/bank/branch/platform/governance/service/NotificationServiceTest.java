package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;
import com.bank.branch.platform.governance.entity.UserNotification;
import com.bank.branch.platform.governance.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 通知服务单元测试
 * TDD RED 阶段：先编写测试用例，确保编译失败后再实现生产代码
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationMapper notificationMapper;

    @InjectMocks
    NotificationService notificationService;

    /**
     * 测试 sendNotification：验证 mapper.insert 被调用且实体字段正确映射
     */
    @Test
    void sendNotification_insertsRecord() {
        NotificationCmd cmd = NotificationCmd.builder()
                .targetEmpId("E001")
                .title("审批通知")
                .content("您有一条新的审批任务")
                .notifyType("WORKFLOW")
                .bizType("LOAN")
                .bizId("LOAN-001")
                .linkUrl("/workflow/task/LOAN-001")
                .build();

        when(notificationMapper.insert(any(UserNotification.class))).thenReturn(1);

        notificationService.sendNotification(cmd);

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationMapper).insert(captor.capture());

        UserNotification entity = captor.getValue();
        assertThat(entity.getId()).isNotNull().hasSize(32);
        assertThat(entity.getEmpId()).isEqualTo("E001");
        assertThat(entity.getTitle()).isEqualTo("审批通知");
        assertThat(entity.getContent()).isEqualTo("您有一条新的审批任务");
        assertThat(entity.getNotifyType()).isEqualTo("WORKFLOW");
        assertThat(entity.getBizType()).isEqualTo("LOAN");
        assertThat(entity.getBizId()).isEqualTo("LOAN-001");
        assertThat(entity.getLinkUrl()).isEqualTo("/workflow/task/LOAN-001");
        assertThat(entity.getIsRead()).isEqualTo(0);
        assertThat(entity.getCreatedTime()).isNotNull();
    }

    /**
     * 测试 countUnread：验证正确委托给 mapper
     */
    @Test
    void countUnread_delegatesToMapper() {
        when(notificationMapper.countUnread("E001")).thenReturn(5);

        int count = notificationService.countUnread("E001");

        assertThat(count).isEqualTo(5);
        verify(notificationMapper).countUnread("E001");
    }

    /**
     * 测试 markAsRead：验证更新已读状态
     */
    @Test
    void markAsRead_updatesReadStatus() {
        UserNotification existing = makeNotification("N001", "E001", "测试通知");
        when(notificationMapper.selectById("N001")).thenReturn(existing);
        when(notificationMapper.updateReadStatus(eq("N001"), eq(1), any(LocalDateTime.class))).thenReturn(1);

        notificationService.markAsRead("N001");

        verify(notificationMapper).updateReadStatus(eq("N001"), eq(1), any(LocalDateTime.class));
    }

    /**
     * 测试 markAsRead：通知不存在时抛出 GOV-40006
     */
    @Test
    void markAsRead_notFound_throwsGov40006() {
        when(notificationMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> notificationService.markAsRead("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "GOV-40006");
    }

    /**
     * 测试 markAsReadForUser：本用户通知标记已读成功
     */
    @Test
    void markAsReadForUser_correctUser_callsMarkAsRead() {
        UserNotification existing = makeNotification("N001", "E001", "测试通知");
        when(notificationMapper.selectById("N001")).thenReturn(existing);
        when(notificationMapper.updateReadStatus(eq("N001"), eq(1), any(LocalDateTime.class))).thenReturn(1);

        notificationService.markAsReadForUser("N001", "E001");

        verify(notificationMapper).updateReadStatus(eq("N001"), eq(1), any(LocalDateTime.class));
    }

    /**
     * 测试 markAsReadForUser：非本用户通知抛出 GOV-40006（不暴露通知是否存在）
     */
    @Test
    void markAsReadForUser_wrongUser_throwsGov40006() {
        UserNotification existing = makeNotification("N001", "E001", "测试通知");
        when(notificationMapper.selectById("N001")).thenReturn(existing);

        assertThatThrownBy(() -> notificationService.markAsReadForUser("N001", "E002"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "GOV-40006");
    }

    /**
     * 测试 markAsReadForUser：通知不存在时抛出 GOV-40006
     */
    @Test
    void markAsReadForUser_notFound_throwsGov40006() {
        when(notificationMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> notificationService.markAsReadForUser("NOT_EXIST", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "GOV-40006");
    }

    /**
     * 测试 getByIdForUser：本用户通知查询成功
     */
    @Test
    void getByIdForUser_correctUser_returnsDto() {
        UserNotification existing = makeNotification("N001", "E001", "测试通知");
        existing.setNotifyType("SYSTEM");
        when(notificationMapper.selectById("N001")).thenReturn(existing);

        NotificationDTO dto = notificationService.getByIdForUser("N001", "E001");

        assertThat(dto.getId()).isEqualTo("N001");
        assertThat(dto.getEmpId()).isEqualTo("E001");
        assertThat(dto.getTitle()).isEqualTo("测试通知");
    }

    /**
     * 测试 getByIdForUser：非本用户通知抛出 GOV-40006（不暴露通知是否存在）
     */
    @Test
    void getByIdForUser_wrongUser_throwsGov40006() {
        UserNotification existing = makeNotification("N001", "E001", "测试通知");
        when(notificationMapper.selectById("N001")).thenReturn(existing);

        assertThatThrownBy(() -> notificationService.getByIdForUser("N001", "E002"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "GOV-40006");
    }

    /**
     * 测试 getByIdForUser：通知不存在时抛出 GOV-40006
     */
    @Test
    void getByIdForUser_notFound_throwsGov40006() {
        when(notificationMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> notificationService.getByIdForUser("NOT_EXIST", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "GOV-40006");
    }

    /**
     * 测试 queryNotifications：验证分页查询参数传递和结果组装
     */
    @Test
    void queryNotifications_returnsPaginatedResult() {
        UserNotification n1 = makeNotification("N001", "E001", "通知1");
        UserNotification n2 = makeNotification("N002", "E001", "通知2");

        // isRead=null 对应全部查询，Integer 类型传 null
        when(notificationMapper.countByEmpId("E001", null)).thenReturn(2L);
        when(notificationMapper.selectByEmpId("E001", null, 0, 10)).thenReturn(List.of(n1, n2));

        PageResult<NotificationDTO> result = notificationService.queryNotifications("E001", null, 1, 10);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getId()).isEqualTo("N001");
        assertThat(result.getRecords().get(1).getId()).isEqualTo("N002");

        verify(notificationMapper).selectByEmpId("E001", null, 0, 10);
        verify(notificationMapper).countByEmpId("E001", null);
    }

    /**
     * 测试 getById：通知不存在时抛出 GOV-40006
     */
    @Test
    void getById_notFound_throwsGov40006() {
        when(notificationMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> notificationService.getById("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "GOV-40006");
    }

    /**
     * 测试 getById：正常返回 DTO
     */
    @Test
    void getById_returnsDto() {
        UserNotification existing = makeNotification("N001", "E001", "测试通知");
        existing.setNotifyType("SYSTEM");
        when(notificationMapper.selectById("N001")).thenReturn(existing);

        NotificationDTO dto = notificationService.getById("N001");

        assertThat(dto.getId()).isEqualTo("N001");
        assertThat(dto.getEmpId()).isEqualTo("E001");
        assertThat(dto.getTitle()).isEqualTo("测试通知");
        assertThat(dto.getNotifyType()).isEqualTo("SYSTEM");
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    /**
     * 测试 markAllAsRead：委托给 mapper 并返回影响行数
     */
    @Test
    void markAllAsRead_delegatesToMapper() {
        when(notificationMapper.markAllAsRead(eq("E001"), any(LocalDateTime.class))).thenReturn(3);

        int count = notificationService.markAllAsRead("E001");

        assertThat(count).isEqualTo(3);
        verify(notificationMapper).markAllAsRead(eq("E001"), any(LocalDateTime.class));
    }

    /**
     * 测试 queryNotifications：isRead=true 时传递 1 给 mapper
     */
    @Test
    void queryNotifications_withIsReadTrue_passesIntegerOne() {
        when(notificationMapper.countByEmpId("E001", 1)).thenReturn(1L);
        UserNotification n = makeNotification("N001", "E001", "已读通知");
        n.setIsRead(1);
        when(notificationMapper.selectByEmpId("E001", 1, 0, 10)).thenReturn(List.of(n));

        PageResult<NotificationDTO> result = notificationService.queryNotifications("E001", true, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        verify(notificationMapper).selectByEmpId("E001", 1, 0, 10);
    }

    /**
     * 测试 queryNotifications：isRead=false 时传递 0 给 mapper
     */
    @Test
    void queryNotifications_withIsReadFalse_passesIntegerZero() {
        when(notificationMapper.countByEmpId("E001", 0)).thenReturn(2L);
        when(notificationMapper.selectByEmpId("E001", 0, 0, 10))
                .thenReturn(List.of(makeNotification("N001", "E001", "未读1"), makeNotification("N002", "E001", "未读2")));

        PageResult<NotificationDTO> result = notificationService.queryNotifications("E001", false, 1, 10);

        assertThat(result.getTotal()).isEqualTo(2L);
        verify(notificationMapper).countByEmpId("E001", 0);
    }

    /**
     * 测试 queryNotifications：空结果集返回空 PageResult
     */
    @Test
    void queryNotifications_emptyResult_returnsEmptyPage() {
        when(notificationMapper.countByEmpId("E999", null)).thenReturn(0L);
        when(notificationMapper.selectByEmpId("E999", null, 0, 20)).thenReturn(List.of());

        PageResult<NotificationDTO> result = notificationService.queryNotifications("E999", null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
    }

    /**
     * 测试 countUnread：empId 无未读通知时返回 0
     */
    @Test
    void countUnread_noUnread_returnsZero() {
        when(notificationMapper.countUnread("E999")).thenReturn(0);

        int count = notificationService.countUnread("E999");

        assertThat(count).isEqualTo(0);
    }

    // ── 辅助方法 ──────────────────────────────────────────────────

    private UserNotification makeNotification(String id, String empId, String title) {
        UserNotification n = new UserNotification();
        n.setId(id);
        n.setEmpId(empId);
        n.setTitle(title);
        n.setContent("测试内容");
        n.setNotifyType("SYSTEM");
        n.setBizType("SYS_CONFIG");
        n.setBizId("C001");
        n.setLinkUrl("/config/C001");
        n.setIsRead(0);
        n.setCreatedTime(LocalDateTime.now());
        return n;
    }
}
