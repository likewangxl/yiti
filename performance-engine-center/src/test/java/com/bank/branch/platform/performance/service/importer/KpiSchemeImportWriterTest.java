package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.service.importer.impl.KpiSchemeImportStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiSchemeImportWriter 落库层单测（2026-06-17）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>方案不存在 → 新建 ACTIVE 方案 + upsertBatch 收到全部 item，schemeId/兜底默认回填正确</li>
 *   <li>方案已存在 → 复用既有 id，never 调 scheme insert（忽略不改名不改角色范围）</li>
 * </ul>
 */
class KpiSchemeImportWriterTest {

    private PerfKpiSchemeMapper schemeMapper;
    private PerfKpiItemMapper itemMapper;
    private KpiSchemeImportWriter writer;

    @BeforeEach
    void setUp() {
        schemeMapper = mock(PerfKpiSchemeMapper.class);
        itemMapper = mock(PerfKpiItemMapper.class);
        writer = new KpiSchemeImportWriter(schemeMapper, itemMapper);
    }

    @Test
    @DisplayName("方案不存在 → 新建 ACTIVE 方案，upsertBatch 收到 item，兜底默认回填")
    void write_newScheme_insertsActiveAndUpserts() {
        when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(null);

        List<PerfKpiItem> items = new ArrayList<>(List.of(item(null), item(new BigDecimal("10"))));
        List<String> codes = List.of("KPI_A", "KPI_A");
        Map<String, KpiSchemeImportStrategy.SchemeInfo> infos = new LinkedHashMap<>();
        infos.put("KPI_A", new KpiSchemeImportStrategy.SchemeInfo("方案A", "R_FIN_MGR,R_TELLER"));

        writer.write(items, codes, infos, "admin");

        ArgumentCaptor<PerfKpiScheme> schemeCap = ArgumentCaptor.forClass(PerfKpiScheme.class);
        verify(schemeMapper, times(1)).insert(schemeCap.capture());
        PerfKpiScheme scheme = schemeCap.getValue();
        assertThat(scheme.getSchemeCode()).isEqualTo("KPI_A");
        assertThat(scheme.getSchemeName()).isEqualTo("方案A");
        assertThat(scheme.getEmpRoleScope()).isEqualTo("R_FIN_MGR,R_TELLER");
        assertThat(scheme.getStatus()).isEqualTo("ACTIVE");
        assertThat(scheme.getCycleType()).isEqualTo("YEARLY");
        assertThat(scheme.getOpenDetail()).isZero();
        assertThat(scheme.getId()).hasSize(32);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfKpiItem>> itemCap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper, times(1)).upsertBatch(itemCap.capture());
        List<PerfKpiItem> upserted = itemCap.getValue();
        assertThat(upserted).hasSize(2);
        for (PerfKpiItem it : upserted) {
            assertThat(it.getId()).hasSize(32);
            assertThat(it.getSchemeId()).isEqualTo(scheme.getId());
            assertThat(it.getMultiplier()).isEqualByComparingTo("1");
            assertThat(it.getCreatedTime()).isNotNull();
        }
        // 第一项 weight 为 null → 兜底 0；第二项保留 10
        assertThat(upserted.get(0).getWeight()).isEqualByComparingTo("0");
        assertThat(upserted.get(0).getMinScore()).isEqualByComparingTo("0");
        assertThat(upserted.get(0).getMaxScore()).isEqualByComparingTo("999999");
        assertThat(upserted.get(1).getWeight()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("方案已存在 → 复用既有 id，never 调 scheme insert")
    void write_existingScheme_reusesIdNoInsert() {
        PerfKpiScheme exist = new PerfKpiScheme();
        exist.setId("EXIST_SCHEME_ID_00000000000000");
        exist.setSchemeCode("KPI_A");
        exist.setSchemeName("旧名称不改");
        exist.setEmpRoleScope("R_OLD");
        when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(exist);

        List<PerfKpiItem> items = new ArrayList<>(List.of(item(new BigDecimal("10"))));
        Map<String, KpiSchemeImportStrategy.SchemeInfo> infos = new LinkedHashMap<>();
        infos.put("KPI_A", new KpiSchemeImportStrategy.SchemeInfo("新名称会被忽略", "R_NEW"));

        writer.write(items, List.of("KPI_A"), infos, "admin");

        verify(schemeMapper, never()).insert(any(PerfKpiScheme.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfKpiItem>> itemCap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper, times(1)).upsertBatch(itemCap.capture());
        assertThat(itemCap.getValue().get(0).getSchemeId()).isEqualTo("EXIST_SCHEME_ID_00000000000000");
    }

    private static PerfKpiItem item(BigDecimal weight) {
        PerfKpiItem item = new PerfKpiItem();
        item.setMetricCode("M_0001");
        item.setBaseDim("EMP");
        item.setFormula("x");
        item.setWeight(weight);
        return item;
    }
}
