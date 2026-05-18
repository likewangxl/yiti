package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.api.dto.UserListItemRespDTO;
import com.bank.branch.platform.auth.service.UserService;
import com.bank.branch.platform.common.web.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock UserService userService;
    @Mock CurrentUserApi currentUserApi;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, currentUserApi)).build();
    }

    @Test
    void list_shouldReturnPagedUsers() throws Exception {
        UserListItemRespDTO it = new UserListItemRespDTO();
        it.setUserId("E001"); it.setUsername("alice");
        PageResult<UserListItemRespDTO> page = PageResult.of(1, 20, 1L, List.of(it));
        when(userService.pageUsers(any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/users").param("username", "ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value("E001"));
    }

    @Test
    void exists_shouldReturnTrue() throws Exception {
        when(userService.existsByUsername("alice")).thenReturn(true);
        mockMvc.perform(get("/api/admin/users/alice/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void getById_shouldReturnUserDetail() throws Exception {
        UserDetailRespDTO dto = new UserDetailRespDTO();
        dto.setUserId("E001"); dto.setUsername("alice");
        when(userService.getById("E001")).thenReturn(dto);
        mockMvc.perform(get("/api/admin/users/E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("E001"));
    }

    @Test
    void create_shouldReturnSuccess() throws Exception {
        com.bank.branch.platform.auth.api.dto.UserCreateReqDTO req =
                new com.bank.branch.platform.auth.api.dto.UserCreateReqDTO();
        req.setUserId("E001"); req.setUsername("alice"); req.setUserchnname("张三");
        req.setInitialPassword("Init@123");
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        org.mockito.Mockito.doNothing().when(userService).create(any(), anyString());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/users")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void update_shouldReturnSuccess() throws Exception {
        com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req =
                new com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO();
        req.setUsername("alice2");
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        org.mockito.Mockito.doNothing().when(userService).update(anyString(), any(), anyString());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_shouldReturnAffectedCount() throws Exception {
        when(userService.deleteByIds(java.util.List.of("E001","E002"))).thenReturn(2);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/users/E001,E002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void resetPassword_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.resetPassword(java.util.List.of("E001","E002"), "OPERATOR1")).thenReturn(2);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001,E002/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void changeMyPassword_shouldReturnSuccess() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req =
                new com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO();
        req.setOldPassword("oldPass1"); req.setNewPassword("newPass2");
        org.mockito.Mockito.doNothing().when(userService).changeMyPassword(eq("E001"), any());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/me/password")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void active_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.batchActivate(java.util.List.of("E001"), "OPERATOR1")).thenReturn(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001/active"))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void inactive_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.batchInactivate(java.util.List.of("E001"), "OPERATOR1")).thenReturn(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001/inactive"))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void lock_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.batchLock(java.util.List.of("E001"), "OPERATOR1")).thenReturn(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001/lock"))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void unlock_shouldReturnAffected() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR1");
        when(userService.batchUnlock(java.util.List.of("E001"), "OPERATOR1")).thenReturn(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/users/E001/unlock"))
                .andExpect(jsonPath("$.data").value(1));
    }
}
