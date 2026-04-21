package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.customer.service.TagCustomerService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CustomerTagController 集成测试（TDD RED 阶段）。
 * <p>
 * 验证 POST /api/customers/{id}/tags（追加打标）
 * 和 DELETE /api/customers/{id}/tags/{tagId}（取消单标签）的 REST 端点行为。
 * </p>
 */
class CustomerTagControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TagCustomerService tagCustomerService;

    // ==================== POST /api/customers/{id}/tags ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void addTags_shouldReturnAddedCount() throws Exception {
        // given: 两个标签 ID，service 返回新增数量 2
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(tagCustomerService.addTagsToCustomer(eq("C001"), anyList(), eq("E10001")))
                .thenReturn(2);

        String body = "{\"tagIds\":[\"tag-001\",\"tag-002\"]}";

        // when & then
        mockMvc.perform(post("/api/customers/C001/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void addTags_shouldReturnZeroWhenAllExist() throws Exception {
        // given: service 返回 0（全部已存在）
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(tagCustomerService.addTagsToCustomer(eq("C001"), anyList(), anyString()))
                .thenReturn(0);

        String body = "{\"tagIds\":[\"tag-001\"]}";

        // when & then
        mockMvc.perform(post("/api/customers/C001/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void addTags_shouldReturn400WhenTagIdsEmpty() throws Exception {
        // given: 空 tagIds 列表，@NotEmpty 校验失败
        String body = "{\"tagIds\":[]}";

        // when & then
        mockMvc.perform(post("/api/customers/C001/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void addTags_shouldReturn400WhenTagIdsMissing() throws Exception {
        // given: tagIds 字段缺失
        String body = "{}";

        // when & then
        mockMvc.perform(post("/api/customers/C001/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /api/customers/{id}/tags/{tagId} ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void removeTag_shouldReturnTrueWhenRemoved() throws Exception {
        // given: service 返回 true（删除成功）
        when(tagCustomerService.removeTagFromCustomer("C001", "tag-001")).thenReturn(true);

        // when & then
        mockMvc.perform(delete("/api/customers/C001/tags/tag-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void removeTag_shouldReturnFalseWhenNotExist() throws Exception {
        // given: service 返回 false（关联不存在）
        when(tagCustomerService.removeTagFromCustomer("C001", "tag-999")).thenReturn(false);

        // when & then
        mockMvc.perform(delete("/api/customers/C001/tags/tag-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(false));
    }
}
