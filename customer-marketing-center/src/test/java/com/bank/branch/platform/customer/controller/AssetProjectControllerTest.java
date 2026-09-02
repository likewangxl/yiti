package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.customer.dto.asset.AssetProjectSaveRequest;
import com.bank.branch.platform.customer.service.AssetProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssetProjectControllerTest {
    @Mock private AssetProjectService service;
    @Mock private CurrentUserApi currentUserApi;
    @InjectMocks private AssetProjectController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void create_missingCustomer_shouldBeRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/marketing/asset-projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"产业园一期\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void update_missingLockVersion_shouldBeRejectedBeforeService() throws Exception {
        mockMvc.perform(put("/api/marketing/asset-projects/9001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custId\":101,\"projectName\":\"产业园一期\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).update(any(), any(), any(), any(), eq(false));
    }

    @Test
    void submit_shouldUseAuthenticatedEmployeeAndOrganization() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);

        mockMvc.perform(post("/api/marketing/asset-projects/9001/submit"))
                .andExpect(status().isOk());

        verify(service).submit(9001L, "E001", "ORG001", false);
    }

    @Test
    void everyEndpoint_shouldDeclareAssetProjectAuthorization() throws Exception {
        Map<String, BizAction> expected = Map.of(
                "page", BizAction.LIST,
                "detail", BizAction.READ,
                "create", BizAction.WRITE,
                "update", BizAction.WRITE,
                "submit", BizAction.WRITE,
                "delete", BizAction.WRITE,
                "cancel", BizAction.WRITE,
                "urgentContext", BizAction.READ,
                "requestUrgent", BizAction.WRITE,
                "urgentApplies", BizAction.READ);

        for (Method method : AssetProjectController.class.getDeclaredMethods()) {
            BizAction action = expected.get(method.getName());
            if (action == null) continue;
            BizAuth auth = method.getAnnotation(BizAuth.class);
            assertThat(auth).as(method.getName()).isNotNull();
            assertThat(auth.bizType()).as(method.getName()).isEqualTo(BizType.ASSET_PROJECT);
            assertThat(auth.action()).as(method.getName()).isEqualTo(action);
        }
        assertThat(expected).hasSize(10);
    }
}
