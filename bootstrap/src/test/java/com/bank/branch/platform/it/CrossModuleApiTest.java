package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 5 - 跨模块 API 调用验证
 * 验证 bootstrap 加载后，所有模块的 *Api 接口可被正确注入和调用。
 *
 * 测试目标:
 * 1. 所有模块 *Api 接口可被 Spring 注入 (无 NoSuchBeanDefinitionException)
 * 2. 跨模块调用路径完整 (auth → governance 等)
 * 3. 数据从 DB 经由 API 层完整传递到调用方
 *
 * 与所有其他测试共享同一个 ApplicationContext（相同 @SpringBootTest 配置），
 * 避免重复加载 Spring 上下文。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
class CrossModuleApiTest {

    @Autowired(required = false)
    private com.bank.branch.platform.auth.api.CurrentUserApi currentUserApi;

    @Autowired(required = false)
    private com.bank.branch.platform.auth.api.ResourceApi resourceApi;

    @Autowired(required = false)
    private com.bank.branch.platform.auth.api.BizScopeApi bizScopeApi;

    @Autowired(required = false)
    private com.bank.branch.platform.auth.api.OrgApi orgApi;

    @Autowired(required = false)
    private com.bank.branch.platform.governance.api.DictApi dictApi;

    @Autowired(required = false)
    private com.bank.branch.platform.governance.api.ConfigApi configApi;

    @Autowired(required = false)
    private com.bank.branch.platform.governance.api.CalendarApi calendarApi;

    @Autowired(required = false)
    private com.bank.branch.platform.workflow.api.WorkflowApi workflowApi;

    @Autowired(required = false)
    private com.bank.branch.platform.customer.api.AssetProjectQueryApi assetProjectQueryApi;

    @Autowired(required = false)
    private com.bank.branch.platform.bizapp.api.SupportApi supportApi;

    @Autowired(required = false)
    private com.bank.branch.platform.bizapp.api.SupportQueryApi supportQueryApi;

    @Autowired(required = false)
    private com.bank.branch.platform.bizapp.api.BizApplyQueryApi bizApplyQueryApi;

    // ========== 测试 1: 所有 API Bean 可被注入 ==========

    @Test
    @DisplayName("跨模块 API - Auth 模块 API 可被注入")
    void authApis_allInjectable() {
        assertThat(currentUserApi).as("CurrentUserApi should be injectable").isNotNull();
        assertThat(resourceApi).as("ResourceApi should be injectable").isNotNull();
        assertThat(bizScopeApi).as("BizScopeApi should be injectable").isNotNull();
        assertThat(orgApi).as("OrgApi should be injectable").isNotNull();
    }

    @Test
    @DisplayName("跨模块 API - Governance 模块 API 可被注入")
    void governanceApis_allInjectable() {
        assertThat(dictApi).as("DictApi").isNotNull();
        assertThat(configApi).as("ConfigApi").isNotNull();
        assertThat(calendarApi).as("CalendarApi").isNotNull();
    }

    @Test
    @DisplayName("跨模块 API - Workflow 模块 API 可被注入")
    void workflowApi_injectable() {
        assertThat(workflowApi).as("WorkflowApi should be injectable").isNotNull();
    }

    @Test
    @DisplayName("跨模块 API - BizApp 模块 API 可被注入")
    void bizAppApis_allInjectable() {
        assertThat(assetProjectQueryApi).as("AssetProjectQueryApi should be injectable").isNotNull();
        assertThat(supportApi).as("SupportApi should be injectable").isNotNull();
        assertThat(supportQueryApi).as("SupportQueryApi should be injectable").isNotNull();
        assertThat(bizApplyQueryApi).as("BizApplyQueryApi should be injectable").isNotNull();
    }

    @Test
    @DisplayName("跨模块 API - BizApp 查询 API 可返回基础业务数据")
    @Sql(scripts = {
            "/business-application-schema.sql",
            "/business-application-data.sql"
    })
    void bizAppApis_basicQueryWorks() {
        assertThat(supportApi.getSupportRequest("support-seed-001"))
                .as("SupportApi should return seeded support request")
                .isPresent();
        assertThat(bizApplyQueryApi.countRunningApplications("CUST_BIZ_SEED"))
                .satisfies(dto -> {
                    assertThat(dto.getRunningLoanCount()).isZero();
                    assertThat(dto.getRunningSupportCount()).isEqualTo(1L);
                });
    }

    // ========== 测试 2: OrgApi 功能验证 ==========

    @Test
    @DisplayName("OrgApi - 根据 orgCode 查询机构信息")
    void orgApi_getOrg_returnsOrgInfo() {
        var org = orgApi.getOrg("BJ");
        assertThat(org).as("getOrg('BJ') should return a result").isNotNull();
        assertThat(org.getOrgCode()).isEqualTo("BJ");
    }

