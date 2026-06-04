package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * UserFacade#getCandidateGroupKeys 单元测试。
 * <p>
 * 验证「按 empId 计算工作流候选组」能力：不依赖登录态 ThreadLocal，
 * 按传入员工号查库组装 ROLE:/USER:/ORG: 三类前缀，供 SOAP 网关等无会话链路使用。
 * 口径须与 AuthService.login 构建 candidateGroupKeys 的逻辑一致。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserFacadeCandidateGroupKeysTest {

    @Mock private UserMapper userMapper;
    @Mock private UserOrgMapper userOrgMapper;
    @Mock private OrgMapper orgMapper;
    @Mock private UserRoleMapper userRoleMapper;

    @InjectMocks private UserFacade userFacade;

    private PtRole role(String code) {
        PtRole r = new PtRole();
        r.setRoleCode(code);
        return r;
    }

    @Test
    @DisplayName("按 empId 组装 ROLE:/USER:/ORG: 三类前缀（口径对齐登录候选组）")
    void getCandidateGroupKeys_buildsRoleUserOrgPrefixes() {
        when(userRoleMapper.selectRolesByUserId("E10001"))
                .thenReturn(List.of(role("R_RM"), role("BRANCH_HEAD")));
        ExtUserOrg org = new ExtUserOrg();
        org.setOrgCode("02901000");
        when(userOrgMapper.selectByUserId("E10001")).thenReturn(org);

        Set<String> keys = userFacade.getCandidateGroupKeys("E10001");

        assertThat(keys).containsExactlyInAnyOrder(
                "ROLE:R_RM", "ROLE:BRANCH_HEAD", "USER:E10001", "ORG:02901000");
    }

    @Test
    @DisplayName("无主机构时省略 ORG: 前缀，仍含 ROLE:/USER:")
    void getCandidateGroupKeys_noMainOrg_omitsOrgPrefix() {
        when(userRoleMapper.selectRolesByUserId("E10002")).thenReturn(List.of(role("R_RM")));
        when(userOrgMapper.selectByUserId("E10002")).thenReturn(null);

        Set<String> keys = userFacade.getCandidateGroupKeys("E10002");

        assertThat(keys).containsExactlyInAnyOrder("ROLE:R_RM", "USER:E10002");
    }

    @Test
    @DisplayName("无角色但有主机构：仅含 USER:/ORG:")
    void getCandidateGroupKeys_noRole_stillHasUserAndOrg() {
        when(userRoleMapper.selectRolesByUserId("E10003")).thenReturn(List.of());
        ExtUserOrg org = new ExtUserOrg();
        org.setOrgCode("02901000");
        when(userOrgMapper.selectByUserId("E10003")).thenReturn(org);

        Set<String> keys = userFacade.getCandidateGroupKeys("E10003");

        assertThat(keys).containsExactlyInAnyOrder("USER:E10003", "ORG:02901000");
    }

    @Test
    @DisplayName("empId 为空/空串返回空集，且不触发任何 DB 查询")
    void getCandidateGroupKeys_blankEmpId_returnsEmpty() {
        assertThat(userFacade.getCandidateGroupKeys(null)).isEmpty();
        assertThat(userFacade.getCandidateGroupKeys("")).isEmpty();
    }
}
