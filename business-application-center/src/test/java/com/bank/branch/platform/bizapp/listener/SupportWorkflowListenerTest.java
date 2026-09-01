package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportCompletedEvent;
import com.bank.branch.platform.bizapp.event.SupportRejectedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SupportWorkflowListener 单元测试（TDD）。
 * 覆盖 APPROVED / REJECTED / 幂等 / 注解结构 共 8 个用例。
 */
@ExtendWith(MockitoExtension.class)
class SupportWorkflowListenerTest {

    @Mock
    private SupportRequestMapper supportMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NotifyApi notifyApi;

    @Mock
    private ProductApi productApi;

    @Mock
    private UserApi userApi;

    @InjectMocks
    private SupportWorkflowListener listener;

    // ==================== APPROVED 分支 ====================

    @Test
    @DisplayName("APPROVED 结果：conditionalUpdateStatus 更新为 COMPLETED，发布 SupportCompletedEvent")
    void onProcessCompleted_approved_updatesToCompleted_andPublishesSupportCompletedEvent() {
        // given
        SupportRequest sr = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        sr.setAssignedEmpId("E20001");
        when(supportMapper.conditionalUpdateStatus("SR001", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.COMPLETED.getCode(), "SYSTEM")).thenReturn(1);
        when(supportMapper.selectById("SR001")).thenReturn(sr);

        // when
        listener.onProcessCompleted(
                new ProcessCompletedEvent("PID001", "SUPPORT:SR001", "APPROVED", null));

        // then: 使用条件更新，目标状态为 COMPLETED
        verify(supportMapper).conditionalUpdateStatus("SR001", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.COMPLETED.getCode(), "SYSTEM");
        // then: 发布 SupportCompletedEvent
        ArgumentCaptor<SupportCompletedEvent> captor = ArgumentCaptor.forClass(SupportCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().getRequestId()).isEqualTo("SR001");
    }

    // ==================== REJECTED 分支 ====================

    @Test
    @DisplayName("REJECTED 结果：conditionalUpdateStatus 更新为 REJECTED，发布带 rejectReason 的 SupportRejectedEvent")
    void onProcessCompleted_rejected_updatesToRejected_andPublishesSupportRejectedEvent() {
        // given
        SupportRequest sr = buildRequest("SR002", SupportStatus.IN_APPROVAL);
        when(supportMapper.conditionalUpdateStatus("SR002", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.REJECTED.getCode(), "SYSTEM")).thenReturn(1);
        when(supportMapper.selectById("SR002")).thenReturn(sr);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent("pid-2", "SUPPORT:SR002", "REJECTED", "客户资质不足");

        // when
        listener.onProcessCompleted(event);

        // then: 条件更新到 REJECTED
        verify(supportMapper).conditionalUpdateStatus("SR002", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.REJECTED.getCode(), "SYSTEM");
        // then: 发布 SupportRejectedEvent，携带 rejectReason
        ArgumentCaptor<SupportRejectedEvent> captor = ArgumentCaptor.forClass(SupportRejectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRejectReason()).isEqualTo("客户资质不足");
        assertThat(captor.getValue().getRequestId()).isEqualTo("SR002");
    }

    @Test
    @DisplayName("REJECTED 结果 reason=null：rejectReason 为 null 且不抛异常")
    void onProcessCompleted_rejected_reasonNull_doesNotThrow() {
        // given
        SupportRequest sr = buildRequest("SR003", SupportStatus.IN_APPROVAL);
        when(supportMapper.conditionalUpdateStatus("SR003", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.REJECTED.getCode(), "SYSTEM")).thenReturn(1);
        when(supportMapper.selectById("SR003")).thenReturn(sr);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent("pid-3", "SUPPORT:SR003", "REJECTED", null);

        // when / then
        assertThatCode(() -> listener.onProcessCompleted(event)).doesNotThrowAnyException();
        ArgumentCaptor<SupportRejectedEvent> captor = ArgumentCaptor.forClass(SupportRejectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRejectReason()).isNull();
    }

    // ==================== 幂等保护 ====================

    @Test
    @DisplayName("conditionalUpdateStatus 返回 0 时：不发布任何事件（幂等保护）")
    void onProcessCompleted_whenRowsAffectedZero_doesNotPublishEvent() {
        // given
        when(supportMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);

        // when
        listener.onProcessCompleted(
                new ProcessCompletedEvent("PID001", "SUPPORT:SR001", "APPROVED", null));

        // then
        verify(eventPublisher, never()).publishEvent(any());
        verify(supportMapper, never()).selectById(anyString());
    }

    // ==================== 边界：非 SUPPORT 前缀 ====================

