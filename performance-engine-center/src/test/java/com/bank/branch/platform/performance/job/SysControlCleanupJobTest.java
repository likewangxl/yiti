package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.mapper.SysControlMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysControlCleanupJob 单元测试（Task Q5.1 Red）.
 *
 * <p>职责：每日 03:00（默认 cron）按 scope_dim 保留最近 N=12 个历史版本（is_valid=0），
 * 多余的历史版本批量硬删除；当前生效行（is_valid=1）<strong>永不删除</strong>。
 *
 * <p>Red 阶段：目标 Job 类 {@link SysControlCleanupJob} 以及 Mapper 新增方法
 * {@code selectScopeDims() / selectOldVersionIdsForCleanup(scope,keep) / deleteByIds(ids)}
 * 尚未实现，编译失败即视为 Red.
 */
@ExtendWith(MockitoExtension.class)
class SysControlCleanupJobTest {

    @Mock
    private SysControlMapper sysControlMapper;

    @InjectMocks
    private SysControlCleanupJob job;

    @BeforeEach
    void setUp() {
        // @Value("${perf.job.sys-control-cleanup.keep-count:12}") 在纯 Mockito 场景不会被注入，
        // 测试中通过反射显式设置为 12 以模拟 Spring 属性绑定后的状态.
        ReflectionTestUtils.setField(job, "keepCount", 12);
    }

    @Test
    @DisplayName("run：每个 scope_dim 保留最近 12 个历史版本，超出的全部硬删")
    void run_keepsRecent12Versions_deletesOverflow() {
        // 预置：两个 scope_dim，都存在多余历史 id
        when(sysControlMapper.selectScopeDims())
                .thenReturn(List.of("EMP", "ORG"));
        // EMP 维度超出 keep=12 的要删 3 条
        List<String> empOverflow = List.of("SC_EMP_OLD_1", "SC_EMP_OLD_2", "SC_EMP_OLD_3");
        when(sysControlMapper.selectOldVersionIdsForCleanup(eq("EMP"), eq(12)))
                .thenReturn(empOverflow);
        when(sysControlMapper.deleteByIds(empOverflow)).thenReturn(empOverflow.size());
        // ORG 维度超出 keep=12 的要删 1 条
        List<String> orgOverflow = List.of("SC_ORG_OLD_1");
        when(sysControlMapper.selectOldVersionIdsForCleanup(eq("ORG"), eq(12)))
                .thenReturn(orgOverflow);
        when(sysControlMapper.deleteByIds(orgOverflow)).thenReturn(orgOverflow.size());

        int deleted = job.run();

        // 硬删应分别按 scope 批量调用
        verify(sysControlMapper, times(1)).deleteByIds(empOverflow);
        verify(sysControlMapper, times(1)).deleteByIds(orgOverflow);
        // 总删除数 = 3 + 1
        assertThat(deleted).isEqualTo(4);
    }

    @Test
    @DisplayName("run：单个 scope_dim 异常不影响其它 scope")
    void run_oneScopeFails_othersContinue() {
        when(sysControlMapper.selectScopeDims())
                .thenReturn(List.of("EMP", "ORG"));
        when(sysControlMapper.selectOldVersionIdsForCleanup(eq("EMP"), anyInt()))
                .thenThrow(new RuntimeException("boom EMP"));
        when(sysControlMapper.selectOldVersionIdsForCleanup(eq("ORG"), anyInt()))
                .thenReturn(List.of("SC_ORG_OLD"));
        when(sysControlMapper.deleteByIds(List.of("SC_ORG_OLD"))).thenReturn(1);

        int deleted = job.run();

        // EMP 异常被吞掉，ORG 仍然被处理
        verify(sysControlMapper).deleteByIds(List.of("SC_ORG_OLD"));
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("run：没有多余历史版本时不调 deleteByIds")
    void run_noOverflow_noDelete() {
        when(sysControlMapper.selectScopeDims())
                .thenReturn(List.of("EMP"));
        when(sysControlMapper.selectOldVersionIdsForCleanup(anyString(), anyInt()))
                .thenReturn(new ArrayList<>());

        int deleted = job.run();

        verify(sysControlMapper, never()).deleteByIds(anyList());
        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("run：scope_dim 列表为空时快速返回 0，不再调查询/删除")
    void run_noScopeDim_returnsZero() {
        when(sysControlMapper.selectScopeDims()).thenReturn(List.of());

        int deleted = job.run();

        verify(sysControlMapper, never()).selectOldVersionIdsForCleanup(anyString(), anyInt());
        verify(sysControlMapper, never()).deleteByIds(anyList());
        assertThat(deleted).isZero();
    }

    /** 辅助：构造贫血 SysControl（Q5.1 测试暂不使用，仅保留以兼容未来扩展）. */
    @SuppressWarnings("unused")
    private SysControl sc(String id, String scope, int isValid) {
        SysControl s = new SysControl();
        s.setId(id);
        s.setScopeDim(scope);
        s.setLatestDataDate(LocalDate.now());
        s.setCurrentVersion("V_" + id);
        s.setIsValid(isValid);
        return s;
    }
}
