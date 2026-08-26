package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 原业绩所属机构负责人会签名单解析（TDD Red→Green）。
 *
 * <p>需求：original_owner_approve 节点审批人由「原业绩分配人本人」改为「原业绩所属
 * 2 级机构的机构负责人(BRANCH_HEAD)」——每个原分配人主机构沿 P_ID 上溯至 2 级机构
 * （与公司部/零售部同级；主机构本身 2 级则就地），机构去重后取各机构 BRANCH_HEAD
 * 持有者并集（跨分行=多机构负责人会签）；任一环节缺失在发起时 fail-fast，
 * 避免流程行至该节点无人可批卡死。</p>
 */
class AllocAdjustServiceOrgLeaderResolveTest {

    private static OrgDTO org(String code, Integer level, String parent) {
        OrgDTO o = new OrgDTO();
        o.setOrgCode(code);
        o.setOrgLevel(level);
        o.setParentOrgCode(parent);
        return o;
    }

    private static OrgDTO org(String code, Integer level, String parent, String name) {
        OrgDTO o = org(code, level, parent);
        o.setOrgName(name);
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeResolve(AllocAdjustService svc, List<String> empIds) throws Exception {
        Method m = AllocAdjustService.class.getDeclaredMethod("resolveOriginalOwnerOrgLeaderEmpIds", List.class);
        m.setAccessible(true);
        try {
            return (List<String>) m.invoke(svc, empIds);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    // ---------- 上溯定位 2 级机构 ----------

    @Test
    void level3Owner_climbsToLevel2Branch_andTakesBranchHead() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        UserApi userApi = mock(UserApi.class);
        // 原分配人 E1 主机构=金台支行(330, level3, 父=宝鸡分行128)
        when(orgApi.getUserMainOrg("E1")).thenReturn(org("330", 3, "128"));
        when(orgApi.getOrg("128")).thenReturn(org("128", 2, "1"));
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "128")).thenReturn(List.of("LDR_BJ"));

        AllocAdjustService svc = AllocAdjustService.forOrgLeaderTest(null, null, userApi, orgApi);
        assertThat(invokeResolve(svc, List.of("E1"))).containsExactly("LDR_BJ");
    }

