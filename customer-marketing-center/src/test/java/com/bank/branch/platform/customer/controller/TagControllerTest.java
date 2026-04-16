package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.service.TagService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TagController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest 基类，通过 @MockBean TagService 替换真实业务逻辑。
 * </p>
 */
class TagControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TagService tagService;

    // ==================== GET /api/tags ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPage_shouldReturn200() throws Exception {
        CustTag tag = new CustTag();
        tag.setId("tag-001");
        tag.setTagName("VIP客户");
        tag.setTagCode("VIP_CUSTOMER");
        PageResult<CustTag> page = PageResult.of(1, 20, 1L, List.of(tag));

        when(tagService.listPage(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/tags")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].tagCode").value("VIP_CUSTOMER"));
    }

    // ==================== GET /api/tags/enabled ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listEnabled_shouldReturn200() throws Exception {
        CustTag tag1 = new CustTag();
        tag1.setId("tag-001");
        tag1.setTagName("VIP客户");
        tag1.setStatus("ACTIVE");

        CustTag tag2 = new CustTag();
        tag2.setId("tag-002");
        tag2.setTagName("潜力客户");
        tag2.setStatus("ACTIVE");

        when(tagService.listEnabled()).thenReturn(Arrays.asList(tag1, tag2));

        mockMvc.perform(get("/api/tags/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ==================== POST /api/tags ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void create_shouldReturn200() throws Exception {
        CustTag createdTag = new CustTag();
        createdTag.setId("new-tag-001");
        createdTag.setTagName("新标签");
        createdTag.setTagCode("NEW_TAG");

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(tagService.createTag(anyString(), anyString(), any(), any(), any(), anyString()))
                .thenReturn(createdTag);

        String body = "{\"tagName\":\"新标签\",\"tagCode\":\"NEW_TAG\",\"description\":\"测试标签\"}";

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("new-tag-001"));
    }

    // ==================== PUT /api/tags/{id} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void update_shouldReturn200() throws Exception {
        CustTag updatedTag = new CustTag();
        updatedTag.setId("tag-001");
        updatedTag.setTagName("更新标签名");

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(tagService.updateTag(eq("tag-001"), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(updatedTag);

        String body = "{\"tagName\":\"更新标签名\",\"tagCode\":\"VIP_CUSTOMER\"}";

        mockMvc.perform(put("/api/tags/tag-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== PUT /api/tags/{id}/status ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void toggleStatus_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(tagService).toggleStatus(eq("tag-001"), eq("DISABLED"), anyString());

        String body = "{\"status\":\"DISABLED\"}";

        mockMvc.perform(put("/api/tags/tag-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}
