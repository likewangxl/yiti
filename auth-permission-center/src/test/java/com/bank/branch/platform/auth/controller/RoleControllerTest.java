package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.RoleCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.auth.api.dto.RoleUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.RoleUserRespDTO;
import com.bank.branch.platform.auth.service.RoleService;
import com.bank.branch.platform.auth.service.UserRoleService;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
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
 * RoleController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RoleService roleService;

    @Mock
    private UserRoleService userRoleService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RoleController(roleService, userRoleService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listRoles_shouldReturn200WithPageResult() throws Exception {
        // given
        RoleRespDTO dto = new RoleRespDTO();
        dto.setRoleId("R_001");
        dto.setRoleCode("SYS_ADMIN");
        PageResult<RoleRespDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(roleService.listByPage(any(), any(), anyInt(), anyInt())).thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/roles/")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void createRole_shouldReturn200WithCreatedRole() throws Exception {
        // given
        RoleRespDTO dto = new RoleRespDTO();
        dto.setRoleId("R_NEW");
        dto.setRoleCode("TEST_ROLE");
        when(roleService.createRole(anyString(), anyString(), any())).thenReturn(dto);

        RoleCreateReqDTO req = new RoleCreateReqDTO();
        req.setRoleCode("TEST_ROLE");
        req.setRoleChName("测试角色");

        // when & then
        mockMvc.perform(post("/api/admin/roles/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.roleCode").value("TEST_ROLE"));
    }

    @Test
    void updateRole_shouldReturn200() throws Exception {
        // given
        RoleRespDTO dto = new RoleRespDTO();
        dto.setRoleId("R_001");
        dto.setRoleChName("新名称");
        when(roleService.updateRole(anyString(), anyString(), any())).thenReturn(dto);

        RoleUpdateReqDTO req = new RoleUpdateReqDTO();
        req.setRoleChName("新名称");

        // when & then
        mockMvc.perform(put("/api/admin/roles/R_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void deleteRole_shouldReturn200() throws Exception {
        // given
        doNothing().when(roleService).deleteRole(anyString(), anyString());

        // when & then
        mockMvc.perform(delete("/api/admin/roles/R_001")
                .param("reason", "不再需要"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void listRoleUsers_shouldReturn200WithPageResult() throws Exception {
        // given
        RoleUserRespDTO userDto = new RoleUserRespDTO();
        userDto.setEmpId("emp001");
        PageResult<RoleUserRespDTO> pageResult = PageResult.of(1, 20, 1L, List.of(userDto));
        when(userRoleService.listRoleUsers(anyString(), any(), anyInt(), anyInt())).thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/roles/R_001/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void createRole_duplicateCode_shouldReturnBizError() throws Exception {
        when(roleService.createRole(anyString(), anyString(), any()))
            .thenThrow(new BizException("AUTH-40901", "角色编码已存在"));

        RoleCreateReqDTO req = new RoleCreateReqDTO();
        req.setRoleCode("DUP_ROLE");
        req.setRoleChName("重复角色");

        mockMvc.perform(post("/api/admin/roles/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH-40901"));
    }

    @Test
    void updateRole_notFound_shouldReturnBizError() throws Exception {
        when(roleService.updateRole(anyString(), anyString(), any()))
            .thenThrow(new BizException("AUTH-40401", "角色不存在"));

        RoleUpdateReqDTO req = new RoleUpdateReqDTO();
        req.setRoleChName("新名称");

        mockMvc.perform(put("/api/admin/roles/NONE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH-40401"));
    }
}
