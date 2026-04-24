package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.controller.dto.AddKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.PublishKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.ReleaseSlotReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiSchemeReqDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.support.KpiTestDataBuilder;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KpiSchemeController IT: 覆盖 9 个端点的正反向 + 鉴权/审计注解.
 *
 * <p>测试数据前缀: TEST_KPI_* (方案) / TEST_METRIC_* (依赖的指标),
 * 事务 @Transactional + @Rollback 自动清理。
 */
class KpiSchemeControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.KpiSchemeController";

    @Autowired
    private PerfKpiSchemeMapper schemeMapper;

    @Autowired
    private PerfKpiItemMapper itemMapper;

    @Autowired
    private PerfMetricDefMapper metricDefMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // =================== list ===================

    @Test
    void list_withDefaultPaging_returns200() throws Exception {
        schemeMapper.insert(scheme("PAGE_A"));
        schemeMapper.insert(scheme("PAGE_B"));

        mockMvc.perform(get("/api/perf/kpi-schemes")
                        .param("keyword", "TEST_KPI_PAGE")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.pageNo").value(1))
                .andExpect(jsonPath("$.page.pageSize").value(10))
                .andExpect(jsonPath("$.page.total").value(2));
    }

    // =================== getById ===================

    @Test
    void getById_whenExists_returns200() throws Exception {
        PerfKpiScheme s = scheme("GET_OK");
        schemeMapper.insert(s);

        mockMvc.perform(get("/api/perf/kpi-schemes/{id}", s.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(s.getId()))
                .andExpect(jsonPath("$.data.schemeCode").value("TEST_KPI_GET_OK"));
    }

    @Test
    void getById_whenNotFound_returns404BizError() throws Exception {
        // Q8.5a 对齐 PerfErrorCode §K 权威清单：KPI 方案不存在 → PERF-40003（V1.0 整改后）
        mockMvc.perform(get("/api/perf/kpi-schemes/{id}", "NON_EXIST_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40003"));
    }

    // =================== create ===================

    @Test
    void create_whenSuccess_returns200() throws Exception {
        metricDefMapper.insert(metric("CREATE_OK_M1", 1));
        CreateKpiSchemeReqDTO req = createReq("CREATE_OK", "TEST_METRIC_CREATE_OK_M1");

        mockMvc.perform(post("/api/perf/kpi-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.schemeCode").value("TEST_KPI_CREATE_OK"));

        assertThat(schemeMapper.selectBySchemeCode("TEST_KPI_CREATE_OK")).isNotNull();
    }

    @Test
    void create_whenSchemeCodeDup_returns409BizError() throws Exception {
        schemeMapper.insert(scheme("CREATE_DUP"));
        metricDefMapper.insert(metric("CREATE_DUP_M", 1));
        CreateKpiSchemeReqDTO req = createReq("CREATE_DUP", "TEST_METRIC_CREATE_DUP_M");

        mockMvc.perform(post("/api/perf/kpi-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                // Q8.5a 对齐 PerfErrorCode §K：KPI 方案编码已存在 → PERF-40005（V1.1 P8.1 新增语义细分）
                .andExpect(jsonPath("$.code").value("PERF-40005"));
    }

    @Test
    void create_whenSchemeCodeLowercase_returns400() throws Exception {
        CreateKpiSchemeReqDTO req = createReq("bad_lower", "TEST_METRIC_X");
        req.setSchemeCode("bad_lower"); // 直接破坏规则

        mockMvc.perform(post("/api/perf/kpi-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenItemMetricCodeInvalid_returns400() throws Exception {
        CreateKpiSchemeReqDTO req = createReq("CREATE_BAD_ITEM", "lowercase_metric");

        mockMvc.perform(post("/api/perf/kpi-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =================== update ===================

    @Test
    void update_whenSuccess_returns200() throws Exception {
        PerfKpiScheme s = scheme("UPDATE_OK");
        schemeMapper.insert(s);

        UpdateKpiSchemeReqDTO req = new UpdateKpiSchemeReqDTO();
        req.setSchemeName("新名称");
        req.setCycleType("QUARTERLY");

        mockMvc.perform(put("/api/perf/kpi-schemes/{id}", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.schemeName").value("新名称"))
                .andExpect(jsonPath("$.data.cycleType").value("QUARTERLY"));
    }

    @Test
    void update_whenReasonMissing_returns200() throws Exception {
        // update 不强制 reason, 空 body 仍返回 200 (部分更新, 全部字段 null 不改动)
        PerfKpiScheme s = scheme("UPDATE_NOREQ");
        schemeMapper.insert(s);

        mockMvc.perform(put("/api/perf/kpi-schemes/{id}", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // =================== delete ===================

    @Test
    void delete_whenSuccess_disablesScheme() throws Exception {
        PerfKpiScheme s = scheme("DELETE_OK");
        schemeMapper.insert(s);

        ReleaseSlotReqDTO req = new ReleaseSlotReqDTO();
        req.setReason("业务下线");

        mockMvc.perform(delete("/api/perf/kpi-schemes/{id}", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        assertThat(schemeMapper.selectById(s.getId()).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void delete_whenReasonMissing_returns400() throws Exception {
        PerfKpiScheme s = scheme("DELETE_NOR");
        schemeMapper.insert(s);

        mockMvc.perform(delete("/api/perf/kpi-schemes/{id}", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReleaseSlotReqDTO())))
                .andExpect(status().isBadRequest());
    }

    // =================== publish ===================

    @Test
    void publish_whenUnpublished_returns200() throws Exception {
        PerfKpiScheme s = scheme("PUBLISH_OK");
        s.setStatus("DRAFT");
        schemeMapper.insert(s);
        // 方案下无 item, KpiSchemeService.publish 允许空方案发布
        PublishKpiSchemeReqDTO req = new PublishKpiSchemeReqDTO();
        req.setReason("开始发布");

        mockMvc.perform(post("/api/perf/kpi-schemes/{id}/publish", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(schemeMapper.selectById(s.getId()).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void publish_whenAlreadyDisabled_returnsBizError() throws Exception {
        PerfKpiScheme s = scheme("PUBLISH_DISABLED");
        s.setStatus("DISABLED");
        schemeMapper.insert(s);
        PublishKpiSchemeReqDTO req = new PublishKpiSchemeReqDTO();
        req.setReason("尝试发布");

        mockMvc.perform(post("/api/perf/kpi-schemes/{id}/publish", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                // Q8.5a 对齐 PerfErrorCode §K：方案已禁用不可发布 → 走 VALIDATION_FAILED（PERF-42200）
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    @Test
    void publish_whenReasonMissing_returns400() throws Exception {
        PerfKpiScheme s = scheme("PUBLISH_NOR");
        schemeMapper.insert(s);

        mockMvc.perform(post("/api/perf/kpi-schemes/{id}/publish", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PublishKpiSchemeReqDTO())))
                .andExpect(status().isBadRequest());
    }

    // =================== addItem ===================

    @Test
    void addItem_whenSuccess_returns200() throws Exception {
        PerfKpiScheme s = scheme("ADD_ITEM");
        schemeMapper.insert(s);
        metricDefMapper.insert(metric("ADD_ITEM_M", 1));

        AddKpiItemReqDTO req = itemReq("TEST_METRIC_ADD_ITEM_M", "50.0000");

        mockMvc.perform(post("/api/perf/kpi-schemes/{id}/items", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.metricCode").value("TEST_METRIC_ADD_ITEM_M"));

        assertThat(itemMapper.selectBySchemeId(s.getId())).hasSize(1);
    }

    @Test
    void addItem_whenDuplicate_returns409BizError() throws Exception {
        PerfKpiScheme s = scheme("ADD_ITEM_DUP");
        schemeMapper.insert(s);
        metricDefMapper.insert(metric("ADD_ITEM_DUP_M", 1));
        itemMapper.insert(KpiTestDataBuilder.item(s.getId(), "TEST_METRIC_ADD_ITEM_DUP_M"));

        AddKpiItemReqDTO req = itemReq("TEST_METRIC_ADD_ITEM_DUP_M", "50.0000");

        mockMvc.perform(post("/api/perf/kpi-schemes/{id}/items", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                // Q8.5a 对齐 PerfErrorCode §K：方案内指标重复复用 METRIC_CODE_DUP → PERF-40901
                .andExpect(jsonPath("$.code").value("PERF-40901"));
    }

    // =================== updateItem ===================

    @Test
    void updateItem_whenSuccess_returns200() throws Exception {
        PerfKpiScheme s = scheme("UPD_ITEM");
        schemeMapper.insert(s);
        PerfKpiItem it = KpiTestDataBuilder.item(s.getId(), "TEST_METRIC_UPDATEITEM");
        itemMapper.insert(it);

        UpdateKpiItemReqDTO req = new UpdateKpiItemReqDTO();
        req.setWeight(new BigDecimal("80.0000"));

        mockMvc.perform(put("/api/perf/kpi-schemes/{id}/items/{itemId}", s.getId(), it.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.weight").value(80.0000));
    }

    // =================== deleteItem ===================

    @Test
    void deleteItem_whenSuccess_removesItem() throws Exception {
        PerfKpiScheme s = scheme("DEL_ITEM");
        schemeMapper.insert(s);
        PerfKpiItem it = KpiTestDataBuilder.item(s.getId(), "TEST_METRIC_DELITEM");
        itemMapper.insert(it);

        ReleaseSlotReqDTO req = new ReleaseSlotReqDTO();
        req.setReason("调整方案");

        mockMvc.perform(delete("/api/perf/kpi-schemes/{id}/items/{itemId}", s.getId(), it.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        assertThat(itemMapper.selectById(it.getId())).isNull();
    }

    @Test
    void deleteItem_whenReasonMissing_returns400() throws Exception {
        PerfKpiScheme s = scheme("DEL_ITEM_NOR");
        schemeMapper.insert(s);
        PerfKpiItem it = KpiTestDataBuilder.item(s.getId(), "TEST_METRIC_DELITEM_NOR");
        itemMapper.insert(it);

        mockMvc.perform(delete("/api/perf/kpi-schemes/{id}/items/{itemId}", s.getId(), it.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReleaseSlotReqDTO())))
                .andExpect(status().isBadRequest());
    }

    // =================== 注解约束 ===================

    @Test
    void controller_shouldEnableMethodValidation() throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        assertThat(controllerClass.getAnnotation(Validated.class)).isNotNull();
    }

    @Test
    void readMethods_shouldDeclareBizAuth() throws Exception {
        // list 签名: cycleType, status, keyword, pageNo, pageSize (schemeCode 精确过滤已移除)
        assertBizAuth("list", new Class<?>[]{
                String.class, String.class, String.class, int.class, int.class
        }, BizAction.LIST);
        assertBizAuth("getById", new Class<?>[]{String.class}, BizAction.READ);
    }

    @Test
    void writeMethods_shouldDeclareExpectedBizAuthAndAuditLog() throws Exception {
        assertBizAuth("create", new Class<?>[]{CreateKpiSchemeReqDTO.class}, BizAction.WRITE);
        assertAuditLog("create", new Class<?>[]{CreateKpiSchemeReqDTO.class}, "CREATE", false);

        assertBizAuth("update", new Class<?>[]{String.class, UpdateKpiSchemeReqDTO.class}, BizAction.WRITE);
        assertAuditLog("update", new Class<?>[]{String.class, UpdateKpiSchemeReqDTO.class}, "UPDATE", false);

        assertBizAuth("delete", new Class<?>[]{String.class, ReleaseSlotReqDTO.class}, BizAction.DELETE);
        assertAuditLog("delete", new Class<?>[]{String.class, ReleaseSlotReqDTO.class}, "DELETE", true);

        // Q8.5a 对齐 Controller 实际注解与 CLAUDE.md §7.1.1 对照表：
        // POST /api/perf/kpi-schemes/{id}/publish → action=EXECUTE → P_PERF_KPI_PUB
        assertBizAuth("publish", new Class<?>[]{String.class, PublishKpiSchemeReqDTO.class}, BizAction.EXECUTE);
        assertAuditLog("publish", new Class<?>[]{String.class, PublishKpiSchemeReqDTO.class}, "PUBLISH", true);

        assertBizAuth("addItem", new Class<?>[]{String.class, AddKpiItemReqDTO.class}, BizAction.WRITE);
        assertAuditLog("addItem", new Class<?>[]{String.class, AddKpiItemReqDTO.class}, "CREATE", false);

        assertBizAuth("updateItem", new Class<?>[]{String.class, String.class, UpdateKpiItemReqDTO.class}, BizAction.WRITE);
        assertAuditLog("updateItem", new Class<?>[]{String.class, String.class, UpdateKpiItemReqDTO.class}, "UPDATE", false);

        assertBizAuth("deleteItem", new Class<?>[]{String.class, String.class, ReleaseSlotReqDTO.class}, BizAction.DELETE);
        assertAuditLog("deleteItem", new Class<?>[]{String.class, String.class, ReleaseSlotReqDTO.class}, "DELETE", true);
    }

    // =================== helpers ===================

    private void assertBizAuth(String methodName, Class<?>[] parameterTypes, BizAction action) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).as("method %s 应有 @BizAuth", methodName).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(action);
    }

    private void assertAuditLog(String methodName,
                                Class<?>[] parameterTypes,
                                String action,
                                boolean reasonRequired) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog).as("method %s 应有 @AuditLog", methodName).isNotNull();
        assertThat(auditLog.action()).isEqualTo(action);
        assertThat(auditLog.resourceType()).isEqualTo("KPI_SCHEME");
        assertThat(auditLog.reasonRequired()).isEqualTo(reasonRequired);
    }

    private static PerfKpiScheme scheme(String codeSuffix) {
        return KpiTestDataBuilder.scheme(codeSuffix);
    }

    private static PerfMetricDef metric(String codeSuffix, int level) {
        return MetricTestDataBuilder.l1Emp(codeSuffix, level);
    }

    private static CreateKpiSchemeReqDTO createReq(String suffix, String metricCode) {
        CreateKpiSchemeReqDTO req = new CreateKpiSchemeReqDTO();
        req.setSchemeCode("TEST_KPI_" + suffix);
        req.setSchemeName("KPI-" + suffix);
        req.setCycleType("MONTHLY");
        req.setOpenDetail(Boolean.FALSE);
        AddKpiItemReqDTO item = itemReq(metricCode, "50.0000");
        req.setItems(java.util.List.of(item));
        return req;
    }

    private static AddKpiItemReqDTO itemReq(String metricCode, String weight) {
        AddKpiItemReqDTO item = new AddKpiItemReqDTO();
        item.setMetricCode(metricCode);
        item.setWeight(new BigDecimal(weight));
        return item;
    }
}
