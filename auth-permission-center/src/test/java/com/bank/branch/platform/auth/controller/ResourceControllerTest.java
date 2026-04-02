package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.ResourceCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO;
import com.bank.branch.platform.auth.api.dto.ResourceUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleResourceBindReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleResourceReplaceReqDTO;
import com.bank.branch.platform.auth.service.ResourceService;
import com.bank.branch.platform.auth.service.RoleResourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ResourceController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ResourceControllerTest {

    @Mock
    private ResourceService resourceService;

    @Mock
    private RoleResourceService roleResourceService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ResourceController(resourceService, roleResourceService)).build();
    }

    @Test
    void getResourceTree_shouldReturn200WithTree() throws Exception {
        // given
        ResourceTreeNodeDTO node = new ResourceTreeNodeDTO();
        node.setResourceId("RES_001");
        node.setMenuName("系统管理");
        when(resourceService.getResourceTree(any(), any())).thenReturn(List.of(node));

        // when & then
        mockMvc.perform(get("/api/admin/resources/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].resourceId").value("RES_001"));
    }

    @Test
    void createResource_shouldReturn200WithCreatedResource() throws Exception {
        // given
        ResourceTreeNodeDTO dto = new ResourceTreeNodeDTO();
        dto.setResourceId("RES_NEW");
        dto.setMenuName("新菜单");
        when(resourceService.createResource(anyString(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(dto);

        ResourceCreateReqDTO req = new ResourceCreateReqDTO();
        req.setResourceUrl("/api/test");
        req.setResourceMethod("GET");
        req.setMenuName("新菜单");
        req.setIsMenu(0);

        // when & then
        mockMvc.perform(post("/api/admin/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.resourceId").value("RES_NEW"));
    }

    @Test
    void updateResource_shouldReturn200() throws Exception {
        // given
        ResourceTreeNodeDTO dto = new ResourceTreeNodeDTO();
        dto.setResourceId("RES_001");
        when(resourceService.updateResource(anyString(), any(ResourceUpdateReqDTO.class))).thenReturn(dto);

        ResourceUpdateReqDTO req = new ResourceUpdateReqDTO();
        req.setMenuName("更新菜单名");

        // when & then
        mockMvc.perform(put("/api/admin/resources/RES_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void deleteResource_shouldReturn200() throws Exception {
        // given
        doNothing().when(resourceService).deleteResource(anyString(), anyString());

        // when & then
        mockMvc.perform(delete("/api/admin/resources/RES_001")
                .param("reason", "废弃接口"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void getResourceIdsByRoleId_shouldReturn200WithIds() throws Exception {
        // given
        when(roleResourceService.getResourceIdsByRoleId(anyString())).thenReturn(Set.of("RES_001", "RES_002"));

        // when & then
        mockMvc.perform(get("/api/admin/roles/R_001/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void bindResources_shouldReturn200() throws Exception {
        // given
        doNothing().when(roleResourceService).bindResources(anyString(), anyList(), anyString());

        RoleResourceBindReqDTO req = new RoleResourceBindReqDTO();
        req.setResourceIds(List.of("RES_001"));
        req.setReason("授权操作");

        // when & then
        mockMvc.perform(post("/api/admin/roles/R_001/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void replaceResources_shouldReturn200() throws Exception {
        // given
        doNothing().when(roleResourceService).replaceResources(anyString(), anyList(), anyString());

        RoleResourceReplaceReqDTO req = new RoleResourceReplaceReqDTO();
        req.setResourceIds(List.of("RES_001", "RES_002"));
        req.setReason("全量替换");

        // when & then
        mockMvc.perform(put("/api/admin/roles/R_001/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}
