package com.bank.branch.platform.performance.eval.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EvalAssignImportAcceptedDTO} 单元测试：异步导入受理响应体（batchId + status）。
 */
class EvalAssignImportAcceptedDTOTest {

    @Test
    @DisplayName("全参构造：batchId / status 正确赋值")
    void allArgsConstructor_setsFields() {
        EvalAssignImportAcceptedDTO dto = new EvalAssignImportAcceptedDTO(99L, 3);
        assertThat(dto.getBatchId()).isEqualTo(99L);
        assertThat(dto.getStatus()).isEqualTo(3);
    }
}
