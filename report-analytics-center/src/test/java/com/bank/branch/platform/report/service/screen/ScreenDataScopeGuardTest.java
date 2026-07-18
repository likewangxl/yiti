package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 大屏取数 DATA_SCOPE 行级权限守卫单测（spec 2026-07-17 §4）.
 *
 * <p>判定矩阵：七类 scope ×（empId/orgCode 合法/越权组合）、GLOBAL 数据源省级判定、
 * 无主体参数 SUBJECT 数据源按 GLOBAL 规则防绕过、fail-close 兜底。
 * BizScopeApi/CurrentUserApi/OrgApi 全部 mock；多角色并集在 auth 端
 * {@code BizScopeApi.resolveScope} 已实现（取最大边界），Guard 侧守护"消费该并集入口"。
 */
@ExtendWith(MockitoExtension.class)
class ScreenDataScopeGuardTest {

    private static final String CUR_EMP = "E001";
    private static final String MAIN_ORG = "ORG_A";

    @Mock private BizScopeApi bizScopeApi;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private OrgApi orgApi;

    private ScreenDataScopeGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ScreenDataScopeGuard(bizScopeApi, currentUserApi, orgApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn(CUR_EMP);
        lenient().when(currentUserApi.getCurrentOrgCode()).thenReturn(MAIN_ORG);
    }

    // ===== 工具 =====

    private void scope(DataScopeType t) {
        when(bizScopeApi.resolveScope(CUR_EMP, BizType.REPORT)).thenReturn(t);
    }

    /** 消费主体参数的 SUBJECT 数据源（宽表引导式，无 aggregation） */
    private RptScreenDatasource subjectDs() {
        return ds("WIDE_TABLE", "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\","
                + "\"subjectParam\":\"empId\",\"metrics\":[{\"metricCode\":\"M_0001\",\"slot\":3}]}");
    }

    private RptScreenDatasource ds(String sourceKind, String configJson) {
        RptScreenDatasource e = new RptScreenDatasource();
        e.setId(1L);
        e.setSourceKind(sourceKind);
        e.setConfigJson(configJson);
        e.setStatus("ACTIVE");
        return e;
    }

    private ScreenDataReqDTO req(String empId, String orgCode) {
        ScreenDataReqDTO r = new ScreenDataReqDTO();
        r.setDsId(1L);
        Map<String, String> ctx = new HashMap<>();
        if (empId != null) {
            ctx.put("empId", empId);
        }
        if (orgCode != null) {
            ctx.put("orgCode", orgCode);
        }
        r.setContextParams(ctx);
        return r;
    }

    private OrgDTO org(String orgCode, Integer level, String parent) {
        OrgDTO o = new OrgDTO();
        o.setOrgCode(orgCode);
        o.setOrgLevel(level);
        o.setParentOrgCode(parent);
        return o;
    }

    private void assertDenied(RptScreenDatasource d, ScreenDataReqDTO r) {
        assertThatThrownBy(() -> guard.check(d, r))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43013");
    }

    private void assertPassed(RptScreenDatasource d, ScreenDataReqDTO r) {
        assertThatCode(() -> guard.check(d, r)).doesNotThrowAnyException();
    }

    // ===== SELF =====

    @Test
    void self_empIdEqualsCurrentUser_pass() {
        scope(DataScopeType.SELF);
        assertPassed(subjectDs(), req(CUR_EMP, null));
    }

    @Test
    void self_empIdOfOtherUser_denied() {
        scope(DataScopeType.SELF);
        assertDenied(subjectDs(), req("E999", null));
    }

    @Test
    void self_orgCodeEqualsMainOrg_pass() {
        scope(DataScopeType.SELF);
        assertPassed(subjectDs(), req(CUR_EMP, MAIN_ORG));
    }

    @Test
    void self_orgCodeOfOtherOrg_denied() {
        scope(DataScopeType.SELF);
        assertDenied(subjectDs(), req(null, "ORG_B"));
    }

    @Test
    void self_noSubjectParams_pass() {
        // 主体参数缺失属于 43010（引擎入参校验）职责，Guard 不拦截
        scope(DataScopeType.SELF);
        assertPassed(subjectDs(), req(null, null));
    }

    // ===== ORG =====

