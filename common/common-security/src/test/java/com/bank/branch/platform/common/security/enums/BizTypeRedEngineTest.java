package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 红色引擎 BizType 常量存在性守护 */
class BizTypeRedEngineTest {
    @Test
    void redEngineConstantExists() {
        assertEquals("RED_ENGINE", BizType.RED_ENGINE.getCode());
        assertEquals("党建红色引擎", BizType.RED_ENGINE.getDescription());
    }
}
