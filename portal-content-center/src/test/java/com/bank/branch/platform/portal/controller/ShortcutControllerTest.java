package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.portal.api.dto.ShortcutDTO;
import com.bank.branch.platform.portal.convert.ShortcutConverter;
import com.bank.branch.platform.portal.entity.PortalShortcut;
import com.bank.branch.platform.portal.service.NavService;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.service.ShortcutService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ShortcutController 集成测试
 *
 * <p>使用 @MockBean 替换 ShortcutService，避免拖入数据库依赖。
 * 验证 GET/PUT /api/portal/shortcuts 接口的请求处理和参数校验。</p>
 */
class ShortcutControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    // ────────── GET /api/portal/shortcuts ──────────

    /**
     * GET /api/portal/shortcuts 无参数时应返回 200 且 data 是数组
     */
    @Test
    @WithMockEmpContext
    void listShortcuts_returns200WithArray() throws Exception {
        when(shortcutService.listShortcuts(eq("E10001"), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/portal/shortcuts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * GET /api/portal/shortcuts 有数据时应返回正确的字段
     */
    @Test
    @WithMockEmpContext
    void listShortcuts_returns200WithShortcutData() throws Exception {
        PortalShortcut entity = new PortalShortcut();
        entity.setId("sys-001");
        entity.setShortcutName("核心系统");
        entity.setShortcutUrl("https://core.bank.com");
        entity.setShortcutIcon("icon-core");
        entity.setShortcutType("SYSTEM");
        entity.setTargetType("INTERNAL");
        entity.setSortOrder(1);

        when(shortcutService.listShortcuts(eq("E10001"), any()))
                .thenReturn(Collections.singletonList(entity));

        mockMvc.perform(get("/api/portal/shortcuts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].shortcutName").value("核心系统"))
                .andExpect(jsonPath("$.data[0].shortcutUrl").value("https://core.bank.com"))
                .andExpect(jsonPath("$.data[0].shortcutType").value("SYSTEM"))
                .andExpect(jsonPath("$.data[0].targetType").value("INTERNAL"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(1));
    }

    /**
     * GET /api/portal/shortcuts?shortcutType=SYSTEM 应传递 SYSTEM 参数给 service
     */
    @Test
    @WithMockEmpContext
    void listShortcuts_passesShortcutTypeToService() throws Exception {
        when(shortcutService.listShortcuts("E10001", "SYSTEM"))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/portal/shortcuts").param("shortcutType", "SYSTEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(shortcutService).listShortcuts("E10001", "SYSTEM");
    }

    // ────────── PUT /api/portal/shortcuts ──────────

    /**
     * PUT /api/portal/shortcuts 合法请求应返回 200
     */
    @Test
    @WithMockEmpContext
    void saveShortcuts_returns200() throws Exception {
        String body = """
                {
                    "shortcuts": [
                        {
                            "shortcutName": "我的常用",
                            "shortcutUrl": "https://my.bank.com",
                            "shortcutIcon": "icon-star",
                            "targetType": "INTERNAL",
                            "sortOrder": 1
                        }
                    ]
                }
                """;

        mockMvc.perform(put("/api/portal/shortcuts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /**
     * PUT /api/portal/shortcuts 空 shortcuts 列表应返回 400
     */
    @Test
    @WithMockEmpContext
    void saveShortcuts_returns400WhenShortcutsEmpty() throws Exception {
        String body = """
                {
                    "shortcuts": []
                }
                """;

        mockMvc.perform(put("/api/portal/shortcuts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /**
     * PUT /api/portal/shortcuts 缺少必填字段 shortcutName 应返回 400
     */
    @Test
    @WithMockEmpContext
    void saveShortcuts_returns400WhenShortcutNameMissing() throws Exception {
        String body = """
                {
                    "shortcuts": [
                        {
                            "shortcutUrl": "https://my.bank.com",
                            "targetType": "INTERNAL",
                            "sortOrder": 1
                        }
                    ]
                }
                """;

        mockMvc.perform(put("/api/portal/shortcuts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
