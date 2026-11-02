package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.governance.api.dto.*;
import com.bank.branch.platform.governance.service.LoginScreenRuleService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LoginScreenRuleControllerTest {
    @Mock LoginScreenRuleService service;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    @BeforeEach void setUp() { mvc = MockMvcBuilders.standaloneSetup(new LoginScreenRuleController(service)).setControllerAdvice(new GlobalExceptionHandler()).build(); }
    @Test void getContract() throws Exception {
        var d = new LoginScreenRuleDTO(); d.setRoleId("R1"); d.setTarget("PERSONAL"); d.setPriority(10); d.setVersion("v");
        when(service.getForRole("R1")).thenReturn(d);
        mvc.perform(get("/api/admin/sys/login-screens/R1")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.target").value("PERSONAL"));
    }
    @Test void putContractAndValidation() throws Exception {
        var d = new LoginScreenRuleDTO(); d.setRoleId("R1"); d.setTarget("BRANCH"); d.setPriority(1); d.setVersion("v2");
        when(service.save(eq("R1"), any())).thenReturn(d);
        mvc.perform(put("/api/admin/sys/login-screens/R1").contentType(MediaType.APPLICATION_JSON).content("{\"target\":\"BRANCH\",\"priority\":1,\"expectedVersion\":\"\",\"reason\":\"test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("v2"));
        mvc.perform(put("/api/admin/sys/login-screens/R1").contentType(MediaType.APPLICATION_JSON).content("{\"target\":\"BRANCH\",\"priority\":1}"))
                .andExpect(status().isBadRequest());
    }
    @Test void currentContract() throws Exception {
        var d = new LoginScreenRuleDTO(); d.setRoleId("R1"); d.setTarget("RETAIL"); d.setPriority(2);
        when(service.current()).thenReturn(new LoginScreenRulesRespDTO(List.of(d)));
        mvc.perform(get("/api/sys/login-screens")).andExpect(status().isOk()).andExpect(jsonPath("$.data.rules[0].roleId").value("R1"));
    }
}
