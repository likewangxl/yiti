package com.bank.branch.platform.common.web;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    @Test
    void ofShouldBuildCorrectResult() {
        PageResult<String> result = PageResult.of(1, 20, 100L, List.of("a", "b"));
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
        assertEquals(100L, result.getTotal());
        assertEquals(2, result.getRecords().size());
    }

    @Test
    void getTotalPagesShouldCalculateCorrectly() {
        assertEquals(5, PageResult.of(1, 20, 100L, List.of()).getTotalPages());
    }

    @Test
    void getTotalPagesShouldRoundUp() {
        assertEquals(6, PageResult.of(1, 20, 101L, List.of()).getTotalPages());
    }

    @Test
    void getTotalPagesShouldReturnOneForSinglePage() {
        assertEquals(1, PageResult.of(1, 20, 5L, List.of("a")).getTotalPages());
    }
}
