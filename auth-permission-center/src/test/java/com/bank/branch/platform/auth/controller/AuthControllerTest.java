package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.LoginReqDTO;
import com.bank.branch.platform.auth.api.dto.LoginRespDTO;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.exception.AuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void login_shouldReturn200WithToken() throws Exception {
        // given
        LoginRespDTO resp = new LoginRespDTO();
        resp.setEmpId("emp001");
        resp.setToken("session-token-abc");
        when(authService.login(anyString(), anyString(), any(HttpSession.class))).thenReturn(resp);

        LoginReqDTO req = new LoginReqDTO();
        req.setUsername("testuser");
        req.setPassword("password123");

        // when & then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.empId").value("emp001"));
    }

    @Test
    void logout_shouldReturn200() throws Exception {
        // given
        doNothing().when(authService).logout(any(HttpSession.class));

        // when & then
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void getCurrentUser_shouldReturn200WithUserInfo() throws Exception {
        // given
        CurrentUserContext ctx = new CurrentUserContext(
                "emp001", "testUser", "测试用户", "ORG001", "总行", 1,
                Set.of("R_001"), Set.of("SYS_ADMIN"), Set.of("ROLE:SYS_ADMIN"), true);
        when(authService.getCurrentUser(any(HttpSession.class))).thenReturn(ctx);

        // when & then
        mockMvc.perform(get("/api/auth/current-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.empId").value("emp001"))
                .andExpect(jsonPath("$.data.username").value("testUser"))
                .andExpect(jsonPath("$.data.displayName").value("测试用户"))
                .andExpect(jsonPath("$.data.mainOrgCode").value("ORG001"))
                .andExpect(jsonPath("$.data.mainOrgName").value("总行"))
                .andExpect(jsonPath("$.data.orgLevel").value(1))
                .andExpect(jsonPath("$.data.isSystemAdmin").value(true));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void login_serviceThrowsAuthException_shouldReturn401() throws Exception {
        when(authService.login(anyString(), anyString(), any(HttpSession.class)))
            .thenThrow(new AuthException("AUTH-40101", "用户名或密码错误"));

        LoginReqDTO req = new LoginReqDTO();
        req.setUsername("bad");
        req.setPassword("badpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-40101"));
    }

    @Test
    void getCurrentUser_sessionInvalid_shouldReturn401() throws Exception {
        when(authService.getCurrentUser(any(HttpSession.class)))
            .thenThrow(new AuthException("AUTH-40105", "未登录或会话已过期"));

        mockMvc.perform(get("/api/auth/current-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-40105"));
    }
}
