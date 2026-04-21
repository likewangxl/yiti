package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.entity.PortalNav;
import com.bank.branch.platform.portal.service.NavService;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminNavController 集成测试 -- 管理端 CRUD 接口
 *
 * <p>使用 @MockBean 替换 NavService，避免拖入数据库依赖。
 * 验证 POST/PUT/DELETE /api/admin/nav 接口的请求响应格式。</p>
 */
class AdminNavControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    // ========== B.2 createNav ==========

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void createNavShouldReturn200WithNavId() throws Exception {
        PortalNav resultEntity = new PortalNav();
        resultEntity.setId("NEW_NAV_001");
        resultEntity.setNavName("核心系统");
        when(navService.createNav(any())).thenReturn(resultEntity);

        String body = "{\"navName\":\"核心系统\",\"navUrl\":\"https://core.bank.com\",\"navIcon\":\"icon-core\",\"navCategory\":\"业务系统\",\"sortOrder\":1}";
        mockMvc.perform(post("/api/admin/nav")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("NEW_NAV_001"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void createNavShouldReturn400WhenNavNameMissing() throws Exception {
        String body = "{\"navUrl\":\"https://core.bank.com\"}";
        mockMvc.perform(post("/api/admin/nav")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void createNavShouldReturnErrorWhenDuplicate() throws Exception {
        when(navService.createNav(any()))
                .thenThrow(new BizException("PORTAL-40903", "导航名称在同一分类下已存在"));

        String body = "{\"navName\":\"核心系统\",\"navUrl\":\"https://core.bank.com\",\"navCategory\":\"业务系统\"}";
        mockMvc.perform(post("/api/admin/nav")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PORTAL-40903"));
    }

    // ========== B.3 updateNav ==========

    @Test
    @WithMockEmpContext(empId = "E10001")
    void updateNavShouldReturn200() throws Exception {
        doNothing().when(navService).updateNav(eq("nav-001"), any());

        String body = "{\"navName\":\"更新后名称\",\"navUrl\":\"https://new.bank.com\"}";
        mockMvc.perform(put("/api/admin/nav/nav-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void updateNavShouldReturnErrorWhenNotFound() throws Exception {
        doThrow(new BizException("PORTAL-40001", "导航不存在"))
                .when(navService).updateNav(eq("nav-nonexist"), any());

        String body = "{\"navName\":\"不存在的导航\"}";
        mockMvc.perform(put("/api/admin/nav/nav-nonexist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PORTAL-40001"));
    }

    // ========== B.4 deleteNav ==========

    @Test
    @WithMockEmpContext(empId = "E10001")
    void deleteNavShouldReturn200() throws Exception {
        doNothing().when(navService).deleteNav(eq("nav-001"));

        mockMvc.perform(delete("/api/admin/nav/nav-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void deleteNavShouldReturnErrorWhenNotFound() throws Exception {
        doThrow(new BizException("PORTAL-40001", "导航不存在"))
                .when(navService).deleteNav(eq("nav-nonexist"));

        mockMvc.perform(delete("/api/admin/nav/nav-nonexist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PORTAL-40001"));
    }

    // ========== B.5 batchSort ==========

    @Test
    @WithMockEmpContext(empId = "E10001")
    void batchSortShouldReturn200() throws Exception {
        doNothing().when(navService).batchSort(any());

        String body = "[{\"id\":\"nav-001\",\"sortOrder\":2},{\"id\":\"nav-002\",\"sortOrder\":1}]";
        mockMvc.perform(put("/api/admin/nav/sort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}
