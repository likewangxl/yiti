package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.event.SysControlUpdatedEvent;
import com.bank.branch.platform.performance.mapper.SysControlMapper;
import com.bank.branch.platform.performance.service.cmd.SwitchVersionCmd;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.bank.branch.platform.performance.support.SysControlTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysControlService 切换/回滚场景发布 SysControlUpdatedEvent 集成测试 (V1.2 Q1.3, Red).
 *
 * <p>覆盖：
 * <ul>
 *   <li>doSwitchVersion 调用 publisher.publish(SysControlUpdatedEvent) with publishSource=MANUAL</li>
 *   <li>rollback 调用 publisher.publish(SysControlUpdatedEvent) with publishSource=ROLLBACK</li>
 *   <li>事件字段契约：scopeDim / oldVersion / newVersion / publishBy 对齐入参</li>
 *   <li>topic 字符串对齐 performance.sys-control.updated.v1</li>
 * </ul>
 *
 * <p>本测试采用 Mockito 纯单元测试：mock SysControlMapper + PerfEventPublisher，
 * 验证 Service 正确"调用"事件发布器；事务后投递机制由 PerfEventPublisherTest 覆盖。
 */
class SysControlServiceEventIT extends PerformanceServiceTestBase {

    @Mock
    private SysControlMapper sysControlMapper;

    @Mock
    private PerfEventPublisher perfEventPublisher;

    @InjectMocks
    private SysControlService service;

    @Test
    @DisplayName("[Red] doSwitchVersion 成功后发布 SysControlUpdatedEvent，publishSource=MANUAL")
    void switchVersion_publishesSysControlUpdatedEvent_withMANUAL() {
        // Given
        SysControl curr = SysControlTestDataBuilder.buildTest(
                "E1", "EMP", LocalDate.of(2099, 1, 1), "V_OLD", 1);
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(curr);
        when(sysControlMapper.insert(any(SysControl.class))).thenReturn(1);

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim("EMP")
                .dataDate(LocalDate.of(2099, 2, 1))
                .newVersion("V_NEW")
                .reason("正常切换")
                .operator("admin")
                .publishSource("MANUAL")
                .build();

        // When
        service.doSwitchVersion(cmd);

        // Then
        ArgumentCaptor<SysControlUpdatedEvent> captor =
                ArgumentCaptor.forClass(SysControlUpdatedEvent.class);
        verify(perfEventPublisher).publish(captor.capture());
        SysControlUpdatedEvent e = captor.getValue();
        assertThat(e.getScopeDim()).isEqualTo("EMP");
        assertThat(e.getOldVersion()).isEqualTo("V_OLD");
        assertThat(e.getNewVersion()).isEqualTo("V_NEW");
        assertThat(e.getPublishSource()).isEqualTo("MANUAL");
        assertThat(e.getPublishBy()).isEqualTo("admin");
        assertThat(e.topic()).isEqualTo("performance.sys-control.updated.v1");
    }

    @Test
    @DisplayName("[Red] rollback 成功后发布 SysControlUpdatedEvent，publishSource=ROLLBACK")
    void rollback_publishesSysControlUpdatedEvent_withROLLBACK() {
        // Given
        SysControl v1 = SysControlTestDataBuilder.buildTest(
                "E2", "EMP", LocalDate.of(2099, 1, 1), "V_RB_1", 0);
        SysControl v2 = SysControlTestDataBuilder.buildTest(
                "E3", "EMP", LocalDate.of(2099, 2, 1), "V_RB_2", 1);
        when(sysControlMapper.listByScope(eq("EMP"), anyInt())).thenReturn(List.of(v2, v1));
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(v2);
        when(sysControlMapper.insert(any(SysControl.class))).thenReturn(1);

        // When
        service.rollback("EMP", "V_RB_1", "紧急回滚", "boss");

        // Then
        ArgumentCaptor<SysControlUpdatedEvent> captor =
                ArgumentCaptor.forClass(SysControlUpdatedEvent.class);
        verify(perfEventPublisher).publish(captor.capture());
        SysControlUpdatedEvent e = captor.getValue();
        assertThat(e.getScopeDim()).isEqualTo("EMP");
        assertThat(e.getOldVersion()).isEqualTo("V_RB_2");
        assertThat(e.getNewVersion()).isEqualTo("V_RB_1");
        assertThat(e.getPublishSource()).isEqualTo("ROLLBACK");
        assertThat(e.getPublishBy()).isEqualTo("boss");
        assertThat(e.topic()).isEqualTo("performance.sys-control.updated.v1");
    }
}
