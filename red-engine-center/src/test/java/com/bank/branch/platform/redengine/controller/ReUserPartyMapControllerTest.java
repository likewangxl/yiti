package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReUserPartyMapDTO;
import com.bank.branch.platform.redengine.service.ReUserPartyMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReUserPartyMapControllerTest {

    @Mock
    private ReUserPartyMapService reUserPartyMapService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReUserPartyMapController(reUserPartyMapService)).build();
    }

    @Test
    void list_shouldAcceptAllFiltersAndReturnCommonPageResult() throws Exception {
        ReUserPartyMapDTO row = new ReUserPartyMapDTO();
        row.setId(1L);
        row.setUserId("PT_USER_ID_1001");
        row.setUsername("EMP001");
        row.setDisplayName("张三");
        row.setPartyOrgId(3L);
        row.setPartyRole("REPORTER");
        when(reUserPartyMapService.page(2, 20, "EMP", "张", 3L, "REPORTER"))
                .thenReturn(PageResult.of(2, 20, 21L, List.of(row)));

        mockMvc.perform(get("/api/re/user-party-maps")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("username", "EMP")
                        .param("displayName", "张")
                        .param("partyOrgId", "3")
                        .param("partyRole", "REPORTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.records[0].username").value("EMP001"))
                .andExpect(jsonPath("$.data.records[0].displayName").value("张三"));

        verify(reUserPartyMapService).page(2, 20, "EMP", "张", 3L, "REPORTER");
    }
}
