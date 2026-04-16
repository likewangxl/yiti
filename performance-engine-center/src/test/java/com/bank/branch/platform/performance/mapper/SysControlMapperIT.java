package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import com.bank.branch.platform.performance.support.SysControlTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SysControlMapper 单线程集成测试.
 * <p>继承 PerformanceMapperTestBase 利用 @Transactional + @Rollback 自动回滚,
 * 所有前缀 TEST_SC_ 的数据在测试结束后自动撤销.
 */
class SysControlMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private SysControlMapper sysControlMapper;

    @Test
    @DisplayName("insert 后 selectById 应返回完全相同的数据")
    void insert_then_selectById_shouldMatch() {
        // Given
        SysControl sc = SysControlTestDataBuilder.buildTest(
                "001", "EMP", LocalDate.of(2099, 1, 1), "V2099_01", 1);

        // When
        int inserted = sysControlMapper.insert(sc);
        SysControl got = sysControlMapper.selectById("TEST_SC_001");

        // Then
        assertThat(inserted).isEqualTo(1);
        assertThat(got).isNotNull();
        assertThat(got.getId()).isEqualTo("TEST_SC_001");
        assertThat(got.getScopeDim()).isEqualTo("EMP");
        assertThat(got.getLatestDataDate()).isEqualTo(LocalDate.of(2099, 1, 1));
        assertThat(got.getCurrentVersion()).isEqualTo("V2099_01");
        assertThat(got.getIsValid()).isEqualTo(1);
        assertThat(got.getCreatedTime()).isNotNull();
        assertThat(got.getUpdatedTime()).isNotNull();
    }

    @Test
    @DisplayName("selectByScopeAndValid 仅返回 is_valid=1 的记录")
    void selectByScopeAndValid_shouldReturnIsValidOne() {
        // Given: 同一 scope_dim 下插入 2 条 (旧失效 + 新有效)
        SysControl old = SysControlTestDataBuilder.buildTest(
                "002", "EMP", LocalDate.of(2099, 2, 1), "V2099_02_OLD", 0);
        SysControl curr = SysControlTestDataBuilder.buildTest(
                "003", "EMP", LocalDate.of(2099, 2, 2), "V2099_02_CURR", 1);
        sysControlMapper.insert(old);
        sysControlMapper.insert(curr);

        // When: 仅查 is_valid=1
        SysControl got = sysControlMapper.selectByScopeAndValid("EMP");

        // Then
        assertThat(got).isNotNull();
        assertThat(got.getId()).isEqualTo("TEST_SC_003");
        assertThat(got.getIsValid()).isEqualTo(1);
        assertThat(got.getCurrentVersion()).isEqualTo("V2099_02_CURR");
    }

    @Test
    @DisplayName("同一 scope_dim + latest_data_date 再次插入触发 UK 冲突")
    void updateIsValid_whenUkViolation_shouldThrowDuplicateKey() {
        // Given: 第一条插入成功
        SysControl first = SysControlTestDataBuilder.buildTest(
                "004", "ORG", LocalDate.of(2099, 3, 1), "V2099_03_1", 1);
        sysControlMapper.insert(first);

        // When: 再插入一条同 scope_dim + latest_data_date (UK 冲突)
        SysControl dup = SysControlTestDataBuilder.buildTest(
                "005", "ORG", LocalDate.of(2099, 3, 1), "V2099_03_2", 1);

        // Then
        assertThatThrownBy(() -> sysControlMapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("listByScope 按 latest_data_date 倒序返回全部历史版本")
    void listByScope_shouldOrderByLatestDataDateDesc() {
        // Given: 插入 3 条不同日期的同 scope 记录
        SysControl r1 = SysControlTestDataBuilder.buildTest(
                "006", "CUST", LocalDate.of(2099, 4, 1), "V1", 0);
        SysControl r2 = SysControlTestDataBuilder.buildTest(
                "007", "CUST", LocalDate.of(2099, 4, 3), "V3", 1);
        SysControl r3 = SysControlTestDataBuilder.buildTest(
                "008", "CUST", LocalDate.of(2099, 4, 2), "V2", 0);
        sysControlMapper.insert(r1);
        sysControlMapper.insert(r2);
        sysControlMapper.insert(r3);

        // When
        List<SysControl> list = sysControlMapper.listByScope("CUST", 10);

        // Then: 倒序 V3 > V2 > V1
        assertThat(list).hasSize(3);
        assertThat(list.get(0).getLatestDataDate()).isEqualTo(LocalDate.of(2099, 4, 3));
        assertThat(list.get(1).getLatestDataDate()).isEqualTo(LocalDate.of(2099, 4, 2));
        assertThat(list.get(2).getLatestDataDate()).isEqualTo(LocalDate.of(2099, 4, 1));
    }

    @Test
    @DisplayName("updateIsValid 可翻转 is_valid 状态")
    void updateIsValid_shouldToggleFlag() {
        // Given
        SysControl sc = SysControlTestDataBuilder.buildTest(
                "009", "EMP", LocalDate.of(2099, 5, 1), "V_TOGGLE", 1);
        sysControlMapper.insert(sc);

        // When
        int affected = sysControlMapper.updateIsValid("TEST_SC_009", 0);

        // Then
        assertThat(affected).isEqualTo(1);
        SysControl after = sysControlMapper.selectById("TEST_SC_009");
        assertThat(after.getIsValid()).isEqualTo(0);
    }
}
