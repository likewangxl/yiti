package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.OrgTreeNodeDTO;
import com.bank.branch.platform.auth.api.dto.OrgUserDTO;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.service.OrgService;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
                "emp001", "emp001", "测试用户", "ORG001", "总行", 1,
                Set.of(), Set.of(), Set.of(), false);
        when(currentUserProvider.get()).thenReturn(ctx);

        // 构造树形结构：ORG001 为根节点，ORG002 为子节点
        OrgTreeNodeDTO childDto = new OrgTreeNodeDTO();
        childDto.setOrgCode("ORG002");
        childDto.setOrgName("分行");
        childDto.setOrgLevel(2);
        childDto.setParentOrgCode("ORG001");

        OrgTreeNodeDTO rootDto = new OrgTreeNodeDTO();
        rootDto.setOrgCode("ORG001");
        rootDto.setOrgName("总行");
        rootDto.setOrgLevel(1);
        rootDto.setChildren(List.of(childDto));

        when(orgService.getOrgSubtreeAsTree(anyString())).thenReturn(List.of(rootDto));

        // when & then
        mockMvc.perform(get("/api/orgs/subtree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].orgCode").value("ORG001"))
                .andExpect(jsonPath("$.data[0].children[0].orgCode").value("ORG002"));
    }

    @Test
    void getOrgUsers_shouldReturn200WithUsersAndRoles() throws Exception {
        // given
        RoleSimpleDTO role = new RoleSimpleDTO();
        role.setRoleId("R_001");
        role.setRoleCode("CUST_MGR");
        role.setRoleChName("客户经理");

        OrgUserDTO user = new OrgUserDTO();
        user.setEmpId("emp001");
        user.setUsername("testuser");
        user.setDisplayName("测试用户");
        user.setEmail("test@example.com");
        user.setRoles(List.of(role));

        PageResult<OrgUserDTO> page = PageResult.of(1, 20, 1, List.of(user));
        when(orgService.getOrgUsers(eq("ORG001"), any(), any(), any(), eq(1), eq(20))).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/orgs/ORG001/users")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.records[0].empId").value("emp001"))
                .andExpect(jsonPath("$.page.records[0].displayName").value("测试用户"))
                .andExpect(jsonPath("$.page.records[0].email").value("test@example.com"))
                .andExpect(jsonPath("$.page.records[0].roles[0].roleCode").value("CUST_MGR"));
    }
}