    @Test
    void level2Owner_staysAtOwnOrg() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        UserApi userApi = mock(UserApi.class);
        // 原分配人本身在 2 级机构（如总行公司客户一部 98）→ 就地取该机构负责人
        when(orgApi.getUserMainOrg("E2")).thenReturn(org("98", 2, "1"));
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "98")).thenReturn(List.of("LDR_98"));

        AllocAdjustService svc = AllocAdjustService.forOrgLeaderTest(null, null, userApi, orgApi);
        assertThat(invokeResolve(svc, List.of("E2"))).containsExactly("LDR_98");
    }

    @Test
    void multiOwnersAcrossBranches_unionDeduped_andEachOrgQueriedOnce() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        UserApi userApi = mock(UserApi.class);
        // E1/E11 同属宝鸡分行(128)下两支行；E3 属另一分行 200 直属
        when(orgApi.getUserMainOrg("E1")).thenReturn(org("330", 3, "128"));
        when(orgApi.getUserMainOrg("E11")).thenReturn(org("170", 3, "128"));
        when(orgApi.getUserMainOrg("E3")).thenReturn(org("200", 2, "1"));
        when(orgApi.getOrg("128")).thenReturn(org("128", 2, "1"));
        // 两机构负责人有交集 LDR_X → 结果并集去重
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "128")).thenReturn(List.of("LDR_BJ", "LDR_X"));
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "200")).thenReturn(List.of("LDR_X", "LDR_200"));

        AllocAdjustService svc = AllocAdjustService.forOrgLeaderTest(null, null, userApi, orgApi);
        assertThat(invokeResolve(svc, List.of("E1", "E11", "E3")))
                .containsExactly("LDR_BJ", "LDR_X", "LDR_200");
        // 同一 2 级机构（128）只按机构查一次负责人
        verify(userApi, times(1)).getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "128");
    }

    // ---------- fail-fast ----------

    @Test
    void orgWithoutBranchHead_failsFast() {
        OrgApi orgApi = mock(OrgApi.class);
        UserApi userApi = mock(UserApi.class);
        when(orgApi.getUserMainOrg("E1")).thenReturn(org("330", 3, "128", "金台支行"));
        when(orgApi.getOrg("128")).thenReturn(org("128", 2, "1", "宝鸡分行"));
        when(userApi.getEmpIdsByRoleCodeAndOrg(anyString(), anyString())).thenReturn(List.of());

        AllocAdjustService svc = AllocAdjustService.forOrgLeaderTest(null, null, userApi, orgApi);
        assertThatThrownBy(() -> invokeResolve(svc, List.of("E1")))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("宝鸡分行")
                .hasMessageNotContaining("128");
    }

    @Test
    void ownerWithoutMainOrg_failsFast() {
        OrgApi orgApi = mock(OrgApi.class);
        UserApi userApi = mock(UserApi.class);
        when(orgApi.getUserMainOrg("E9")).thenReturn(null);

        AllocAdjustService svc = AllocAdjustService.forOrgLeaderTest(null, null, userApi, orgApi);
        assertThatThrownBy(() -> invokeResolve(svc, List.of("E9")))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("E9");
    }

    @Test
    void targetWithoutMainOrg_failsFastWithTargetLabel() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        UserApi userApi = mock(UserApi.class);

        AllocAdjustService svc = AllocAdjustService.forOrgLeaderTest(null, null, userApi, orgApi);
        assertThatThrownBy(() -> invokeResolveTarget(svc, List.of("E9")))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("分配对象")
                .hasMessageContaining("E9");
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeResolveTarget(AllocAdjustService svc, List<String> empIds) throws Exception {
        Method m = AllocAdjustService.class.getDeclaredMethod("resolveAllocationTargetOrgLeaderEmpIds", List.class);
        m.setAccessible(true);
        try {
            return (List<String>) m.invoke(svc, empIds);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void cannotReachLevel2_failsFast() {
        OrgApi orgApi = mock(OrgApi.class);
        UserApi userApi = mock(UserApi.class);
        // 脏数据：level3 支行的父机构直接是 level1 总行，上溯穿不过 2 级
        when(orgApi.getUserMainOrg("E1")).thenReturn(org("330", 3, "1"));
        when(orgApi.getOrg("1")).thenReturn(org("1", 1, null));

        AllocAdjustService svc = AllocAdjustService.forOrgLeaderTest(null, null, userApi, orgApi);
        assertThatThrownBy(() -> invokeResolve(svc, List.of("E1")))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("E1");
    }

    // ---------- 起流程变量装配 ----------

    @Test
    void startApprovalWorkflow_putsOrgLeaderVarBesidesOriginalOwners() throws Exception {
        WorkflowApi workflowApi = mock(WorkflowApi.class);
        CustAllocRelationMapper allocRelationMapper = mock(CustAllocRelationMapper.class);
        OrgApi orgApi = mock(OrgApi.class);
        UserApi userApi = mock(UserApi.class);

        when(workflowApi.resolveDesignerProcDefKey("alloc_corp_designer")).thenReturn("DSN_alloc_corp_designer");
        WorkflowLaunchResp resp = new WorkflowLaunchResp();
        resp.setProcessInstanceId("PID1");
        when(workflowApi.startProcess(any())).thenReturn(resp);
        // 单值兜底走会签名单首位（无活跃分配关系）
        when(allocRelationMapper.selectCurrentByCustAndBiz(anyString(), anyString(), any())).thenReturn(List.of());
        // 发起机构 98（2级，通过 startOrgLevel 前置校验）；原分配人 E1 → 330 → 128
        when(orgApi.getOrg("98")).thenReturn(org("98", 2, "1"));
        when(orgApi.getUserMainOrg("E1")).thenReturn(org("330", 3, "128"));
        when(orgApi.getOrg("128")).thenReturn(org("128", 2, "1"));
        when(userApi.getEmpIdsByRoleCodeAndOrg("BRANCH_HEAD", "128")).thenReturn(List.of("LDR_BJ"));

        SubmitAllocAdjustCmd cmd = new SubmitAllocAdjustCmd();
        cmd.setCustType("CORP");
        cmd.setBizKind("CORP_LOAN");
        cmd.setApplicant("APPLICANT1");
        cmd.setOwnerOrgId("98");

        AllocAdjustService svc = AllocAdjustService.forOrgLeaderTest(workflowApi, allocRelationMapper, userApi, orgApi);
        Method m = AllocAdjustService.class.getDeclaredMethod("startApprovalWorkflow",
                String.class, String.class, String.class, SubmitAllocAdjustCmd.class, List.class);
        m.setAccessible(true);
        m.invoke(svc, "A1", "NO1", "C001", cmd, List.of("E1"));

        ArgumentCaptor<StartProcessCmd> captor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(captor.capture());
        // 新变量：original_owner_approve 节点候选（DB conf 切换到该变量后生效）
        assertThat(captor.getValue().getVariables().get("originalOwnerOrgLeaderEmpIds"))
                .isEqualTo(List.of("LDR_BJ"));
        // 旧变量保留（进度展示/审计仍在读）
        assertThat(captor.getValue().getVariables().get("originalOwnerEmpIds")).isEqualTo(List.of("E1"));
    }
}
