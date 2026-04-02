package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.OrgTreeNodeDTO;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.service.OrgService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrgController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class OrgControllerTest {

    @Mock
    private OrgService orgService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrgController(orgService, currentUserProvider)).build();
    }

    @Test
    void getOrgTree_shouldReturn200WithTree() throws Exception {
        // given
        OrgTreeNodeDTO node = new OrgTreeNodeDTO();
        node.setOrgCode("ORG001");
        node.setOrgName("总行");
        when(orgService.getOrgTree()).thenReturn(List.of(node));

        // when & then
        mockMvc.perform(get("/api/orgs/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].orgCode").value("ORG001"));
    }

    @Test
    void getOrgSubtree_shouldReturn200WithSubtreeNodes() throws Exception {
        // given
        CurrentUserContext ctx = new CurrentUserContext(
                "emp001", "ORG001", Set.of(), Set.of(), Set.of(), false);
        when(currentUserProvider.get()).thenReturn(ctx);

        OrgDTO orgDto = new OrgDTO();
        orgDto.setOrgCode("ORG001");
        orgDto.setOrgName("总行");
        OrgDTO childDto = new OrgDTO();
        childDto.setOrgCode("ORG002");
        childDto.setOrgName("分行");
        when(orgService.getOrgSubtree(anyString())).thenReturn(List.of(orgDto, childDto));

        // when & then
        mockMvc.perform(get("/api/orgs/subtree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].orgCode").value("ORG001"))
                .andExpect(jsonPath("$.data[1].orgCode").value("ORG002"));
    }
}