    @Test
    @DisplayName("非 SUPPORT: 前缀的 businessKey：直接忽略，不查询数据库")
    void onProcessCompleted_nonSupportBusinessKey_shouldIgnore() {
        // given: LOAN: 前缀，不是 SUPPORT:
        listener.onProcessCompleted(
                new ProcessCompletedEvent("PID001", "LOAN:LA001", "APPROVED", null));

        // then
        verify(supportMapper, never()).conditionalUpdateStatus(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== 注解结构验证 ====================

    @Test
    @DisplayName("onProcessCompleted 方法应标注 @TransactionalEventListener(AFTER_COMMIT) + @Transactional(REQUIRES_NEW)")
    void onProcessCompleted_shouldBeAnnotatedWithTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method method = SupportWorkflowListener.class.getMethod(
                "onProcessCompleted", ProcessCompletedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).as("方法应标注 @TransactionalEventListener").isNotNull();
        assertThat(annotation.phase())
                .as("phase 应为 AFTER_COMMIT")
                .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution())
                .as("fallbackExecution 应为 false：与 @Transactional(REQUIRES_NEW) 配合后无需 fallback 路径"
                        + "（与 customer.WorkflowCallbackListener P0 修复 pattern 一致）")
                .isFalse();

        // P0 bug 防御性修复：要求 @Transactional(REQUIRES_NEW)，让本方法内 publishEvent 在新事务内 publish，
        // 未来若新增下游 AFTER_COMMIT listener 订阅 SupportCompleted/SupportRejected 也无需 fallbackExecution 即可正确触发
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).as("方法应标注 @Transactional 以让 publishEvent 在新事务内发布").isNotNull();
        assertThat(transactional.propagation())
                .as("propagation 应为 REQUIRES_NEW")
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.rollbackFor())
                .as("rollbackFor 应包含 Exception.class")
                .contains(Exception.class);
    }

    // ==================== 异常不传播 ====================

    @Test
    @DisplayName("处理过程中抛出异常：不应传播到调用方")
    void onProcessCompleted_exceptionInHandler_shouldNotPropagate() {
        // given
        when(supportMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB Error"));

        // when / then
        assertThatCode(() -> listener.onProcessCompleted(
                new ProcessCompletedEvent("PID001", "SUPPORT:SR001", "APPROVED", null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ZT-04 场景B：流程结束后通知承接部门 SUPPORT_SE，通知失败不影响主流程")
    void onProcessCompleted_sceneB_notifiesSupportDepartmentSecretary() {
        SupportRequest sr = buildRequest("SR004", SupportStatus.IN_PROGRESS);
        sr.setSupportDeptId("SUPPORT-DEPT");
        when(supportMapper.conditionalUpdateStatus("SR004", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.COMPLETED.getCode(), "SYSTEM")).thenReturn(0);
        when(supportMapper.conditionalUpdateStatus("SR004", SupportStatus.IN_PROGRESS.getCode(),
                SupportStatus.COMPLETED.getCode(), "SYSTEM")).thenReturn(1);
        when(supportMapper.selectById("SR004")).thenReturn(sr);
        when(userApi.getEmpIdsByRoleCodeAndOrg("SUPPORT_SE", "SUPPORT-DEPT"))
                .thenReturn(List.of("E501", "E502"));

        listener.onProcessCompleted(new ProcessCompletedEvent("PID004", "SUPPORT:SR004", "APPROVED", null));

        ArgumentCaptor<NotificationCmd> captor = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notifyApi, org.mockito.Mockito.times(3)).sendNotification(captor.capture());
        assertThat(captor.getAllValues().stream()
                .filter(cmd -> "中台支持结束知悉".equals(cmd.getTitle()))
                .map(NotificationCmd::getTargetEmpId))
                .containsExactlyInAnyOrder("E501", "E502");
    }

    @Test
    @DisplayName("ZT-04 场景A：从产品维护部门解析 SUPPORT_SE 知悉人")
    void onProcessCompleted_sceneA_resolvesProductDepartmentForSecretary() {
        SupportRequest sr = buildRequest("SR005", SupportStatus.IN_APPROVAL);
        sr.setProductId("P001");
        when(supportMapper.conditionalUpdateStatus("SR005", SupportStatus.IN_APPROVAL.getCode(),
                SupportStatus.COMPLETED.getCode(), "SYSTEM")).thenReturn(1);
        when(supportMapper.selectById("SR005")).thenReturn(sr);
        when(productApi.getProduct("P001")).thenReturn(Optional.of(ProductDTO.builder()
                .id("P001").productDeptOrgCode("PRODUCT-DEPT").build()));
        when(userApi.getEmpIdsByRoleCodeAndOrg("SUPPORT_SE", "PRODUCT-DEPT"))
                .thenReturn(List.of("E601"));

        listener.onProcessCompleted(new ProcessCompletedEvent("PID005", "SUPPORT:SR005", "APPROVED", null));

        ArgumentCaptor<NotificationCmd> captor = ArgumentCaptor.forClass(NotificationCmd.class);
        verify(notifyApi, org.mockito.Mockito.times(2)).sendNotification(captor.capture());
        assertThat(captor.getAllValues().stream()
                .filter(cmd -> "中台支持结束知悉".equals(cmd.getTitle()))
                .map(NotificationCmd::getTargetEmpId))
                .containsExactly("E601");
    }

    // ==================== 辅助方法 ====================

    private SupportRequest buildRequest(String id, SupportStatus status) {
        SupportRequest sr = new SupportRequest();
        sr.setId(id);
        sr.setRequestNo("SR20260414000001");
        sr.setCustId("CUST001");
        sr.setStatus(status.getCode());
        sr.setBusinessKey("SUPPORT:" + id);
        sr.setOwnerOrgId("ORG001");
        sr.setCreatedBy("E001");
        sr.setDeleted(0);
        return sr;
    }
}
