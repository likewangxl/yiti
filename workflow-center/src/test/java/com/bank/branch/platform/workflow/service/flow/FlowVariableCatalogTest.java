package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowVariableDTO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FlowVariableCatalogTest {

    private final FlowVariableCatalog c = new FlowVariableCatalog();

    @Test
    void allocAdjust_hasExpectedVars() {
        // ALLOC_ADJUST：bizKind / allocDim + 系统注入 startOrgLevel + 经办产出路由变量 corpRouteTo / finRouteTo
        assertThat(c.variables("ALLOC_ADJUST")).extracting(FlowVariableDTO::getField)
                .containsExactlyInAnyOrder("bizKind", "allocDim", "startOrgLevel", "corpRouteTo", "finRouteTo");
    }

    @Test
    void targetAdjust_hasExpectedVars() {
        // TARGET_ADJUST：subjectType / subjectId / cycleKey + 系统注入 startOrgLevel
        assertThat(c.variables("TARGET_ADJUST")).extracting(FlowVariableDTO::getField)
                .containsExactlyInAnyOrder("subjectType", "subjectId", "cycleKey", "startOrgLevel");
    }

    @Test
    void startOrgLevel_availableForBranching() {
        // 发起人机构级别变量供网关按 2级/3级机构分流，两条业务流程均可用
        assertThat(c.fields("ALLOC_ADJUST")).contains("startOrgLevel");
        assertThat(c.fields("TARGET_ADJUST")).contains("startOrgLevel");
        FlowVariableDTO v = c.variables("ALLOC_ADJUST").stream()
                .filter(x -> "startOrgLevel".equals(x.getField())).findFirst().orElseThrow();
        assertThat(v.getLabel()).isEqualTo("发起人机构级别");
        assertThat(v.getType()).isEqualTo("number");
    }

    @Test
    void unknownBiz_empty() {
        assertThat(c.variables("NOPE")).isEmpty();
        assertThat(c.fields("NOPE")).isEmpty();
    }

    @Test
    void approverVariables_allocAdjust_hasOriginalOwner() {
        // VAR 审批人可选的「名单类流程变量」：原业绩所属人 originalOwnerEmpIds
        assertThat(c.approverVariables("ALLOC_ADJUST")).extracting(FlowVariableDTO::getField)
                .contains("originalOwnerEmpIds");
        FlowVariableDTO v = c.approverVariables("ALLOC_ADJUST").stream()
                .filter(x -> "originalOwnerEmpIds".equals(x.getField())).findFirst().orElseThrow();
        assertThat(v.getLabel()).isEqualTo("原业绩所属人");
    }

    @Test
    void approverVariables_unknownBiz_empty() {
        assertThat(c.approverVariables("NOPE")).isEmpty();
    }

    @Test
    void fields_forValidator() {
        assertThat(c.fields("ALLOC_ADJUST")).contains("bizKind");
        assertThat(c.fields("TARGET_ADJUST")).contains("subjectType");
    }
}
