package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.portal.api.dto.NavDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavGroupItem;
import com.bank.branch.platform.portal.controller.dto.nav.NavGroupRespDTO;
import com.bank.branch.platform.portal.service.NavService;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NavController 集成测试 -- 只读公开接口
 *
 * <p>使用 @MockBean 替换 NavService，避免拖入数据库依赖。
 * 验证 GET /api/nav 接口的分组响应格式。</p>
 */
class NavControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockEmpContext(empId = "E10001", roleCodes = {"R_RM"})
    void listGroupedShouldReturn200WithGroups() throws Exception {
        NavGroupRespDTO resp = buildGroupResp();
        when(navService.listGrouped(any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/nav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.groups").isArray())
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].category").value("业务系统"))
                .andExpect(jsonPath("$.data.groups[0].navs[0].navName").value("核心系统"));
    }

    @Test
    @WithMockEmpContext
    void listGroupedShouldReturn200WithEmptyGroupsWhenNoNavs() throws Exception {
        NavGroupRespDTO resp = new NavGroupRespDTO();
        resp.setGroups(Collections.emptyList());
        when(navService.listGrouped(any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/nav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.groups").isArray())
                .andExpect(jsonPath("$.data.groups").isEmpty());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listGroupedWithCategoryFilterShouldReturn200() throws Exception {
        NavGroupRespDTO resp = new NavGroupRespDTO();
        NavGroupItem item = new NavGroupItem();
        item.setCategory("业务系统");
        item.setNavs(Collections.singletonList(
                NavDTO.builder().id("id1").navName("核心系统").navUrl("https://core.bank.com")
                        .navCategory("业务系统").sortOrder(1).status("ACTIVE").build()
        ));
        resp.setGroups(Collections.singletonList(item));
        when(navService.listGrouped(eq("业务系统"), any())).thenReturn(resp);

        mockMvc.perform(get("/api/nav").param("category", "业务系统"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].category").value("业务系统"));
    }

    private NavGroupRespDTO buildGroupResp() {
        NavGroupRespDTO resp = new NavGroupRespDTO();
        NavGroupItem group1 = new NavGroupItem();
        group1.setCategory("业务系统");
        group1.setNavs(Arrays.asList(
                NavDTO.builder().id("id1").navName("核心系统").navUrl("https://core.bank.com")
                        .navIcon("icon-core").navCategory("业务系统").sortOrder(1).status("ACTIVE").build(),
                NavDTO.builder().id("id2").navName("信贷系统").navUrl("https://loan.bank.com")
                        .navIcon("icon-loan").navCategory("业务系统").sortOrder(2).status("ACTIVE").build()
        ));
        NavGroupItem group2 = new NavGroupItem();
        group2.setCategory("常用工具");
        group2.setNavs(Collections.singletonList(
                NavDTO.builder().id("id3").navName("百度").navUrl("https://baidu.com")
                        .navIcon("icon-search").navCategory("常用工具").sortOrder(1).status("ACTIVE").build()
        ));
        resp.setGroups(Arrays.asList(group1, group2));
        return resp;
    }
}
