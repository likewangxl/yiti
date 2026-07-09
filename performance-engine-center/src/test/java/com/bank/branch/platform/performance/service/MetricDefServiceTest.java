package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.MetricCategoryDTO;
import com.bank.branch.platform.performance.controller.dto.MetricDefRespDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricDefService 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class MetricDefServiceTest {

    @Mock
    private PerfMetricDefMapper mapper;

    @Mock
    private MetricRefService metricRefService;

    @Mock
    private MetricSlotService metricSlotService;

    @Mock
    private MetricCycleDetectService metricCycleDetectService;

    @Mock
    private com.bank.branch.platform.auth.api.UserApi userApi;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MetricDefService service;

    @Test
    @DisplayName("指标详情：创建人/更新人 empId 解析为 username + 中文名，并返回创建/更新时间")
    void getByCodeDto_resolvesCreatorUpdaterUsernameAndChnName() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("TEST_METRIC_AUDIT");
        def.setMetricName("审计字段指标");
        def.setCreatedBy("E001");
        def.setUpdatedBy("E002");
        java.time.LocalDateTime ct = java.time.LocalDateTime.of(2026, 5, 1, 9, 0, 0);
        java.time.LocalDateTime ut = java.time.LocalDateTime.of(2026, 5, 31, 18, 30, 0);
        def.setCreatedTime(ct);
        def.setUpdatedTime(ut);
        when(mapper.selectByMetricCode("TEST_METRIC_AUDIT")).thenReturn(def);

        com.bank.branch.platform.auth.api.dto.UserDTO u1 = new com.bank.branch.platform.auth.api.dto.UserDTO();
        u1.setEmpId("E001");
        u1.setUsername("rm_zhang");
        u1.setDisplayName("张三");
        com.bank.branch.platform.auth.api.dto.UserDTO u2 = new com.bank.branch.platform.auth.api.dto.UserDTO();
        u2.setEmpId("E002");
        u2.setUsername("rm_li");
        u2.setDisplayName("李四");
        when(userApi.getUserByEmpIds(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(u1, u2));

        MetricDefRespDTO dto = service.getByCodeDto("TEST_METRIC_AUDIT");

        assertThat(dto.getCreatedBy()).isEqualTo("E001");
        assertThat(dto.getUpdatedBy()).isEqualTo("E002");
        assertThat(dto.getCreatedByUsername()).isEqualTo("rm_zhang");
        assertThat(dto.getCreatedByName()).isEqualTo("张三");
        assertThat(dto.getUpdatedByUsername()).isEqualTo("rm_li");
        assertThat(dto.getUpdatedByName()).isEqualTo("李四");
        assertThat(dto.getCreatedTime()).isEqualTo(ct);
        assertThat(dto.getUpdatedTime()).isEqualTo(ut);
    }

    @Test
    @DisplayName("EXPR 指标创建：仅落库 expr_text（expr_display 列已废弃，不再持久化）")
    void create_expr_persistsExprTextOnly() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_EXPR")
                .metricName("复合指标")
                .baseDim("EMP")
                .metricLevel(2)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("EXPR")
                .exprText("M_0001 + M_0002 * 2")
                .operator("admin")
                .build();
        when(mapper.selectByMetricCode("TEST_METRIC_EXPR")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("EMP", 2, null)).thenReturn(7);

        service.create(cmd);

        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getExprText()).isEqualTo("M_0001 + M_0002 * 2");
    }

    @Test
    @DisplayName("buildExprDisplay：expr_text 的 M_xxx 实时派生为 M_xxx·名称（查不到名的编号原样保留）")
    void buildExprDisplay_replacesCodesWithCodeDotName() {
        PerfMetricDef m1 = new PerfMetricDef();
        m1.setMetricCode("M_0001");
        m1.setMetricName("一般性存款月均余额2");
        PerfMetricDef m5 = new PerfMetricDef();
        m5.setMetricCode("M_0005");
        m5.setMetricName("一般性存款年日均余额");
        when(mapper.selectByMetricCodes(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.List.of(m1, m5));

        String display = service.buildExprDisplay("M_0001 + M_0005");

        assertThat(display).isEqualTo("M_0001·一般性存款月均余额2 + M_0005·一般性存款年日均余额");
    }

    @Test
    @DisplayName("指标创建：description（详细描述）原样落库，不做任何加工")
    void create_persistsDescriptionVerbatim() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_DESC")
                .metricName("详细描述指标")
                .baseDim("EMP")
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("SQL")
                .sqlText("SELECT 1")
                .description("这是用户手动输入的详细描述，应原样保存")
                .operator("admin")
                .build();
        when(mapper.selectByMetricCode("TEST_METRIC_DESC")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("EMP", 1, null)).thenReturn(3);

        service.create(cmd);

        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("这是用户手动输入的详细描述，应原样保存");
    }

    @Test
    @DisplayName("指标名维度后缀归一：无后缀时按维度追加 -员工/-机构/-客户")
    void applyDimensionSuffix_appendsByDimWhenNoSuffix() {
        assertThat(MetricDefService.applyDimensionSuffix("客户数", "EMP")).isEqualTo("客户数-员工");
        assertThat(MetricDefService.applyDimensionSuffix("客户数", "ORG")).isEqualTo("客户数-机构");
        assertThat(MetricDefService.applyDimensionSuffix("客户数", "CUST")).isEqualTo("客户数-客户");
        // 维度小写/带空格也归一
        assertThat(MetricDefService.applyDimensionSuffix("  客户数 ", "emp")).isEqualTo("客户数-员工");
    }

    @Test
    @DisplayName("指标名维度后缀归一：已带任一后缀(员工/机构/客户)则不再追加")
    void applyDimensionSuffix_keepsWhenAlreadyHasAnySuffix() {
        assertThat(MetricDefService.applyDimensionSuffix("客户数-员工", "EMP")).isEqualTo("客户数-员工");
        assertThat(MetricDefService.applyDimensionSuffix("客户数-机构", "CUST")).isEqualTo("客户数-机构");
        assertThat(MetricDefService.applyDimensionSuffix("客户数-客户", "ORG")).isEqualTo("客户数-客户");
    }

    @Test
    @DisplayName("指标名维度后缀归一：维度为空 / 非法 / 指标名为空时不追加")
    void applyDimensionSuffix_noDimOrNullName_noAppend() {
        assertThat(MetricDefService.applyDimensionSuffix("某指标", null)).isEqualTo("某指标");
        assertThat(MetricDefService.applyDimensionSuffix("某指标", "")).isEqualTo("某指标");
        assertThat(MetricDefService.applyDimensionSuffix("某指标", "XYZ")).isEqualTo("某指标");
        assertThat(MetricDefService.applyDimensionSuffix(null, "EMP")).isNull();
    }

    @Test
    @DisplayName("新增指标：指标名按维度自动补后缀后落库")
    void create_appendsDimensionSuffixToMetricName() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_SUFFIX")
                .metricName("新客户数")
                .baseDim("CUST")
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("SQL")
                .sqlText("SELECT 1")
                .operator("admin")
                .build();
        when(mapper.selectByMetricCode("TEST_METRIC_SUFFIX")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("CUST", 1, null)).thenReturn(5);

        service.create(cmd);

        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getMetricName()).isEqualTo("新客户数-客户");
    }

    @Test
    @DisplayName("EXPR 指标创建：expr_text 语法不合法 → 抛 METRIC_CALC_LOGIC_INVALID")
    void create_expr_invalidExprText_throws() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_BAD_EXPR")
                .metricName("非法表达式指标")
                .baseDim("EMP")
                .metricLevel(2)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("EXPR")
                .exprText("M_0001 + ")
                .operator("admin")
                .build();

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("不合法");
        verify(mapper, never()).insert(any(PerfMetricDef.class));
    }

    @Test
    @DisplayName("EXPR 指标：表达式非空但不引用任何指标 → 抛 METRIC_CALC_LOGIC_INVALID")
    void create_expr_nonEmptyWithoutMetric_throws() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_NO_REF")
                .metricName("无引用表达式指标")
                .baseDim("EMP")
                .metricLevel(2)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("EXPR")
                .exprText("100 + 50") // 语法合法但不引用任何 M_ 指标
                .operator("admin")
                .build();

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("至少需要引用一个指标");
        verify(mapper, never()).insert(any(PerfMetricDef.class));
    }

    @Test
    @DisplayName("create：metricCode 以取值时间保留后缀 __PME 结尾 → 抛 METRIC_CODE_RESERVED_SUFFIX")
    void create_reservedSuffixCode_throws() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("M_BALANCE__PME")
                .metricName("保留后缀指标")
                .baseDim("EMP")
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("SQL")
                .sqlText("SELECT 1")
                .operator("admin")
                .build();

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CODE_RESERVED_SUFFIX);
        verify(mapper, never()).insert(any(PerfMetricDef.class));
    }

    @Test
    @DisplayName("L1 指标创建时写主表且无引用")
    void create_L1_insertsDefAndNoRefs() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_L1")
                .metricName("L1 指标")
                .baseDim("EMP")
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("SQL")
                .sqlText("SELECT 1")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_L1")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("EMP", 1, null)).thenReturn(1);

        PerfMetricDef created = service.create(cmd);

        assertThat(created.getMetricCode()).isEqualTo("TEST_METRIC_L1");
        assertThat(created.getValSlot()).isEqualTo(1);
        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRefMetricCodes()).isEqualTo("[]");
        verify(metricRefService).setRefs("TEST_METRIC_L1", List.of());
    }

    @Test
    @DisplayName("V1.9：cmd.status=DISABLED 时落库尊重，不再无脑 ACTIVE")
    void create_withStatusDisabled_respectsCmdStatus() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_OFF")
                .metricName("停用指标")
                .baseDim(null)
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("MANUAL")
                .calcLogicType("EXPR")
                .status("DISABLED")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_OFF")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());

        PerfMetricDef created = service.create(cmd);

        assertThat(created.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("V1.9：cmd.status=null 时回落 ACTIVE（普通 CRUD 创建路径）")
    void create_withNullStatus_defaultsActive() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_DEF_ACT")
                .metricName("默认启用指标")
                .baseDim("EMP")
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("SQL")
                .sqlText("SELECT 1")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_DEF_ACT")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("EMP", 1, null)).thenReturn(2);

        PerfMetricDef created = service.create(cmd);

        assertThat(created.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("V1.9：baseDim=null 创建指标跳过 slot 分配，val_slot=null")
    void create_baseDimNull_skipsSlotAllocation() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_META")
                .metricName("维度无关型指标")
                .baseDim(null)        // V1.9：维度无关型
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("MANUAL")
                .calcLogicType("EXPR")
                .exprText("外部填值")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_META")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());

        PerfMetricDef created = service.create(cmd);

        assertThat(created.getMetricCode()).isEqualTo("TEST_METRIC_META");
        assertThat(created.getValSlot()).isNull();
        assertThat(created.getBaseDim()).isNull();
        // slot 服务一次都不能被调
        verify(metricSlotService, never()).allocSlot(any(), any(), any());
    }

    @Test
    @DisplayName("L2 指标创建时会写主表并重建引用")
    void create_L2WithRefs_insertsDefAndRefRows() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_L2")
                .metricName("L2 指标")
                .baseDim("EMP")
                .metricLevel(2)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("EXPR")
                .exprText("REF_1 + REF_2")
                .refMetricCodes("[\"REF_1\",\"REF_2\"]")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_L2")).thenReturn(null);
        when(mapper.selectByMetricCodes(List.of("REF_1", "REF_2")))
                .thenReturn(List.of(metric("REF_1", 1), metric("REF_2", 1)));
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("EMP", 2, null)).thenReturn(101);

        PerfMetricDef created = service.create(cmd);

        assertThat(created.getMetricCode()).isEqualTo("TEST_METRIC_L2");
        assertThat(created.getValSlot()).isEqualTo(101);
        verify(metricRefService).setRefs("TEST_METRIC_L2", List.of("REF_1", "REF_2"));
    }

    @Test
    @DisplayName("指标编码重复时抛 40903")
    void create_whenMetricCodeDup_throws40903() {
        when(mapper.selectByMetricCode("TEST_METRIC_DUP")).thenReturn(metric("TEST_METRIC_DUP", 1));

        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_DUP")
                .metricName("dup")
                .baseDim("EMP")
                .metricLevel(1)
                .operator("admin")
                .build();

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CODE_DUP));
    }

    @Test
    @DisplayName("引用层级错误时抛 40911")
    void create_whenRefLevelWrong_throws40911() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_BAD_LEVEL")
                .metricName("bad")
                .baseDim("EMP")
                .metricLevel(2)
                .refMetricCodes("[\"REF_BAD\"]")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_BAD_LEVEL")).thenReturn(null);
        when(mapper.selectByMetricCodes(List.of("REF_BAD"))).thenReturn(List.of(metric("REF_BAD", 3)));
        doThrow(new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "bad"))
                .when(metricCycleDetectService).checkLevelConstraint(eq(2), any(Map.class));

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID));
    }

    @Test
    @DisplayName("引用成环时抛 40902")
    void create_whenRefFormsCycle_throws40902() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_CYCLE")
                .metricName("cycle")
                .baseDim("EMP")
                .metricLevel(2)
                .refMetricCodes("[\"REF_A\"]")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_CYCLE")).thenReturn(null);
        when(mapper.selectByMetricCodes(List.of("REF_A"))).thenReturn(List.of(metric("REF_A", 1)));
        when(metricRefService.loadFullGraph()).thenReturn(Map.of("REF_A", List.of("TEST_METRIC_CYCLE")));
        doThrow(new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "cycle"))
                .when(metricCycleDetectService).checkNoCycle(any(Map.class), eq("TEST_METRIC_CYCLE"), eq(List.of("REF_A")));

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID));
    }

    @Test
    @DisplayName("更新引用时会重建引用关系")
    void update_withRefsChange_callsSetRefsWithNewList() {
        PerfMetricDef existing = metric("TEST_METRIC_UPD", 2);
        existing.setId("ID_UPD");
        existing.setBaseDim("EMP");
        existing.setMetricLevel(2);
        existing.setValSlot(120);

        UpdateMetricDefCmd cmd = UpdateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_UPD")
                .metricName("updated")
                .metricDesc("new desc")
                .refMetricCodes("[\"REF_NEW\"]")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_UPD")).thenReturn(existing);
        when(mapper.selectByMetricCodes(List.of("REF_NEW"))).thenReturn(List.of(metric("REF_NEW", 1)));
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());

        PerfMetricDef updated = service.update(cmd);

        assertThat(updated.getMetricCode()).isEqualTo("TEST_METRIC_UPD");
        verify(metricRefService).setRefs("TEST_METRIC_UPD", List.of("REF_NEW"));
        verify(mapper).updateByIdSelective(any(PerfMetricDef.class));
    }

    @Test
    @DisplayName("更新未显式提供 refMetricCodes 时保留原有依赖")
    void update_whenRefMetricCodesOmitted_keepsExistingRefs() {
        PerfMetricDef existing = metric("TEST_METRIC_KEEP_REFS", 2);
        existing.setId("ID_KEEP_REFS");
        existing.setMetricLevel(2);
        existing.setRefMetricCodes("[\"REF_OLD\"]");

        UpdateMetricDefCmd cmd = UpdateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_KEEP_REFS")
                .metricName("updated-name")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_KEEP_REFS")).thenReturn(existing);

        PerfMetricDef updated = service.update(cmd);

        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).updateByIdSelective(captor.capture());
        assertThat(captor.getValue().getRefMetricCodes()).isNull();
        verify(metricRefService, never()).setRefs(any(), any());
        assertThat(updated.getRefMetricCodes()).isEqualTo("[\"REF_OLD\"]");
        assertThat(updated.getMetricName()).isEqualTo("updated-name");
    }

    @Test
    @DisplayName("更新指标层级 → patch 携带 metricLevel 落库，返回视图同步（val_slot 不变）")
    void update_metricLevel_persistedToPatchAndView() {
        PerfMetricDef existing = metric("TEST_METRIC_LVL", 1);
        existing.setId("ID_LVL");
        existing.setMetricLevel(1);
        existing.setValSlot(7);

        UpdateMetricDefCmd cmd = UpdateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_LVL")
                .metricName("n")
                .metricLevel(2) // 1 级 → 2 级
                .operator("admin")
                .build();
        when(mapper.selectByMetricCode("TEST_METRIC_LVL")).thenReturn(existing);

        PerfMetricDef updated = service.update(cmd);

        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).updateByIdSelective(captor.capture());
        // patch 携带新层级 → mapper <if metricLevel!=null> 落库 metric_level
        assertThat(captor.getValue().getMetricLevel()).isEqualTo(2);
        // 返回视图同步新层级；val_slot 保持不变（不重分配）
        assertThat(updated.getMetricLevel()).isEqualTo(2);
        assertThat(updated.getValSlot()).isEqualTo(7);
    }

    @Test
    @DisplayName("编辑清空 Groovy 表达式（exprText=null）→ patch 以空串落库，选择性更新可写入清空")
    void update_clearExprText_persistedAsEmptyString() {
        PerfMetricDef existing = metric("TEST_METRIC_CLR", 2);
        existing.setId("ID_CLR");
        existing.setMetricLevel(2);
        existing.setCalcLogicType("EXPR");
        existing.setExprText("M_0001 + M_0002");

        UpdateMetricDefCmd cmd = UpdateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_CLR")
                .metricName("n")
                .calcLogicType("EXPR")
                .exprText(null) // 用户删除了 Groovy 表达式
                .operator("admin")
                .build();
        when(mapper.selectByMetricCode("TEST_METRIC_CLR")).thenReturn(existing);

        PerfMetricDef updated = service.update(cmd);

        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).updateByIdSelective(captor.capture());
        // 非 null 空串 → mapper <if exprText!=null> 命中，expr_text 落库为 ''（清空）
        assertThat(captor.getValue().getExprText()).isEqualTo("");
        assertThat(updated.getExprText()).isEqualTo("");
    }

    @Test
    @DisplayName("非 ACTIVE 状态不可停用")
    void disable_whenStatusNotActive_throws40905() {
        PerfMetricDef existing = metric("TEST_METRIC_DISABLED", 1);
        existing.setStatus("DISABLED");
        when(mapper.selectByMetricCode("TEST_METRIC_DISABLED")).thenReturn(existing);

        assertThatThrownBy(() -> service.disable("TEST_METRIC_DISABLED", "停用", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("分页查询返回 PageResult")
    void page_returnsPageResult() {
        when(mapper.countByCondition("EMP", 1, "ACTIVE", "KW")).thenReturn(2L);
        when(mapper.selectByCondition("EMP", 1, "ACTIVE", "KW", 0, 10))
                .thenReturn(List.of(metric("M1", 1), metric("M2", 1)));

        PageResult<PerfMetricDef> result = service.page("EMP", 1, "ACTIVE", "KW", 1, 10);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
    }

    @Test
    @DisplayName("V1.10：listAllDto 一次性返回全部指标，按 metric_code 顺序，不分页")
    void listAllDto_returnsAllRecordsAsRespDTOs() {
        when(mapper.selectAllByCondition(null, null, null, null))
                .thenReturn(List.of(
                        metric("M_A_ALL", 1),
                        metric("M_B_ALL", 2),
                        metric("M_C_ALL", 1)
                ));

        List<MetricDefRespDTO> result = service.listAllDto(null, null, null, null);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getMetricCode()).isEqualTo("M_A_ALL");
        assertThat(result.get(1).getMetricCode()).isEqualTo("M_B_ALL");
        assertThat(result.get(2).getMetricCode()).isEqualTo("M_C_ALL");
        // 数据库空集合 → 返回空 List（非 null）
        when(mapper.selectAllByCondition("ORG", null, null, null)).thenReturn(Collections.emptyList());
        assertThat(service.listAllDto("ORG", null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("V1.10：listCategories 把 distinct metric_category 包装成 {value,label} 对（value==label）")
    void listCategories_returnsDistinctCategoriesAsValueLabelPairs() {
        when(mapper.selectDistinctCategories())
                .thenReturn(List.of("合规类", "效益类", "规模类", "质量类"));

        List<MetricCategoryDTO> result = service.listCategories();

        assertThat(result).hasSize(4);
        // V1.9 metric_category 直接存中文，未引入 sys_dict 翻译，value == label
        assertThat(result).extracting(MetricCategoryDTO::getValue)
                .containsExactly("合规类", "效益类", "规模类", "质量类");
        assertThat(result).allSatisfy(dto ->
                assertThat(dto.getLabel()).isEqualTo(dto.getValue()));
    }

    @Test
    @DisplayName("V1.10：listCategories 当无分类数据时返回空 List 而非 null")
    void listCategories_returnsEmptyListWhenNoCategoryRows() {
        when(mapper.selectDistinctCategories()).thenReturn(Collections.emptyList());
        assertThat(service.listCategories()).isEmpty();
    }

    @Test
    @DisplayName("Task B5：deleteMetric 执行软删除，删除后 getMetricById 返回 null")
    void deleteMetric_isSoftDelete_notReturnedInQueries() {
        PerfMetricDef m = metric("TEST_METRIC_B5", 1);
        m.setId("ID_B5");
        m.setDeleted(0);

        // deleteMetric 应调用 softDelete（deleted=1），而非物理删除
        when(mapper.softDelete("ID_B5")).thenReturn(1);

        service.deleteMetric("ID_B5");

        // 验证调用的是软删除而非 deleteById
        verify(mapper).softDelete("ID_B5");

        // getMetricById 底层 selectById 返回 null（已软删）
        when(mapper.selectById("ID_B5")).thenReturn(null);
        assertThat(service.getMetricById("ID_B5")).isNull();
    }

    private static PerfMetricDef metric(String metricCode, int level) {
        PerfMetricDef def = new PerfMetricDef();
        def.setId("ID_" + metricCode);
        def.setMetricCode(metricCode);
        def.setMetricName(metricCode);
        def.setBaseDim("EMP");
        def.setMetricLevel(level);
        def.setCalcFreq("DAY");
        def.setCalcMode("AUTO");
        def.setCalcLogicType("SQL");
        def.setStatus("ACTIVE");
        def.setDeleted(0);
        return def;
    }
}
