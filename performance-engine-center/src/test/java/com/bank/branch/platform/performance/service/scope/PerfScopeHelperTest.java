package com.bank.branch.platform.performance.service.scope;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerfScopeHelper 单元测试（V1.2 Task Q7.1 + V1.4 Task S1.2）.
 *
 * <p>V1.4 S1.2：ScopeColumns 扩第 5 字段 bizKeyCol；WORKFLOW_PARTICIPANT 分支
 * 从 V1.2 fail-close 切换到调 {@link WorkflowQueryApi#queryParticipatedBusinessKeys}
 * 生成 {@code <bizKeyCol> IN (...)} 片段。
 *
 * <p>本测试覆盖全部 7 种 {@link DataScopeType} 以及管理员/空上下文兜底：
 * <ul>
 *   <li>ALL：返回 empty Fragment（sql="" params={}），Mapper 不追加条件</li>
 *   <li>SELF_CREATED：sql="created_by = #{ownerEmpId}" + params.ownerEmpId</li>
 *   <li>SELF：sql="{selfCol} = #{ownerEmpId}" + params.ownerEmpId</li>
 *   <li>SELF_ASSIGNED：sql="{assigneeCol} = #{ownerEmpId}" + params.ownerEmpId</li>
 *   <li>ORG：sql="{ownerOrgCol} = #{ownerOrgCode}" + params.ownerOrgCode</li>
 *   <li>ORG_SUBTREE：sql="{ownerOrgCol} IN (&lt;subtree codes&gt;)" + 动态 IN 参数</li>
 *   <li>WORKFLOW_PARTICIPANT（V1.4 S1.2 实现）：调 WorkflowQueryApi 返回 businessKey
 *       集合 → sql="{bizKeyCol} IN (#{bizKey0}, #{bizKey1})"；
 *       businessKeys 空集/bizKeyCol null → fail-close "1=0"</li>
 *   <li>buildScopeContext 返回 null：降级为 "1=0" 防止越权</li>
 * </ul>
 *
 * <p>SQL 注入防护约束：ownerEmpId/ownerOrgCode/bizKey* 走 {@code #{}} 预编译
 * （Fragment.params），仅 sql 字段通过 {@code ${}} 注入，params 绝不出现在 sql 字段内。
 */
@ExtendWith(MockitoExtension.class)
class PerfScopeHelperTest {

    @Mock
    private BizScopeApi bizScopeApi;

    @Mock
    private WorkflowQueryApi workflowQueryApi;

    @InjectMocks
    private PerfScopeHelper helper;

    @Test
    @DisplayName("ALL → 返回空 Fragment (sql=\"\" params={})，Mapper 不追加任何条件")
    void whenScopeAll_returnsEmptyFragment() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.ALL, "admin", "HQ", Set.of(), BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        PerfScopeHelper.Fragment frag = helper.getFragment("admin", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "emp_id", "emp_id", "org_code", null));

        assertThat(frag.isEmpty()).isTrue();
        assertThat(frag.getSql()).isEmpty();
        assertThat(frag.getParams()).isEmpty();
    }

    @Test
    @DisplayName("SELF_CREATED → sql=\"created_by = #{ownerEmpId}\" + params.ownerEmpId=当前 empId")
    void whenScopeSelfCreated_returnsCreatedByFragment() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.SELF_CREATED, "USER_A", "BRANCH_01", Set.of(), BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        // createdByCol 指定为 "created_by"
        PerfScopeHelper.Fragment frag = helper.getFragment("USER_A", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "assignee_id", "created_by", "org_code", null));

        assertThat(frag.isEmpty()).isFalse();
        // ${scopeFragment} 注入 "created_by = #{scopeParams.ownerEmpId}"
        assertThat(frag.getSql()).isEqualTo("created_by = #{scopeParams.ownerEmpId}");
        assertThat(frag.getParams()).containsEntry("ownerEmpId", "USER_A");
    }

    @Test
    @DisplayName("SELF → sql=\"<selfCol> = #{ownerEmpId}\"，selfCol 来自 ScopeColumns")
    void whenScopeSelf_usesSelfColumn() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.SELF, "USER_B", "BRANCH_01", Set.of(), BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        PerfScopeHelper.Fragment frag = helper.getFragment("USER_B", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "assignee_id", "created_by", "org_code", null));

        assertThat(frag.getSql()).isEqualTo("emp_id = #{scopeParams.ownerEmpId}");
        assertThat(frag.getParams()).containsEntry("ownerEmpId", "USER_B");
    }

    @Test
    @DisplayName("SELF_ASSIGNED → sql=\"<assigneeCol> = #{ownerEmpId}\"")
    void whenScopeSelfAssigned_usesAssigneeColumn() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.SELF_ASSIGNED, "USER_C", "BRANCH_01", Set.of(), BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        PerfScopeHelper.Fragment frag = helper.getFragment("USER_C", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "assignee_id", "created_by", "org_code", null));

        assertThat(frag.getSql()).isEqualTo("assignee_id = #{scopeParams.ownerEmpId}");
        assertThat(frag.getParams()).containsEntry("ownerEmpId", "USER_C");
    }

    @Test
    @DisplayName("ORG → sql=\"<ownerOrgCol> = #{ownerOrgCode}\" + params.ownerOrgCode=mainOrgCode")
    void whenScopeOrg_returnsOrgCodeFragment() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.ORG, "USER_D", "BRANCH_01", Set.of(), BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        PerfScopeHelper.Fragment frag = helper.getFragment("USER_D", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "emp_id", "emp_id", "org_code", null));

        assertThat(frag.getSql()).isEqualTo("org_code = #{scopeParams.ownerOrgCode}");
        assertThat(frag.getParams()).containsEntry("ownerOrgCode", "BRANCH_01");
    }

    @Test
    @DisplayName("ORG_SUBTREE → sql=\"<ownerOrgCol> IN (<subtree keys>)\" + 展开 params.orgCode0..N")
    void whenScopeOrgSubtree_returnsInClause() {
        Set<String> subtree = Set.of("BRANCH_01", "BRANCH_01_S1", "BRANCH_01_S2");
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.ORG_SUBTREE, "USER_E", "BRANCH_01", subtree, BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        PerfScopeHelper.Fragment frag = helper.getFragment("USER_E", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "emp_id", "emp_id", "org_code", null));

        assertThat(frag.getSql())
                .startsWith("org_code IN (")
                .endsWith(")")
                .contains("#{scopeParams.orgCode0}")
                .contains("#{scopeParams.orgCode1}")
                .contains("#{scopeParams.orgCode2}");
        // 3 个 key 展开到 params，值与 subtree 一一对应
        assertThat(frag.getParams()).hasSize(3);
        assertThat(frag.getParams().values()).containsExactlyInAnyOrderElementsOf(subtree);
    }

    @Test
    @DisplayName("ORG_SUBTREE：subtree 为空集 → 降级为 1=0 防越权")
    void whenScopeOrgSubtreeEmpty_failClose() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.ORG_SUBTREE, "USER_F", "BRANCH_01", Set.of(), BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        PerfScopeHelper.Fragment frag = helper.getFragment("USER_F", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "emp_id", "emp_id", "org_code", null));

        // subtree 空集应 fail-close, 返回 "1=0" 让 Mapper 查无结果
        assertThat(frag.getSql()).isEqualTo("1=0");
        assertThat(frag.getParams()).isEmpty();
    }

    @Test
    @DisplayName("V1.4 S1.2：WORKFLOW_PARTICIPANT + processDefKeyPrefix → 调 WorkflowQueryApi 返回 businessKey IN 片段")
    void workflowParticipant_queriesWorkflowApi_andReturnsInClauseFragment() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.WORKFLOW_PARTICIPANT, "E001", "BRANCH_01", Set.of(),
                BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        // 显式传递 processDefKeyPrefix 由调用方决定（如 AllocAdjustService 传 "perf_alloc_adjust_"）
        when(workflowQueryApi.queryParticipatedBusinessKeys(
                eq("E001"), eq("perf_alloc_adjust_"), eq(180), eq(5000)))
                .thenReturn(new java.util.LinkedHashSet<>(java.util.List.of("BK_001", "BK_002")));

        PerfScopeHelper.Fragment frag = helper.getFragment(
                "E001", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns(null, null, null, null, "business_key"),
                "perf_alloc_adjust_");

        assertThat(frag.getSql())
                .startsWith("business_key IN (")
                .endsWith(")")
                .contains("#{scopeParams.bizKey0}")
                .contains("#{scopeParams.bizKey1}");
        assertThat(frag.getParams()).containsEntry("bizKey0", "BK_001");
        assertThat(frag.getParams()).containsEntry("bizKey1", "BK_002");
    }

    @Test
    @DisplayName("V1.4 S1.2：WORKFLOW_PARTICIPANT businessKeys 空集 → fail-close \"1=0\"")
    void workflowParticipant_emptyBusinessKeys_failClose() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.WORKFLOW_PARTICIPANT, "E002", "BRANCH_01", Set.of(),
                BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        when(workflowQueryApi.queryParticipatedBusinessKeys(any(), any(), any(), any()))
                .thenReturn(Set.of());

        PerfScopeHelper.Fragment frag = helper.getFragment(
                "E002", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns(null, null, null, null, "business_key"),
                "perf_alloc_adjust_");

        assertThat(frag.getSql()).isEqualTo("1=0");
        assertThat(frag.getParams()).isEmpty();
    }

    @Test
    @DisplayName("V1.4 S1.2：WORKFLOW_PARTICIPANT bizKeyCol=null → 防御 fail-close，不调 WorkflowQueryApi")
    void workflowParticipant_bizKeyColNull_failCloseDefensive() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.WORKFLOW_PARTICIPANT, "E003", "BRANCH_01", Set.of(),
                BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        PerfScopeHelper.Fragment frag = helper.getFragment(
                "E003", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns(null, null, null, null, null),
                "perf_alloc_adjust_");

        assertThat(frag.getSql()).isEqualTo("1=0");
        assertThat(frag.getParams()).isEmpty();
        // bizKeyCol=null 时本方法应直接 fail-close, 不触发 workflow 查询（避免浪费 Flowable IO）
        verify(workflowQueryApi, never()).queryParticipatedBusinessKeys(any(), any(), any(), any());
    }

    @Test
    @DisplayName("V1.4 S1.2：WORKFLOW_PARTICIPANT 走 4 参 getFragment（未传 prefix）→ fail-close，保持 V1.3 既有调用安全")
    void workflowParticipant_legacyFourArgOverload_failClose() {
        DataScopeContext ctx = new DataScopeContext(
                DataScopeType.WORKFLOW_PARTICIPANT, "E004", "BRANCH_01", Set.of(),
                BizType.PERF_CONFIG, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(ctx);

        // V1.3 既有 5 处调用用 4 参数 getFragment, 没传 workflow prefix → WORKFLOW_PARTICIPANT 分支
        // 仍保持 fail-close（不破坏既有行为）
        PerfScopeHelper.Fragment frag = helper.getFragment(
                "E004", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "emp_id", "emp_id", "org_code", "business_key"));

        assertThat(frag.getSql()).isEqualTo("1=0");
        assertThat(frag.getParams()).isEmpty();
        verify(workflowQueryApi, never()).queryParticipatedBusinessKeys(any(), any(), any(), any());
    }

    @Test
    @DisplayName("buildScopeContext 返回 null → 降级为 1=0（fail-close）")
    void whenContextNull_failClose() {
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(null);

        PerfScopeHelper.Fragment frag = helper.getFragment("USER_H", BizType.PERF_CONFIG, BizAction.LIST,
                new PerfScopeHelper.ScopeColumns("emp_id", "emp_id", "emp_id", "org_code", null));

        assertThat(frag.getSql()).isEqualTo("1=0");
        assertThat(frag.getParams()).isEmpty();
    }

    @Test
    @DisplayName("Fragment.isEmpty(): sql 为 null 或空字符串时返回 true")
    void fragmentEmptyCheck() {
        PerfScopeHelper.Fragment empty = PerfScopeHelper.Fragment.empty();
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.getSql()).isEmpty();
        assertThat(empty.getParams()).isEmpty();
    }
}
