package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.SysControlMapper;
import com.bank.branch.platform.performance.service.cmd.SwitchVersionCmd;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.bank.branch.platform.performance.support.SysControlTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysControlService 纯单元测试.
 */
class SysControlServiceTest extends PerformanceServiceTestBase {

    @Mock
    private SysControlMapper sysControlMapper;

    /** V1.2 Q1.3：事件发布器依赖, 单测中 mock（不需验证调用, 只确保不 NPE）. */
    @Mock
    private PerfEventPublisher perfEventPublisher;

    @InjectMocks
    private SysControlService sysControlService;

    @Test
    @DisplayName("getCurrentVersion 命中时返回实体")
    void getCurrentVersion_whenFound_shouldReturn() {
        // Given
        SysControl curr = SysControlTestDataBuilder.buildTest(
                "G01", "EMP", LocalDate.of(2099, 6, 1), "V_CURR", 1);
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(curr);

        // When
        SysControl got = sysControlService.getCurrentVersion("EMP");

        // Then
        assertThat(got).isNotNull();
        assertThat(got.getCurrentVersion()).isEqualTo("V_CURR");
    }

    @Test
    @DisplayName("getCurrentVersion 未找到时抛 PERF-40406")
    void getCurrentVersion_whenNotFound_shouldThrow40406() {
        // Given
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> sysControlService.getCurrentVersion("EMP"))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND));
    }

    @Test
    @DisplayName("initIfAbsent 当 UK 已存在时返回既有记录")
    void initIfAbsent_whenExists_shouldReturnExisting() {
        // Given
        SysControl existed = SysControlTestDataBuilder.buildTest(
                "G02", "EMP", LocalDate.of(2099, 6, 1), "V_EXIST", 1);
        // 1 次找不到当前有效版本 (避免与实际业务冲突, 但本方法不依赖它)
        when(sysControlMapper.countByCondition("EMP", null)).thenReturn(1L);
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(existed);

        // When
        SysControl got = sysControlService.initIfAbsent("EMP", LocalDate.of(2099, 6, 1), "V_NEW");

        // Then: 返回既有记录, 不应再 insert
        assertThat(got).isSameAs(existed);
        verify(sysControlMapper, never()).insert(any(SysControl.class));
    }

    @Test
    @DisplayName("initIfAbsent 当无记录时 insert 一条新的并返回")
    void initIfAbsent_whenNotExists_shouldInsert() {
        // Given
        when(sysControlMapper.countByCondition("EMP", null)).thenReturn(0L);
        when(sysControlMapper.insert(any(SysControl.class))).thenReturn(1);

        // When
        SysControl got = sysControlService.initIfAbsent("EMP", LocalDate.of(2099, 7, 1), "V_FRESH");

        // Then
        assertThat(got).isNotNull();
        assertThat(got.getScopeDim()).isEqualTo("EMP");
        assertThat(got.getLatestDataDate()).isEqualTo(LocalDate.of(2099, 7, 1));
        assertThat(got.getCurrentVersion()).isEqualTo("V_FRESH");
        assertThat(got.getIsValid()).isEqualTo(1);
        verify(sysControlMapper).insert(any(SysControl.class));
    }

    @Test
    @DisplayName("doSwitchVersion 遇到 DuplicateKeyException 时抛 PERF-40904")
    void doSwitchVersion_whenDupKey_shouldThrow40904() {
        // Given
        SysControl curr = SysControlTestDataBuilder.buildTest(
                "G03", "EMP", LocalDate.of(2099, 6, 1), "V_OLD", 1);
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(curr);
        when(sysControlMapper.updateIsValid(eq("TEST_SC_G03"), eq(0))).thenReturn(1);
        when(sysControlMapper.insert(any(SysControl.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim("EMP")
                .dataDate(LocalDate.of(2099, 8, 1))
                .newVersion("V_NEW")
                .reason("unit test")
                .operator("admin")
                .build();

        // When / Then
        assertThatThrownBy(() -> sysControlService.doSwitchVersion(cmd))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_VERSION_CONFLICT));
    }

    @Test
    @DisplayName("doSwitchVersion 正常切换: 旧版置 0 + 新版 insert")
    void doSwitchVersion_whenOk_shouldTransit() {
        // Given
        SysControl curr = SysControlTestDataBuilder.buildTest(
                "G04", "ORG", LocalDate.of(2099, 6, 1), "V_OLD", 1);
        when(sysControlMapper.selectByScopeAndValid("ORG")).thenReturn(curr);
        when(sysControlMapper.updateIsValid(anyString(), eq(0))).thenReturn(1);
        when(sysControlMapper.insert(any(SysControl.class))).thenReturn(1);

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim("ORG")
                .dataDate(LocalDate.of(2099, 8, 1))
                .newVersion("V_NEW")
                .reason("switch")
                .operator("admin")
                .build();

        // When
        SysControl result = sysControlService.doSwitchVersion(cmd);

        // Then
        assertThat(result.getCurrentVersion()).isEqualTo("V_NEW");
        assertThat(result.getIsValid()).isEqualTo(1);
        assertThat(result.getScopeDim()).isEqualTo("ORG");
        verify(sysControlMapper).updateIsValid("TEST_SC_G04", 0);
        verify(sysControlMapper).insert(any(SysControl.class));
    }

    @Test
    @DisplayName("doSwitchVersion 当 scopeDim 无当前版本时抛 PERF-40406")
    void doSwitchVersion_whenNoCurrent_shouldThrow40406() {
        // Given
        when(sysControlMapper.selectByScopeAndValid("CUST")).thenReturn(null);

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim("CUST")
                .dataDate(LocalDate.of(2099, 8, 1))
                .newVersion("V_NEW")
                .reason("switch")
                .operator("admin")
                .build();

        // When / Then
        assertThatThrownBy(() -> sysControlService.doSwitchVersion(cmd))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND));
    }

    @Test
    @DisplayName("listVersionHistory 调用 Mapper 并返回列表")
    void listVersionHistory_shouldDelegate() {
        // Given
        SysControl r1 = SysControlTestDataBuilder.buildTest(
                "G05", "EMP", LocalDate.of(2099, 9, 1), "V1", 0);
        when(sysControlMapper.listByScope("EMP", 10)).thenReturn(java.util.List.of(r1));

        // When
        java.util.List<SysControl> list = sysControlService.listVersionHistory("EMP", 10);

        // Then
        assertThat(list).hasSize(1).containsExactly(r1);
    }

    @Test
    @DisplayName("Task B7：doSwitchVersion 写入 publish 元数据（publishSource/publishBy/remark/publishTime）")
    void doSwitchVersion_records_publishMetadata() {
        // Given
        SysControl curr = SysControlTestDataBuilder.buildTest(
                "G07", "GLOBAL", LocalDate.of(2026, 3, 1), "v20260301", 1);
        when(sysControlMapper.selectByScopeAndValid("GLOBAL")).thenReturn(curr);
        when(sysControlMapper.updateIsValid(anyString(), eq(0))).thenReturn(1);
        when(sysControlMapper.insert(any(SysControl.class))).thenReturn(1);

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim("GLOBAL")
                .dataDate(LocalDate.of(2026, 4, 1))
                .newVersion("v20260401")
                .reason("季度末")
                .operator("admin")
                .publishSource("MANUAL")
                .build();

        // When
        SysControl result = sysControlService.doSwitchVersion(cmd);

        // Then
        assertThat(result.getPublishSource()).isEqualTo("MANUAL");
        assertThat(result.getPublishBy()).isEqualTo("admin");
        assertThat(result.getRemark()).isEqualTo("季度末");
        assertThat(result.getPublishTime()).isNotNull();
    }

    @Test
    @DisplayName("initIfAbsent 当有历史记录但无生效版本时返回最近一条 (历史 is_valid=0)")
    void initIfAbsent_whenHasHistoryButNoneValid_shouldReturnLatest() {
        // Given: 存在一条历史记录 (is_valid=0), 但无当前生效版本
        SysControl historical = SysControlTestDataBuilder.buildTest(
                "G06", "EMP", LocalDate.of(2099, 6, 1), "V_HISTORY", 0);
        when(sysControlMapper.countByCondition("EMP", null)).thenReturn(1L);
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(null);
        when(sysControlMapper.listByScope("EMP", 1)).thenReturn(java.util.List.of(historical));

        // When
        SysControl got = sysControlService.initIfAbsent("EMP", LocalDate.of(2099, 7, 1), "V_NEW");

        // Then: 不 insert 新记录, 返回历史最近一条
        assertThat(got).isSameAs(historical);
        verify(sysControlMapper, never()).insert(any(SysControl.class));
    }
}
