package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReOrgDeleteReqDTO;
import com.bank.branch.platform.redengine.api.dto.RePartyOrgReqDTO;
import com.bank.branch.platform.redengine.api.dto.RePartyOrgTreeDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.service.RePartyOrgService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReOrgController 单元测试（standaloneSetup，同构 workflow-center
 * ProcessCommandControllerTest / auth-permission-center OrgControllerTest 既有惯例）。
 * <p>覆盖三处审计/校验缺口修复：</p>
 * <ul>
 *   <li>deleteOrg：DELETE 请求体 {@link ReOrgDeleteReqDTO#reason} 为 {@code @NotBlank}，
 *   配合 {@code @AuditLog(reasonRequired = true)} 强制审计留痕（缺 reason → 400，RE-40002
 *   子节点守卫逻辑不回归）；</li>
 *   <li>addOrg/updateOrg：改接 {@link RePartyOrgReqDTO} + {@code @Valid}，orgName 为
 *   {@code @NotBlank}（对齐 DDL {@code org_name NOT NULL}）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReOrgControllerTest {

    @Mock
    private RePartyOrgService rePartyOrgService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReOrgController(rePartyOrgService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== DELETE /api/re/orgs/{id} ====================

    @Test
    void deleteOrg_withReason_shouldReturn200AndDelegateToService() throws Exception {
        ReOrgDeleteReqDTO req = new ReOrgDeleteReqDTO();
        req.setReason("组织已撤销，清理历史数据");
        doNothing().when(rePartyOrgService).delete(anyLong());

        mockMvc.perform(delete("/api/re/orgs/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(rePartyOrgService).delete(10L);
    }

    @Test
    void deleteOrg_blankReason_shouldReturn400AndNotCallService() throws Exception {
        ReOrgDeleteReqDTO req = new ReOrgDeleteReqDTO();
        req.setReason("");

        mockMvc.perform(delete("/api/re/orgs/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(rePartyOrgService, never()).delete(anyLong());
    }

    @Test
    void deleteOrg_missingBody_shouldReturn400AndNotCallService() throws Exception {
        mockMvc.perform(delete("/api/re/orgs/10"))
                .andExpect(status().isBadRequest());

        verify(rePartyOrgService, never()).delete(anyLong());
    }

    @Test
    void deleteOrg_hasChildren_shouldReturnRe40002BizErrorNotRegress() throws Exception {
        // 回归防呆：RE-40002 存在下级党组织不可删除——BizException 由 GlobalExceptionHandler
        // 转换为 HTTP 200 + 业务错误码（与平台既有约定一致），reason 字段本身不影响该守卫逻辑
        ReOrgDeleteReqDTO req = new ReOrgDeleteReqDTO();
        req.setReason("误删测试");
        doThrow(new BizException("RE-40002", "存在下级党组织不可删除"))
                .when(rePartyOrgService).delete(anyLong());

        mockMvc.perform(delete("/api/re/orgs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RE-40002"));
    }

    // ==================== POST /api/re/orgs ====================

    @Test
    void addOrg_valid_shouldReturn200AndId() throws Exception {
        RePartyOrgReqDTO req = new RePartyOrgReqDTO();
        req.setOrgName("新支部");
        req.setOrgLevel(2);
        req.setParentId(1L);

        when(rePartyOrgService.add(any(RePartyOrg.class))).thenAnswer(inv -> {
            RePartyOrg arg = inv.getArgument(0);
            assertThat(arg.getOrgName()).isEqualTo("新支部");
            assertThat(arg.getParentId()).isEqualTo(1L);
            return 99L;
        });

        mockMvc.perform(post("/api/re/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(99));
    }

    @Test
    void addOrg_blankOrgName_shouldReturn400AndNotCallService() throws Exception {
        RePartyOrgReqDTO req = new RePartyOrgReqDTO();
        req.setOrgName("");

        mockMvc.perform(post("/api/re/orgs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(rePartyOrgService, never()).add(any(RePartyOrg.class));
    }

    // ==================== PUT /api/re/orgs/{id} ====================

    @Test
    void updateOrg_valid_shouldReturn200AndPathIdWinsOverBody() throws Exception {
        RePartyOrgReqDTO req = new RePartyOrgReqDTO();
        req.setOrgName("改名支部");

        doNothing().when(rePartyOrgService).update(any(Long.class), any(RePartyOrg.class));

        mockMvc.perform(put("/api/re/orgs/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        ArgumentCaptor<RePartyOrg> captor = ArgumentCaptor.forClass(RePartyOrg.class);
        verify(rePartyOrgService).update(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        assertThat(captor.getValue().getOrgName()).isEqualTo("改名支部");
    }

    @Test
    void updateOrg_blankOrgName_shouldReturn400AndNotCallService() throws Exception {
        RePartyOrgReqDTO req = new RePartyOrgReqDTO();
        req.setOrgName("");

        mockMvc.perform(put("/api/re/orgs/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(rePartyOrgService, never()).update(any(Long.class), any(RePartyOrg.class));
    }

    // 保留一条无关删除/新增行为的树查询用例，避免 Controller 里未使用到的其它方法在移植时被误删
    @Test
    void getOrgTree_shouldReturn200() throws Exception {
        RePartyOrgTreeDTO node = new RePartyOrgTreeDTO();
        node.setId(1L);
        node.setOrgName("分行党委");
        when(rePartyOrgService.getOrgTree()).thenReturn(java.util.List.of(node));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/re/orgs/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orgName").value("分行党委"));
    }
}
