package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserRoleBindReqDTO;
import com.bank.branch.platform.auth.service.UserRoleService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserRoleController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class UserRoleControllerTest {

    @Mock
    private UserRoleService userRoleService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserRoleController(userRoleService)).build();
    }

    @Test
    void getUserRoles_shouldReturn200WithRoleList() throws Exception {
        // given
        RoleSimpleDTO roleDto = new RoleSimpleDTO();
        roleDto.setRoleId("R_001");
        roleDto.setRoleCode("SYS_ADMIN");
        when(userRoleService.getRolesByUserId(anyString())).thenReturn(List.of(roleDto));

        // when & then
        mockMvc.perform(get("/api/admin/users/emp001/roles/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].roleCode").value("SYS_ADMIN"));
    }

    @Test
    void bindRoles_shouldReturn200() throws Exception {
        // given
        // bindRoles 现为 4 参（含 primaryRoleId，可能为 null → 用 any()）
        doNothing().when(userRoleService).bindRoles(anyString(), anyList(), any(), anyString());

        UserRoleBindReqDTO req = new UserRoleBindReqDTO();
        req.setRoleIds(List.of("R_001", "R_002"));
        req.setReason("业务需要");

        // when & then
        mockMvc.perform(post("/api/admin/users/emp001/roles/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void unbindRole_shouldReturn200() throws Exception {
        // given
        doNothing().when(userRoleService).unbindRole(anyString(), anyString(), anyString());

        // when & then
        mockMvc.perform(delete("/api/admin/users/emp001/roles/R_001")
                .param("reason", "权限调整"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}
