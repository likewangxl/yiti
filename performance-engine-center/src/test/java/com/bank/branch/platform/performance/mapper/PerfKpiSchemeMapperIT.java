package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.support.KpiTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PerfKpiSchemeMapper 集成测试.
 *
 * <p>覆盖 DoD 硬性场景：
 * <ul>
 *   <li>insert_whenSchemeCodeDup_throwsDuplicateKey：uk_scheme_code 唯一键冲突</li>
 * </ul>
 * 以及基础路径：insert/selectById / selectBySchemeCode / selectByCondition / countByCondition /
 * updateByIdSelective / updateStatusById / deleteById。
 */
class PerfKpiSchemeMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfKpiSchemeMapper mapper;

    @Test
    @DisplayName("insert 后可按 id 查回 KPI 方案")
    void insertAndSelectById_ok() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("SEL_001");

        mapper.insert(scheme);
        PerfKpiScheme loaded = mapper.selectById(scheme.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getSchemeCode()).isEqualTo("TEST_KPI_SEL_001");
        assertThat(loaded.getSchemeName()).isEqualTo("测试KPI方案-SEL_001");
        assertThat(loaded.getCycleType()).isEqualTo("MONTHLY");
        assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("DoD: 重复 scheme_code 触发唯一键冲突")
    void insert_whenSchemeCodeDup_throwsDuplicateKey() {
        PerfKpiScheme first = KpiTestDataBuilder.scheme("DUP_CODE");
        mapper.insert(first);
        // 构造同 scheme_code 但不同 id 的记录，触发 uk_scheme_code
        PerfKpiScheme dup = KpiTestDataBuilder.scheme("DUP_CODE");

        assertThatThrownBy(() -> mapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectBySchemeCode 不存在时返回 null")
    void selectBySchemeCode_whenNotExists_returnsNull() {
        assertThat(mapper.selectBySchemeCode("TEST_KPI_NO_SUCH")).isNull();
    }

    @Test
    @DisplayName("selectBySchemeCode 存在时可查回对应方案")
    void selectBySchemeCode_whenExists_ok() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("BY_CODE");
        mapper.insert(scheme);

        PerfKpiScheme loaded = mapper.selectBySchemeCode("TEST_KPI_BY_CODE");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(scheme.getId());
    }

    @Test
    @DisplayName("selectByCondition 关键字可匹配编码或名称")
    void selectByCondition_withKeyword_matchesCodeOrName() {
        mapper.insert(KpiTestDataBuilder.scheme("KW_A"));
        mapper.insert(KpiTestDataBuilder.scheme("KW_B"));

        List<PerfKpiScheme> list = mapper.selectByCondition("MONTHLY", "ACTIVE", "KW_", 0, 10);

        assertThat(list).extracting(PerfKpiScheme::getSchemeCode)
                .contains("TEST_KPI_KW_A", "TEST_KPI_KW_B");
    }

    @Test
    @DisplayName("countByCondition 与条件查询保持一致")
    void countByCondition_filtersSameAsSelect() {
        mapper.insert(KpiTestDataBuilder.scheme("CNT_A"));
        mapper.insert(KpiTestDataBuilder.scheme("CNT_B"));
        PerfKpiScheme disabled = KpiTestDataBuilder.scheme("CNT_C");
        disabled.setStatus("DISABLED");
        mapper.insert(disabled);

        long count = mapper.countByCondition("MONTHLY", "ACTIVE", "CNT_");

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("updateStatusById 会更新状态和更新人")
    void updateStatusById_changesStatusAndUpdatedBy() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("ST_T");
        mapper.insert(scheme);

        int rows = mapper.updateStatusById(scheme.getId(), "DISABLED", "test-admin");

        assertThat(rows).isEqualTo(1);
        PerfKpiScheme loaded = mapper.selectById(scheme.getId());
        assertThat(loaded.getStatus()).isEqualTo("DISABLED");
        assertThat(loaded.getUpdatedBy()).isEqualTo("test-admin");
    }

    @Test
    @DisplayName("updateByIdSelective 只更新非空字段")
    void updateByIdSelective_onlyUpdatesNonNullFields() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("UPD_T");
        mapper.insert(scheme);

        PerfKpiScheme patch = new PerfKpiScheme();
        patch.setId(scheme.getId());
        patch.setSchemeName("新名称");
        patch.setUpdatedBy("patcher");
        int rows = mapper.updateByIdSelective(patch);

        assertThat(rows).isEqualTo(1);
        PerfKpiScheme loaded = mapper.selectById(scheme.getId());
        assertThat(loaded.getSchemeName()).isEqualTo("新名称");
        assertThat(loaded.getSchemeCode()).isEqualTo("TEST_KPI_UPD_T");
        assertThat(loaded.getUpdatedBy()).isEqualTo("patcher");
    }

    @Test
    @DisplayName("deleteById 可删除方案")
    void deleteById_ok() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("DEL_T");
        mapper.insert(scheme);

        int rows = mapper.deleteById(scheme.getId());

        assertThat(rows).isEqualTo(1);
        assertThat(mapper.selectById(scheme.getId())).isNull();
    }
}
