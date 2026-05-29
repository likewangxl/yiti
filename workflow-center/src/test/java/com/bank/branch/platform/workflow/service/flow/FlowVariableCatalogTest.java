package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowVariableDTO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FlowVariableCatalogTest {

    private final FlowVariableCatalog c = new FlowVariableCatalog();

    @Test
    void allocAdjust_hasExpectedVars() {
        // ALLOC_ADJUST 实际写入变量：bizKind / allocDim（无 custType）
        assertThat(c.variables("ALLOC_ADJUST")).extracting(FlowVariableDTO::getField)
                .containsExactlyInAnyOrder("bizKind", "allocDim");
    }

    @Test
    void targetAdjust_hasExpectedVars() {
        // TARGET_ADJUST 实际写入变量：subjectType / subjectId / cycleKey（无 bizKind/custType）
        assertThat(c.variables("TARGET_ADJUST")).extracting(FlowVariableDTO::getField)
                .containsExactlyInAnyOrder("subjectType", "subjectId", "cycleKey");
    }

    @Test
    void unknownBiz_empty() {
        assertThat(c.variables("NOPE")).isEmpty();
        assertThat(c.fields("NOPE")).isEmpty();
    }

    @Test
    void fields_forValidator() {
        assertThat(c.fields("ALLOC_ADJUST")).contains("bizKind");
        assertThat(c.fields("TARGET_ADJUST")).contains("subjectType");
    }
}
