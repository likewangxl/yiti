package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.enums.SupportScenario;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SupportScenarioRouter 单元测试（TDD）。
 * 共 6 个测试用例。
 */
@ExtendWith(MockitoExtension.class)
class SupportScenarioRouterTest {

    @InjectMocks
    private SupportScenarioRouter router;

    @Test
    void route_withProducts_noOtherDemand_shouldReturnA() {
        // given: 有产品列表，无其他需求
        SupportScenario result = router.route(List.of("P001"), null, null);
        assertThat(result).isEqualTo(SupportScenario.A);
    }

    @Test
    void route_noProducts_withOtherDemand_shouldReturnB() {
        // given: 无产品，有其他需求，有部门ID
        SupportScenario result = router.route(Collections.emptyList(), "需要咨询债券", "DEPT001");
        assertThat(result).isEqualTo(SupportScenario.B);
    }

    @Test
    void route_withProducts_withOtherDemand_shouldReturnB() {
        // given: 同时有产品和其他需求 -> 路由到B，需要部门ID
        SupportScenario result = router.route(List.of("P001"), "补充说明", "DEPT001");
        assertThat(result).isEqualTo(SupportScenario.B);
    }

    @Test
    void route_noProducts_noOtherDemand_shouldThrowBIZ40903() {
        // given: 既无产品也无其他需求 -> BIZ-40903
        assertThatThrownBy(() -> router.route(null, null, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40903");
    }

    @Test
    void route_scenarioB_noDeptId_shouldThrowBIZ40902() {
        // given: 场景B（有其他需求），但未传 supportDeptId
        assertThatThrownBy(() -> router.route(null, "咨询需求", null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40902");
    }

    @Test
    void route_scenarioB_withDeptId_shouldReturnB() {
        // given: 空产品 + 有需求 + 有部门ID
        SupportScenario result = router.route(Collections.emptyList(), "债券咨询", "DEPT002");
        assertThat(result).isEqualTo(SupportScenario.B);
    }
}
