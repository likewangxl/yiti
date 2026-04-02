package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.BizException;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestValidatorTest {

    @Data
    static class TestDTO {
        @NotBlank(message = "名称不能为空")
        private String name;
    }

    @Test
    void shouldPassForValidObject() {
        TestDTO dto = new TestDTO();
        dto.setName("test");
        assertDoesNotThrow(() -> RequestValidator.validate(dto));
    }

    @Test
    void shouldThrowBizExceptionForInvalidObject() {
        TestDTO dto = new TestDTO();
        BizException ex = assertThrows(BizException.class, () -> RequestValidator.validate(dto));
        assertEquals("VALID_001", ex.getCode());
        assertTrue(ex.getMessage().contains("名称不能为空"));
    }
}