    @Test
    void org_orgCodeMainAndEmpMemberOfMainOrg_pass() {
        scope(DataScopeType.ORG);
        when(orgApi.getUserMainOrg("E777")).thenReturn(org(MAIN_ORG, 2, "1"));
        assertPassed(subjectDs(), req("E777", MAIN_ORG));
    }

    @Test
    void org_orgCodeOfOtherOrg_denied() {
        scope(DataScopeType.ORG);
        assertDenied(subjectDs(), req(null, "ORG_B"));
    }

    @Test
    void org_empNotMemberOfMainOrg_denied() {
        scope(DataScopeType.ORG);
        when(orgApi.getUserMainOrg("E777")).thenReturn(org("ORG_B", 2, "1"));
        assertDenied(subjectDs(), req("E777", null));
    }

    @Test
    void org_empMainOrgLookupThrows_failClose_denied() {
        // OrgService.getUserMainOrg 对无主机构用户抛 BizException——fail-close 一律 43013
        scope(DataScopeType.ORG);
        when(orgApi.getUserMainOrg("E777")).thenThrow(new RuntimeException("user org not found"));
        assertDenied(subjectDs(), req("E777", null));
    }

    // ===== ORG_SUBTREE =====

    @Test
    void orgSubtree_orgCodeInSubtree_pass() {
        scope(DataScopeType.ORG_SUBTREE);
        when(orgApi.getOrgSubtreeCodes(MAIN_ORG)).thenReturn(Set.of(MAIN_ORG, "ORG_A1", "ORG_A2"));
        assertPassed(subjectDs(), req(null, "ORG_A1"));
    }

    @Test
    void orgSubtree_orgCodeOutsideSubtree_denied() {
        scope(DataScopeType.ORG_SUBTREE);
        when(orgApi.getOrgSubtreeCodes(MAIN_ORG)).thenReturn(Set.of(MAIN_ORG, "ORG_A1"));
        assertDenied(subjectDs(), req(null, "ORG_B"));
    }

    @Test
    void orgSubtree_empMainOrgInSubtree_pass() {
        scope(DataScopeType.ORG_SUBTREE);
        when(orgApi.getOrgSubtreeCodes(MAIN_ORG)).thenReturn(Set.of(MAIN_ORG, "ORG_A2"));
        when(orgApi.getUserMainOrg("E777")).thenReturn(org("ORG_A2", 3, MAIN_ORG));
        assertPassed(subjectDs(), req("E777", null));
    }

    @Test
    void orgSubtree_empMainOrgOutsideSubtree_denied() {
        scope(DataScopeType.ORG_SUBTREE);
        when(orgApi.getOrgSubtreeCodes(MAIN_ORG)).thenReturn(Set.of(MAIN_ORG));
        when(orgApi.getUserMainOrg("E777")).thenReturn(org("ORG_B", 2, "1"));
        assertDenied(subjectDs(), req("E777", null));
    }

    // ===== ALL =====

    @Test
    void all_anySubjectParams_pass() {
        scope(DataScopeType.ALL);
        assertPassed(subjectDs(), req("E999", "ORG_B"));
    }

    // ===== 其余类型 / 未配置：fail-close =====

    @Test
    void selfCreated_denied() {
        scope(DataScopeType.SELF_CREATED);
        assertDenied(subjectDs(), req(CUR_EMP, null));
    }

    @Test
    void selfAssigned_denied() {
        scope(DataScopeType.SELF_ASSIGNED);
        assertDenied(subjectDs(), req(CUR_EMP, null));
    }

    @Test
    void workflowParticipant_denied() {
        scope(DataScopeType.WORKFLOW_PARTICIPANT);
        assertDenied(subjectDs(), req(CUR_EMP, null));
    }

    @Test
    void scopeResolveReturnsNull_denied() {
        scope(null);
        assertDenied(subjectDs(), req(CUR_EMP, null));
    }

    @Test
    void scopeResolveThrows_failClose_denied() {
        when(bizScopeApi.resolveScope(CUR_EMP, BizType.REPORT))
                .thenThrow(new RuntimeException("auth down"));
        assertDenied(subjectDs(), req(CUR_EMP, null));
    }

    /** 多角色并集在 auth 端 resolveScope（REPORT）实现，Guard 必须消费该并集入口取最大边界. */
    @Test
    void usesReportBizTypeUnionResolution() {
        scope(DataScopeType.ALL);
        guard.check(subjectDs(), req(null, null));
        verify(bizScopeApi).resolveScope(CUR_EMP, BizType.REPORT);
    }

