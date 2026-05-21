package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.bank.branch.platform.performance.support.AllocTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AllocRelationService Task Q7.2 数据范围注入单元测试.
 *
 * <p>验证 {@code listCustomersByEmpWithScope} 方法按 4 种 {@link DataScopeType}
 * 经 {@link PerfScopeHelper} 生成 scopeFragment + scopeParams 并传入 Mapper:
 * <ul>
 *   <li>ALL → scopeFragment=空 / scopeParams=空</li>
 *   <li>SELF_CREATED → scopeFragment="created_by = #{scopeParams.ownerEmpId}"</li>
 *   <li>SELF → scopeFragment="emp_id = #{scopeParams.ownerEmpId}"</li>
 *   <li>无权限（ctx=null）→ fail-close "1=0"</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AllocRelationServiceScopeTest {

    @Mock
    private CustAllocRelationMapper allocMapper;

    @Mock
    private SysControlService sysControlService;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private BizScopeApi bizScopeApi;

    // Q7.2: 真正的 helper 实例（用 bizScopeApi mock 驱动）
    private PerfScopeHelper perfScopeHelper;

    @InjectMocks
    private AllocRelationService service;

    private void wireScopeHelper() {
        this.perfScopeHelper = new PerfScopeHelper(bizScopeApi, null);
        // V1.8 去 Redis：构造函数移除 RedisTemplate 参数
        this.service = new AllocRelationService(
                allocMapper, sysControlService, currentUserApi, bizScopeApi, perfScopeHelper);
    }

    @Test
    @DisplayName("listCustomersByEmpWithScope: ALL → scopeFragment=空, scopeParams=空")
    void whenScopeAll_passesEmptyFragment() {
        wireScopeHelper();
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.buildScopeContext(eq("admin"), eq(BizType.PERF_CONFIG), eq(BizAction.LIST)))
                .thenReturn(new DataScopeContext(
                        DataScopeType.ALL, "admin", "HQ", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(allocMapper.selectByEmpAndBizWithScope(
                anyString(), any(), any(LocalDate.class), eq(""), any()))
                .thenReturn(Collections.emptyList());

        service.listCustomersByEmpWithScope("EMP_X", "LOAN");

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(allocMapper).selectByEmpAndBizWithScope(
                eq("EMP_X"), eq("LOAN"), any(LocalDate.class), sqlCap.capture(), paramsCap.capture());
        assertThat(sqlCap.getValue()).isEmpty();
        assertThat(paramsCap.getValue()).isEmpty();
    }

    @Test
    @DisplayName("listCustomersByEmpWithScope: SELF → scopeFragment=\"emp_id = #{scopeParams.ownerEmpId}\"")
    void whenScopeSelf_passesEmpIdFragment() {
        wireScopeHelper();
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_SELF");
        when(bizScopeApi.buildScopeContext(eq("USER_SELF"), eq(BizType.PERF_CONFIG), eq(BizAction.LIST)))
                .thenReturn(new DataScopeContext(
                        DataScopeType.SELF, "USER_SELF", "BRANCH_01", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        CustAllocRelation rel = AllocTestDataBuilder.relation("AR1", "USER_SELF", "LOAN");
        when(allocMapper.selectByEmpAndBizWithScope(
                eq("USER_SELF"), any(), any(LocalDate.class), anyString(), any()))
                .thenReturn(List.of(rel));

        List<CustAllocRelation> result = service.listCustomersByEmpWithScope("USER_SELF", null);

        assertThat(result).hasSize(1);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(allocMapper).selectByEmpAndBizWithScope(
                eq("USER_SELF"), eq(null), any(LocalDate.class), sqlCap.capture(), paramsCap.capture());
        assertThat(sqlCap.getValue()).isEqualTo("emp_id = #{scopeParams.ownerEmpId}");
        assertThat(paramsCap.getValue()).containsEntry("ownerEmpId", "USER_SELF");
    }

    @Test
    @DisplayName("listCustomersByEmpWithScope: SELF_CREATED → scopeFragment=\"created_by = #{scopeParams.ownerEmpId}\"")
    void whenScopeSelfCreated_passesCreatedByFragment() {
        wireScopeHelper();
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_CRT");
        when(bizScopeApi.buildScopeContext(any(), any(), any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.SELF_CREATED, "USER_CRT", "BRANCH_02", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(allocMapper.selectByEmpAndBizWithScope(
                anyString(), any(), any(LocalDate.class), anyString(), any()))
                .thenReturn(Collections.emptyList());

        service.listCustomersByEmpWithScope("USER_CRT", "LOAN");

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(allocMapper).selectByEmpAndBizWithScope(
                eq("USER_CRT"), eq("LOAN"), any(LocalDate.class), sqlCap.capture(), any());
        assertThat(sqlCap.getValue()).isEqualTo("created_by = #{scopeParams.ownerEmpId}");
    }

    @Test
    @DisplayName("listCustomersByEmpWithScope: 无权限 ctx=null → 传 \"1=0\" fail-close")
    void whenNoContext_passesFailClose() {
        wireScopeHelper();
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_NO");
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(null);
        when(allocMapper.selectByEmpAndBizWithScope(
                anyString(), any(), any(LocalDate.class), eq("1=0"), any()))
                .thenReturn(Collections.emptyList());

        List<CustAllocRelation> result = service.listCustomersByEmpWithScope("USER_NO", null);

        assertThat(result).isEmpty();
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(allocMapper).selectByEmpAndBizWithScope(
                eq("USER_NO"), eq(null), any(LocalDate.class), sqlCap.capture(), any());
        assertThat(sqlCap.getValue()).isEqualTo("1=0");
    }
}
