package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.BizScopeRespDTO;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import com.bank.branch.platform.auth.mapper.RoleBizScopeMapper;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BizScopeServiceTest {

    @Mock RoleBizScopeMapper roleBizScopeMapper;
    @Mock RoleMapper roleMapper;
    @Mock PermissionCacheService cacheService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks BizScopeService bizScopeService;

    @Test
    void resolveScope_singleRoleReturnsConfiguredScope() {
        PtRoleBizScope scope = makeScope("S_RM_LEAD", "R_RM", "LEAD", "SELF_CREATED");
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_RM"));
        when(cacheService.getBizScopesByRoleId("R_RM")).thenReturn(List.of(scope));

        DataScopeType result = bizScopeService.resolveScope("E001", BizType.LEAD);

        assertThat(result).isEqualTo(DataScopeType.SELF_CREATED);
    }

    @Test
    void resolveScope_multipleRolesTakesHigherPriority() {
        // CUST_MANAGER has ORG_SUBTREE; CORP_DEPT has ALL => result should be ALL
        PtRoleBizScope scope1 = makeScope("S1", "R_RM", "CUSTOMER", "ORG_SUBTREE");
        PtRoleBizScope scope2 = makeScope("S2", "R_CORP", "CUSTOMER", "ALL");
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_RM", "R_CORP"));
        when(cacheService.getBizScopesByRoleId("R_RM")).thenReturn(List.of(scope1));
        when(cacheService.getBizScopesByRoleId("R_CORP")).thenReturn(List.of(scope2));

        DataScopeType result = bizScopeService.resolveScope("E001", BizType.CUSTOMER);

        assertThat(result).isEqualTo(DataScopeType.ALL);
    }

    @Test
    void resolveScope_noBizTypeScopeThrowsPermissionDenied() {
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_RM"));
        when(cacheService.getBizScopesByRoleId("R_RM")).thenReturn(List.of()); // 没有 LOAN 配置

        assertThatThrownBy(() -> bizScopeService.resolveScope("E001", BizType.LOAN))
            .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void getUserBizScopes_shouldMergeAllRoleScopes() {
        PtRoleBizScope leadScope = makeScope("S1", "R_RM", "LEAD", "SELF_CREATED");
        PtRoleBizScope reportScope = makeScope("S2", "R_RM", "REPORT", "SELF");
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_RM"));
        when(cacheService.getBizScopesByRoleId("R_RM")).thenReturn(List.of(leadScope, reportScope));

        Map<BizType, DataScopeType> scopes = bizScopeService.getUserBizScopes("E001");

        assertThat(scopes).containsEntry(BizType.LEAD, DataScopeType.SELF_CREATED);
        assertThat(scopes).containsEntry(BizType.REPORT, DataScopeType.SELF);
    }

    @Test
    void checkWritePermission_allScopeAlwaysReturnsTrue() {
        PtRoleBizScope scope = makeScope("S1", "R_ADMIN", "CUSTOMER", "ALL");
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_ADMIN"));
        when(cacheService.getBizScopesByRoleId("R_ADMIN")).thenReturn(List.of(scope));

        boolean result = bizScopeService.checkWritePermission("E001", BizType.CUSTOMER, "ORG_OTHER", "E999");

        assertThat(result).isTrue();
    }

    @Test
    void checkWritePermission_selfCreatedScopeMatchesCreatedBy() {
        PtRoleBizScope scope = makeScope("S1", "R_RM", "LEAD", "SELF_CREATED");
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_RM"));
        when(cacheService.getBizScopesByRoleId("R_RM")).thenReturn(List.of(scope));

        assertThat(bizScopeService.checkWritePermission("E001", BizType.LEAD, "ORG001", "E001")).isTrue();
        assertThat(bizScopeService.checkWritePermission("E001", BizType.LEAD, "ORG001", "E999")).isFalse();
    }

    @Test
    void saveBizScope_shouldThrowWhenRoleNotFound() {
        when(roleMapper.selectByRoleId("NONE")).thenReturn(null);
        assertThatThrownBy(() -> bizScopeService.saveBizScope("NONE", "LEAD", "SELF_CREATED", "原因"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40401"));
    }

    @Test
    void saveBizScope_shouldUpsertAndEvictCache() {
        PtRole role = new PtRole();
        role.setRoleId("R_RM");
        role.setRoleCode("CUST_MANAGER");
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(role);
        when(roleBizScopeMapper.selectByRoleIdAndBizType("R_RM", "LEAD")).thenReturn(null);
        when(roleBizScopeMapper.insert(any(PtRoleBizScope.class))).thenReturn(1);

        BizScopeRespDTO dto = bizScopeService.saveBizScope("R_RM", "LEAD", "SELF_CREATED", "原因");

        assertThat(dto.getBizType()).isEqualTo("LEAD");
        assertThat(dto.getDataScope()).isEqualTo("SELF_CREATED");
        verify(cacheService).evictBizScopeCache("R_RM");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void deleteBizScope_shouldThrowWhenNotFound() {
        when(roleBizScopeMapper.selectById("NONE")).thenReturn(null);
        assertThatThrownBy(() -> bizScopeService.deleteBizScope("NONE", "原因"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40405"));
    }

    @Test
    void deleteBizScope_shouldDeleteAndEvictCache() {
        PtRoleBizScope scope = makeScope("S1", "R_RM", "LEAD", "SELF_CREATED");
        when(roleBizScopeMapper.selectById("S1")).thenReturn(scope);
        when(roleBizScopeMapper.deleteById("S1")).thenReturn(1);

        bizScopeService.deleteBizScope("S1", "原因");

        verify(roleBizScopeMapper).deleteById("S1");
        verify(cacheService).evictBizScopeCache("R_RM");
    }

    private PtRoleBizScope makeScope(String id, String roleId, String bizType, String dataScope) {
        PtRoleBizScope s = new PtRoleBizScope();
        s.setId(id);
        s.setRoleId(roleId);
        s.setBizType(bizType);
        s.setDataScope(dataScope);
        s.setRecordStatus(0);
        return s;
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    @Test
    void checkWritePermission_defaultScopeReturnsFalse() {
        // ORG scope 在简化实现中返回 false
        PtRoleBizScope scope = makeScope("S1", "R_RM", "CUSTOMER", "ORG");
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_RM"));
        when(cacheService.getBizScopesByRoleId("R_RM")).thenReturn(List.of(scope));

        assertThat(bizScopeService.checkWritePermission("E001", BizType.CUSTOMER, "ORG001", "E001")).isFalse();
    }

    @Test
    void checkWritePermission_noBizTypeConfigReturnsFalse() {
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_RM"));
        when(cacheService.getBizScopesByRoleId("R_RM")).thenReturn(List.of());

        assertThat(bizScopeService.checkWritePermission("E001", BizType.LOAN, "ORG001", "E001")).isFalse();
    }

    @Test
    void saveBizScope_existingScope_shouldUpdate() {
        PtRole role = new PtRole();
        role.setRoleId("R_RM");
        role.setRoleCode("CUST_MANAGER");
        role.setRoleChName("客户经理");
        when(roleMapper.selectByRoleId("R_RM")).thenReturn(role);

        PtRoleBizScope existing = makeScope("S1", "R_RM", "LEAD", "SELF_CREATED");
        when(roleBizScopeMapper.selectByRoleIdAndBizType("R_RM", "LEAD")).thenReturn(existing);

        BizScopeRespDTO dto = bizScopeService.saveBizScope("R_RM", "LEAD", "ALL", "升级权限");

        assertThat(dto.getDataScope()).isEqualTo("ALL");
        verify(roleBizScopeMapper).updateById(argThat((PtRoleBizScope s) -> "ALL".equals(s.getDataScope())));
        verify(roleBizScopeMapper, never()).insert(any(PtRoleBizScope.class));
    }

    @Test
    void resolveScope_disabledScopesShouldBeIgnored() {
        PtRoleBizScope active = makeScope("S1", "R_RM", "LEAD", "SELF_CREATED");
        PtRoleBizScope disabled = makeScope("S2", "R_RM", "LEAD", "ALL");
        disabled.setRecordStatus(1); // 已禁用
        when(cacheService.getRoleIdsByEmpId("E001")).thenReturn(java.util.Set.of("R_RM"));
        when(cacheService.getBizScopesByRoleId("R_RM")).thenReturn(List.of(active, disabled));

        DataScopeType result = bizScopeService.resolveScope("E001", BizType.LEAD);

        assertThat(result).isEqualTo(DataScopeType.SELF_CREATED);
    }

    @Test
    void listByPage_shouldDelegateToMapper() {
        when(roleBizScopeMapper.selectByPage(null, null, 0, 20)).thenReturn(List.of());
        when(roleBizScopeMapper.countByPage(null, null)).thenReturn(0L);

        PageResult<BizScopeRespDTO> result = bizScopeService.listByPage(null, null, 1, 20);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }
}