    // ===== GLOBAL 数据源（scopeMode=GLOBAL）=====

    private RptScreenDatasource globalDs() {
        return ds("CUSTOM_SQL", "{\"scopeMode\":\"GLOBAL\",\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\"}");
    }

    @Test
    void global_scopeAll_pass() {
        scope(DataScopeType.ALL);
        assertPassed(globalDs(), req(null, null));
    }

    @Test
    void global_orgSubtreeAtProvinceRoot_pass() {
        // 省级节点判定：EXT_ORG_INFO 顶层（ORG_LEVEL=1 / P_ID='0'）
        scope(DataScopeType.ORG_SUBTREE);
        when(orgApi.getOrg(MAIN_ORG)).thenReturn(org(MAIN_ORG, 1, "0"));
        assertPassed(globalDs(), req(null, null));
    }

    @Test
    void global_orgSubtreeAtBranchNode_denied() {
        scope(DataScopeType.ORG_SUBTREE);
        when(orgApi.getOrg(MAIN_ORG)).thenReturn(org(MAIN_ORG, 2, "1"));
        assertDenied(globalDs(), req(null, null));
    }

    @Test
    void global_orgSubtreeMainOrgUnknown_failClose_denied() {
        scope(DataScopeType.ORG_SUBTREE);
        when(orgApi.getOrg(MAIN_ORG)).thenReturn(null);
        assertDenied(globalDs(), req(null, null));
    }

    @Test
    void global_scopeOrg_denied() {
        scope(DataScopeType.ORG);
        assertDenied(globalDs(), req(null, null));
    }

    @Test
    void global_scopeSelf_denied() {
        scope(DataScopeType.SELF);
        assertDenied(globalDs(), req(null, null));
    }

    // ===== SUBJECT 型但不消费主体参数：按 GLOBAL 同规则处理（防绕过）=====

    @Test
    void subjectCustomSqlWithoutSubjectPlaceholders_treatedAsGlobal_denied() {
        // scopeMode 缺省=SUBJECT，但 SQL 无 #{empId}/#{orgCode} 占位 → 全省口径，ORG scope 拒绝
        scope(DataScopeType.ORG);
        RptScreenDatasource d = ds("CUSTOM_SQL", "{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\"}");
        assertDenied(d, req(null, null));
    }

    @Test
    void subjectCustomSqlWithEmpPlaceholder_subjectRuleApplies_pass() {
        scope(DataScopeType.SELF);
        RptScreenDatasource d = ds("CUSTOM_SQL",
                "{\"sql\":\"SELECT COUNT(*) AS cnt FROM TOUCH_TASK WHERE assignee_emp_id = #{empId}\"}");
        assertPassed(d, req(CUR_EMP, null));
    }

    @Test
    void wideTableWithAggregation_treatedAsGlobal_denied() {
        // 聚合引导式跨主体、不消费主体参数 → 非省级 ORG_SUBTREE 拒绝
        scope(DataScopeType.ORG_SUBTREE);
        when(orgApi.getOrg(MAIN_ORG)).thenReturn(org(MAIN_ORG, 3, "120"));
        RptScreenDatasource d = ds("WIDE_TABLE", "{\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"slot\":3}],"
                + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}}");
        assertDenied(d, req(null, null));
    }

    @Test
    void wideTableWithAggregation_scopeAll_pass() {
        scope(DataScopeType.ALL);
        RptScreenDatasource d = ds("WIDE_TABLE", "{\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"slot\":3}],"
                + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}}");
        assertPassed(d, req(null, null));
    }

    @Test
    void kpiDetail_subjectRuleApplies_denied() {
        // KPI_DETAIL 恒消费主体参数：SELF scope 下 empId 越权拒绝
        scope(DataScopeType.SELF);
        RptScreenDatasource d = ds("KPI_DETAIL",
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}");
        assertDenied(d, req("E999", null));
    }

    @Test
    void configJsonUnparseable_failClose_denied() {
        // 配置解析失败按最严的 GLOBAL 规则 fail-close（非 ALL 即拒绝）
        scope(DataScopeType.ORG);
        assertDenied(ds("CUSTOM_SQL", "{not-json"), req(null, null));
    }
}
