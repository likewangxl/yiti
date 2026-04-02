package com.bank.branch.platform.common.web;

import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PageRequestTest {

    @Test
    void defaultValuesShouldBeCorrect() {
        PageRequest req = new PageRequest();
        assertEquals(1, req.getPageNo());
        assertEquals(20, req.getPageSize());
        assertEquals("createdTime", req.getSortBy());
        assertEquals("desc", req.getSortDir());
    }

    @Test
    void offsetShouldCalculateCorrectly() {
        PageRequest req = new PageRequest();
        req.setPageNo(3);
        req.setPageSize(10);
        assertEquals(20, req.getOffset());
    }

    @Test
    void offsetForFirstPageShouldBeZero() {
        assertEquals(0, new PageRequest().getOffset());
    }

    @Test
    void validateSortByShouldPassForAllowedField() {
        PageRequest req = new PageRequest();
        req.setSortBy("createdTime");
        assertDoesNotThrow(() -> req.validateSortBy(Set.of("createdTime", "name")));
    }

    @Test
    void validateSortByShouldThrowForDisallowedField() {
        PageRequest req = new PageRequest();
        req.setSortBy("hackerField");
        BizException ex = assertThrows(BizException.class,
            () -> req.validateSortBy(Set.of("createdTime", "name")));
        assertEquals("PAGE_001", ex.getCode());
    }
}
