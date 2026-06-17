package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.service.cmd.AddKpiItemCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateKpiItemCmd;
import com.bank.branch.platform.performance.support.KpiTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiItemService 单元测试.
 *
 * <p>使用 MockitoExtension, 纯 Mock 模式 (不加载 Spring 上下文).
 * <p>测试数据前缀统一 {@link KpiTestDataBuilder#CODE_PREFIX} = "TEST_KPI_".
 */
@ExtendWith(MockitoExtension.class)
class KpiItemServiceTest {

    @Mock
    private PerfKpiItemMapper itemMapper;

    @Mock
    private PerfKpiSchemeMapper schemeMapper;

    @InjectMocks
    private KpiItemService service;

    /** 为同方案内重复 / 校验通过的场景统一 stub 父方案存在. */
    private void stubSchemeExists(String schemeId) {
        PerfKpiScheme parent = new PerfKpiScheme();
        parent.setId(schemeId);
        when(schemeMapper.selectById(schemeId)).thenReturn(parent);
    }

    @Test
    @DisplayName("addItem: 父方案不存在时抛 PerfException (KPI_SCHEME_NOT_FOUND), 不查 item")
    void addItem_whenSchemeNotFound_throws() {
        when(schemeMapper.selectById("S_MISSING")).thenReturn(null);

        AddKpiItemCmd cmd = AddKpiItemCmd.builder()
                .schemeId("S_MISSING")
                .metricCode("TEST_KPI_METRIC_X")
                .weight(new BigDecimal("50.0000"))
                .operator("admin")
                .build();

        assertThatThrownBy(() -> service.addItem(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.KPI_SCHEME_NOT_FOUND));
        // 父方案校验不过, UK 预校验与插入都不应触发
        verify(itemMapper, never()).selectBySchemeAndMetric(any(), any());
        verify(itemMapper, never()).insert(any(PerfKpiItem.class));
    }

    @Test
    @DisplayName("同方案内 metricCode 重复时抛 PerfException (KPI_ITEM_DUP)")
    void addItem_whenDuplicateMetricInScheme_throws() {
        stubSchemeExists("S_DUP");
        PerfKpiItem existing = KpiTestDataBuilder.item("S_DUP", "TEST_KPI_METRIC_A");
        when(itemMapper.selectBySchemeAndMetric("S_DUP", "TEST_KPI_METRIC_A")).thenReturn(existing);

        AddKpiItemCmd cmd = AddKpiItemCmd.builder()
                .schemeId("S_DUP")
                .metricCode("TEST_KPI_METRIC_A")
                .weight(new BigDecimal("50.0000"))
                .operator("admin")
                .build();

        assertThatThrownBy(() -> service.addItem(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CODE_DUP));
        verify(itemMapper, never()).insert(any(PerfKpiItem.class));
    }

    @Test
    @DisplayName("addItem: 计算表达式与SQL表达式 都空 → KPI_ITEM_EXPR_REQUIRED(必选)")
    void addItem_noExpr_throwsExprRequired() {
        stubSchemeExists("S_E");
        when(itemMapper.selectBySchemeAndMetric("S_E", "TEST_KPI_M_E")).thenReturn(null);
        AddKpiItemCmd cmd = AddKpiItemCmd.builder()
                .schemeId("S_E").metricCode("TEST_KPI_M_E").weight(new BigDecimal("10")).operator("admin").build();
        assertThatThrownBy(() -> service.addItem(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.KPI_ITEM_EXPR_REQUIRED));
        verify(itemMapper, never()).insert(any(PerfKpiItem.class));
    }

    @Test
    @DisplayName("addItem: 计算表达式与SQL表达式 都填 → KPI_ITEM_EXPR_REQUIRED(互斥)")
    void addItem_bothExpr_throwsExprRequired() {
        stubSchemeExists("S_B");
        when(itemMapper.selectBySchemeAndMetric("S_B", "TEST_KPI_M_B")).thenReturn(null);
        AddKpiItemCmd cmd = AddKpiItemCmd.builder()
                .schemeId("S_B").metricCode("TEST_KPI_M_B").weight(new BigDecimal("10"))
                .formula("actual").sqlExpr("SELECT 1 AS kpi_value").operator("admin").build();
        assertThatThrownBy(() -> service.addItem(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.KPI_ITEM_EXPR_REQUIRED));
        verify(itemMapper, never()).insert(any(PerfKpiItem.class));
    }

    @Test
    @DisplayName("新增方案项成功: 生成 id + 默认值补齐 + mapper.insert 被调用一次")
    void addItem_whenValid_insertsWithDefaults() {
        stubSchemeExists("S_OK");
        when(itemMapper.selectBySchemeAndMetric("S_OK", "TEST_KPI_METRIC_B")).thenReturn(null);

        AddKpiItemCmd cmd = AddKpiItemCmd.builder()
                .schemeId("S_OK")
                .metricCode("TEST_KPI_METRIC_B")
                .weight(new BigDecimal("40.0000"))
                .formula("min(actual/target*100, 100)")
                .operator("admin")
                .build();

        PerfKpiItem created = service.addItem(cmd);

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getSchemeId()).isEqualTo("S_OK");
        assertThat(created.getMetricCode()).isEqualTo("TEST_KPI_METRIC_B");
        assertThat(created.getWeight()).isEqualByComparingTo("40.0000");
        // 默认值: multiplier=1, minScore=0, maxScore=999999
        assertThat(created.getMultiplier()).isEqualByComparingTo("1");
        assertThat(created.getMinScore()).isEqualByComparingTo("0");
        assertThat(created.getMaxScore()).isEqualByComparingTo("999999");
        assertThat(created.getCreatedTime()).isNotNull();

        ArgumentCaptor<PerfKpiItem> captor = ArgumentCaptor.forClass(PerfKpiItem.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("新增方案项: 指标维度 baseDim 随 cmd 固化落库, 供列表展示直接读取")
    void addItem_persistsBaseDim() {
        stubSchemeExists("S_DIM");
        when(itemMapper.selectBySchemeAndMetric("S_DIM", "TEST_KPI_METRIC_ORG")).thenReturn(null);

        AddKpiItemCmd cmd = AddKpiItemCmd.builder()
                .schemeId("S_DIM")
                .metricCode("TEST_KPI_METRIC_ORG")
                .baseDim("ORG")
                .weight(new BigDecimal("30.0000"))
                .formula("min(actual/target*100, 100)")
                .operator("admin")
                .build();

        PerfKpiItem created = service.addItem(cmd);

        assertThat(created.getBaseDim()).isEqualTo("ORG");
        ArgumentCaptor<PerfKpiItem> captor = ArgumentCaptor.forClass(PerfKpiItem.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getBaseDim()).isEqualTo("ORG");
    }

    @Test
    @DisplayName("新增方案项: SQL 表达式 sqlExpr 随 cmd 固化落库")
    void addItem_persistsSqlExpr() {
        stubSchemeExists("S_SQL");
        when(itemMapper.selectBySchemeAndMetric("S_SQL", "TEST_KPI_METRIC_SQL")).thenReturn(null);

        AddKpiItemCmd cmd = AddKpiItemCmd.builder()
                .schemeId("S_SQL")
                .metricCode("TEST_KPI_METRIC_SQL")
                .sqlExpr("SUM(#{slot1}) / NULLIF(#{slot2}, 0)")
                .weight(new BigDecimal("25.0000"))
                .operator("admin")
                .build();

        PerfKpiItem created = service.addItem(cmd);

        assertThat(created.getSqlExpr()).isEqualTo("SUM(#{slot1}) / NULLIF(#{slot2}, 0)");
        ArgumentCaptor<PerfKpiItem> captor = ArgumentCaptor.forClass(PerfKpiItem.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getSqlExpr()).isEqualTo("SUM(#{slot1}) / NULLIF(#{slot2}, 0)");
    }

    @Test
    @DisplayName("编辑方案项: 非空 sqlExpr 选择性 patch 落库")
    void updateItem_patchesSqlExpr() {
        PerfKpiItem existing = KpiTestDataBuilder.item("S_USQL", "TEST_KPI_METRIC_USQL");
        existing.setId("ID_USQL");
        when(itemMapper.selectById("ID_USQL")).thenReturn(existing);

        UpdateKpiItemCmd cmd = UpdateKpiItemCmd.builder()
                .sqlExpr("AVG(#{slot})")
                .operator("admin")
                .build();

        PerfKpiItem updated = service.updateItem("ID_USQL", cmd);

        assertThat(updated.getSqlExpr()).isEqualTo("AVG(#{slot})");
        ArgumentCaptor<PerfKpiItem> captor = ArgumentCaptor.forClass(PerfKpiItem.class);
        verify(itemMapper).updateByIdSelective(captor.capture());
        assertThat(captor.getValue().getSqlExpr()).isEqualTo("AVG(#{slot})");
    }

    @Test
    @DisplayName("updateItem: 找不到 item 时抛 KPI_ITEM_NOT_FOUND")
    void updateItem_whenNotFound_throwsNotFound() {
        when(itemMapper.selectById("NO_SUCH")).thenReturn(null);

        UpdateKpiItemCmd cmd = UpdateKpiItemCmd.builder()
                .weight(new BigDecimal("20.0000"))
                .operator("admin")
                .build();

        assertThatThrownBy(() -> service.updateItem("NO_SUCH", cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.KPI_SCHEME_NOT_FOUND));
    }

    @Test
    @DisplayName("updateItem: 选择性更新只 patch 非空字段")
    void updateItem_whenFound_selectivePatch() {
        PerfKpiItem existing = KpiTestDataBuilder.item("S_UPD", "TEST_KPI_METRIC_UPD");
        existing.setId("ID_UPD");
        when(itemMapper.selectById("ID_UPD")).thenReturn(existing);

        // 表达式必选：本次更新携带计算表达式（formula）
        UpdateKpiItemCmd cmd = UpdateKpiItemCmd.builder()
                .weight(new BigDecimal("80.0000"))
                .formula("min(actual/target*100, 100)")
                .operator("admin")
                .build();

        PerfKpiItem updated = service.updateItem("ID_UPD", cmd);

        assertThat(updated.getWeight()).isEqualByComparingTo("80.0000");
        ArgumentCaptor<PerfKpiItem> captor = ArgumentCaptor.forClass(PerfKpiItem.class);
        verify(itemMapper).updateByIdSelective(captor.capture());
        PerfKpiItem patch = captor.getValue();
        assertThat(patch.getId()).isEqualTo("ID_UPD");
        assertThat(patch.getWeight()).isEqualByComparingTo("80.0000");
        // 选中计算表达式 → formula 落库、sqlExpr 清空(空串覆盖)
        assertThat(patch.getFormula()).isEqualTo("min(actual/target*100, 100)");
        assertThat(patch.getSqlExpr()).isEmpty();
        // 其余未提供字段仍不写入 patch
        assertThat(patch.getMultiplier()).isNull();
        assertThat(patch.getMinScore()).isNull();
        assertThat(patch.getMaxScore()).isNull();
    }

    @Test
    @DisplayName("deleteItem: reason 为空白时抛 PARAM_INVALID, 不落库")
    void deleteItem_whenReasonBlank_throws() {
        assertThatThrownBy(() -> service.deleteItem("ID_ANY", "  ", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(itemMapper, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("deleteItem: item 不存在时抛 KPI_ITEM_NOT_FOUND")
    void deleteItem_whenNotFound_throws() {
        when(itemMapper.selectById("NO_SUCH")).thenReturn(null);

        assertThatThrownBy(() -> service.deleteItem("NO_SUCH", "清理无效指标", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.KPI_SCHEME_NOT_FOUND));
    }

    @Test
    @DisplayName("deleteItem: 正常路径调用 mapper.deleteById")
    void deleteItem_whenValid_deletes() {
        PerfKpiItem existing = KpiTestDataBuilder.item("S_DEL", "TEST_KPI_METRIC_DEL");
        existing.setId("ID_DEL");
        when(itemMapper.selectById("ID_DEL")).thenReturn(existing);

        service.deleteItem("ID_DEL", "指标过期需清理", "admin");

        verify(itemMapper).deleteById("ID_DEL");
    }

    @Test
    @DisplayName("listBySchemeId 透传 mapper 结果")
    void listBySchemeId_returnsMapperResult() {
        PerfKpiItem a = KpiTestDataBuilder.item("S_L", "TEST_KPI_METRIC_L1");
        PerfKpiItem b = KpiTestDataBuilder.item("S_L", "TEST_KPI_METRIC_L2");
        when(itemMapper.selectBySchemeId("S_L")).thenReturn(List.of(a, b));

        List<PerfKpiItem> list = service.listBySchemeId("S_L");
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("getById: 不存在抛 KPI_ITEM_NOT_FOUND")
    void getById_whenNotFound_throws() {
        when(itemMapper.selectById("NO")).thenReturn(null);
        assertThatThrownBy(() -> service.getById("NO"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.KPI_SCHEME_NOT_FOUND));
    }
}
