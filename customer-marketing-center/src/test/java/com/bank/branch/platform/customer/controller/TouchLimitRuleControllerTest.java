package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.dto.resp.TouchLimitRuleRespDTO;
import com.bank.branch.platform.customer.service.TouchLimitRuleService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 触达周期规则 REST 接口契约测试。 */
class TouchLimitRuleControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TouchLimitRuleService service;

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPage_shouldUseDefaultPagingAndReturnRuleRows() throws Exception {
        TouchLimitRuleRespDTO row = new TouchLimitRuleRespDTO();
        row.setTagId("tag-1");
        row.setTagName("重点客户");
        row.setCycleUnit("MONTH");
        row.setMaxTouches(5);
        when(service.listPage(isNull(), eq(1), eq(20)))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(row)));

        mockMvc.perform(get("/api/touch-limit-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].tagId").value("tag-1"))
                .andExpect(jsonPath("$.page.records[0].cycleUnit").value("MONTH"))
                .andExpect(jsonPath("$.page.records[0].maxTouches").value(5));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void update_shouldPassCurrentOperatorAndRequestToService() throws Exception {
        doNothing().when(service).updateRule(eq("tag-1"), any(), eq("E10001"));

        mockMvc.perform(put("/api/touch-limit-rules/tag-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cycleUnit\":\"WEEK\",\"maxTouches\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(service).updateRule(eq("tag-1"), any(), eq("E10001"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void update_shouldRejectMaxTouchesOutsideBusinessRange() throws Exception {
        mockMvc.perform(put("/api/touch-limit-rules/tag-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cycleUnit\":\"WEEK\",\"maxTouches\":10000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void update_shouldRejectMissingMaxTouches() throws Exception {
        mockMvc.perform(put("/api/touch-limit-rules/tag-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cycleUnit\":\"WEEK\"}"))
                .andExpect(status().isBadRequest());
    }
}