    @Test
    @DisplayName("OrgApi - 不存在的 org 返回 null")
    void orgApi_getOrg_notFound() {
        var org = orgApi.getOrg("NONEXISTENT");
        assertThat(org).isNull();
    }

    @Test
    @DisplayName("OrgApi - getOrgSubtreeCodes 返回子机构代码")
    void orgApi_getOrgSubtreeCodes_returnsSubtree() {
        // HQ 的子机构: HQ, BJ, SH, BJ_CY, SH_PD
        var codes = orgApi.getOrgSubtreeCodes("HQ");
        assertThat(codes).contains("HQ", "BJ", "SH");
    }

    @Test
    @DisplayName("OrgApi - 搜索机构返回非空结果")
    void orgApi_searchOrgs_returnsResults() {
        var results = orgApi.searchOrgs("北京", 10);
        assertThat(results).as("搜索 '北京' 应返回结果").isNotEmpty();
    }

    // ========== 测试 3: DictApi 功能验证 ==========

    @Test
    @DisplayName("DictApi - 查询字典项返回列表")
    void dictApi_getDictItems_returnsList() {
        var items = dictApi.getDictItems("INDUSTRY");
        assertThat(items).as("INDUSTRY dict should have items").hasSize(2);
    }

    @Test
    @DisplayName("DictApi - 获取单个字典项")
    void dictApi_getDictItem_returnsItem() {
        var result = dictApi.getDictItem("INDUSTRY", "IT");
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("DictApi - 批量获取字典项")
    void dictApi_batchGetDictItems_returnsMultiple() {
        var result = dictApi.batchGetDictItems(java.util.Set.of("INDUSTRY", "STATUS"));
        assertThat(result).hasSize(2);
        assertThat(result.get("INDUSTRY")).hasSize(2);
    }

    @Test
    @DisplayName("DictApi - 校验字典值有效性 (有效)")
    void dictApi_isValidDictValue_returnsTrueForValidValue() {
        assertThat(dictApi.isValidDictValue("STATUS", "ACT")).isTrue();
    }

    @Test
    @DisplayName("DictApi - 校验无效字典值返回 false")
    void dictApi_isValidDictValue_returnsFalseForInvalidValue() {
        assertThat(dictApi.isValidDictValue("STATUS", "NONEXISTENT")).isFalse();
    }

    @Test
    @DisplayName("DictApi - 获取字典标签")
    void dictApi_getDictLabel_returnsLabel() {
        assertThat(dictApi.getDictLabel("STATUS", "ACT")).isEqualTo("激活");
    }

    // ========== 测试 4: ConfigApi 功能验证 ==========

    @Test
    @DisplayName("ConfigApi - 获取配置值")
    void configApi_getConfigValue_returnsValue() {
        var value = configApi.getConfigValue("app.name");
        assertThat(value).isPresent().contains("分行业务平台");
    }

    @Test
    @DisplayName("ConfigApi - 获取不存在的配置返回空的 Optional")
    void configApi_getConfigValue_missingKey() {
        var value = configApi.getConfigValue("nonexistent.key");
        assertThat(value).isNotPresent();
    }

    @Test
    @DisplayName("ConfigApi - 类型化获取 Boolean 值")
    void configApi_getConfigValue_typedBoolean() {
        Boolean value = configApi.getConfigValue("feature.loan.enabled", Boolean.class);
        assertThat(value).isTrue();
    }

    @Test
    @DisplayName("ConfigApi - 类型化获取 Number 值")
    void configApi_getConfigValue_typedNumber() {
        Integer value = configApi.getConfigValue("max.login.retry", Integer.class);
        assertThat(value).isEqualTo(5);
    }

    @Test
    @DisplayName("ConfigApi - 带默认值获取配置")
    void configApi_getConfigValue_withDefault() {
        var value = configApi.getConfigValue("nonexistent.key", "defaultValue");
        assertThat(value).isEqualTo("defaultValue");
    }

    // ========== 测试 5: CalendarApi 功能验证 ==========

    @Test
    @DisplayName("CalendarApi - 工作日查询")
    void calendarApi_isWorkingDay_workday() {
        assertThat(calendarApi.isWorkingDay(
                java.time.LocalDate.of(2026, 4, 1))).isTrue();
    }

    @Test
    @DisplayName("CalendarApi - 非工作日查询 (周末)")
    void calendarApi_isWorkingDay_weekend() {
        assertThat(calendarApi.isWorkingDay(
                java.time.LocalDate.of(2026, 4, 4))).isFalse();
    }
}
