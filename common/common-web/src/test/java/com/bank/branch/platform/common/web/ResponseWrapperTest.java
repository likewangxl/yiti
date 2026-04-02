package com.bank.branch.platform.common.web;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResponseWrapperTest {

    @Test
    void successWithDataShouldSetCodeZero() {
        ResponseWrapper<String> result = ResponseWrapper.success("hello");
        assertEquals("0", result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("hello", result.getData());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void successWithoutDataShouldReturnVoid() {
        ResponseWrapper<Void> result = ResponseWrapper.success();
        assertEquals("0", result.getCode());
        assertNull(result.getData());
    }

    @Test
    void pageShouldSetPageResult() {
        PageResult<String> pageResult = PageResult.of(1, 20, 100L, List.of("a", "b"));
        ResponseWrapper<String> result = ResponseWrapper.page(pageResult);
        assertEquals("0", result.getCode());
        assertNotNull(result.getPage());
        assertEquals(100L, result.getPage().getTotal());
        assertNull(result.getData());
    }

    @Test
    void errorShouldSetCodeAndMessage() {
        ResponseWrapper<?> result = ResponseWrapper.error("AUTH-40101", "用户名或密码错误");
        assertEquals("AUTH-40101", result.getCode());
        assertEquals("用户名或密码错误", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void timestampShouldNotBeEmpty() {
        ResponseWrapper<Void> result = ResponseWrapper.success();
        assertNotNull(result.getTimestamp());
        assertFalse(result.getTimestamp().isEmpty());
    }
}
