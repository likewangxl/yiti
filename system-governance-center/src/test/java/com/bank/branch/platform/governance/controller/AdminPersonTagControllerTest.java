package com.bank.branch.platform.governance.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.PersonTagCreateReqDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagImportResultDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberAddReqDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberRespDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagRespDTO;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.bank.branch.platform.governance.service.PersonTagImportService;
import com.bank.branch.platform.governance.service.PersonTagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminPersonTagController 单元测试（standalone MockMvc + Mock Service）。
 */
@ExtendWith(MockitoExtension.class)
class AdminPersonTagControllerTest {

    @Mock
    private PersonTagService personTagService;
    @Mock
    private PersonTagImportService personTagImportService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminPersonTagController(personTagService, personTagImportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // 模拟当前登录用户
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("OP1");
        DataScopeContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void list_shouldReturnPage() throws Exception {
        PersonTagRespDTO row = new PersonTagRespDTO();
        row.setTagId(1L);
        row.setTagName("骨干");
        row.setMemberCount(2L);
        when(personTagService.pageTags(eq("骨"), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 20, 1, List.of(row)));

        mockMvc.perform(get("/api/admin/sys/person-tags").param("keyword", "骨"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].tagName").value("骨干"));
    }

    @Test
    void create_shouldPassOperatorFromContext() throws Exception {
        PersonTag tag = new PersonTag();
        tag.setTagId(1L);
        tag.setTagName("骨干");
        when(personTagService.createTag(eq("骨干"), any(), eq("OP1"))).thenReturn(tag);

        PersonTagCreateReqDTO req = new PersonTagCreateReqDTO();
        req.setTagName("骨干");

        mockMvc.perform(post("/api/admin/sys/person-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tagId").value(1));
    }

    @Test
    void create_blankName_shouldFailValidation() throws Exception {
        PersonTagCreateReqDTO req = new PersonTagCreateReqDTO();
        req.setTagName("  ");

        mockMvc.perform(post("/api/admin/sys/person-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("0")));
    }

    @Test
    void delete_shouldCascadeViaService() throws Exception {
        mockMvc.perform(delete("/api/admin/sys/person-tags/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(personTagService).deleteTag(1L);
    }

    @Test
    void delete_notFound_shouldReturnBizErrorCode() throws Exception {
        doThrow(new BizException("GOV-40008", "业务标签不存在"))
                .when(personTagService).deleteTag(99L);

        mockMvc.perform(delete("/api/admin/sys/person-tags/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40008"));
    }

    @Test
    void members_empDim_default_shouldReturnUsername() throws Exception {
        PersonTagMemberRespDTO m = new PersonTagMemberRespDTO();
        m.setId(11L);
        m.setDimType(PersonTagRel.DIM_EMP);
        m.setUsername("100001");
        when(personTagService.pageMembers(eq(1L), eq("EMP"), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 20, 1, List.of(m)));

        mockMvc.perform(get("/api/admin/sys/person-tags/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].dimType").value("EMP"))
                .andExpect(jsonPath("$.data.records[0].username").value("100001"));
    }

    @Test
    void members_orgDim_shouldReturnDeptNoAndOrgName() throws Exception {
        PersonTagMemberRespDTO m = new PersonTagMemberRespDTO();
        m.setId(21L);
        m.setDimType(PersonTagRel.DIM_ORG);
        m.setOrgDeptNo("0101");
        m.setOrgName("城东支行");
        when(personTagService.pageMembers(eq(1L), eq("ORG"), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 20, 1, List.of(m)));

        mockMvc.perform(get("/api/admin/sys/person-tags/1/members").param("dim", "ORG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].dimType").value("ORG"))
                .andExpect(jsonPath("$.data.records[0].orgDeptNo").value("0101"))
                .andExpect(jsonPath("$.data.records[0].orgName").value("城东支行"));
    }

    @Test
    void addMembers_bothDims_shouldReturnAddedCount() throws Exception {
        when(personTagService.addMembers(eq(1L), anyList(), anyList(), eq("OP1"))).thenReturn(3);

        PersonTagMemberAddReqDTO req = new PersonTagMemberAddReqDTO();
        req.setUsernames(List.of("100001", "100002"));
        req.setOrgDeptNos(List.of("0101"));

        mockMvc.perform(post("/api/admin/sys/person-tags/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void updateMember_empRow_shouldDelegateWithNullDeptNo() throws Exception {
        mockMvc.perform(put("/api/admin/sys/person-tags/1/members/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"100002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(personTagService).updateMember(1L, 11L, "100002", null, "OP1");
    }

    @Test
    void updateMember_orgRow_shouldDelegateWithDeptNo() throws Exception {
        mockMvc.perform(put("/api/admin/sys/person-tags/1/members/21")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgDeptNo\":\"0102\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(personTagService).updateMember(eq(1L), eq(21L), isNull(), eq("0102"), eq("OP1"));
    }

    @Test
    void importGlobal_empDim_default_shouldReturnResult() throws Exception {
        PersonTagImportResultDTO result = new PersonTagImportResultDTO();
        result.setSuccess(true);
        result.setImportedCount(3);
        result.setCreatedTagCount(1);
        when(personTagImportService.importGlobal(any(), eq("EMP"), eq("OP1"))).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

        mockMvc.perform(multipart("/api/admin/sys/person-tags/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.importedCount").value(3));
    }

    @Test
    void importMembers_orgDim_shouldTargetClickedTagAndDim() throws Exception {
        PersonTagImportResultDTO result = new PersonTagImportResultDTO();
        result.setSuccess(true);
        result.setImportedCount(2);
        when(personTagImportService.importMembers(eq(1L), any(), eq("ORG"), eq("OP1"))).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "members.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

        mockMvc.perform(multipart("/api/admin/sys/person-tags/1/import").file(file).param("dim", "ORG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(2));

        verify(personTagImportService).importMembers(eq(1L), any(), eq("ORG"), eq("OP1"));
    }

    @Test
    void importTemplate_bothDims_shouldStreamXlsx() throws Exception {
        mockMvc.perform(get("/api/admin/sys/person-tags/import-template")).andExpect(status().isOk());
        MvcResult result = mockMvc.perform(
                        get("/api/admin/sys/person-tags/import-template").param("dim", "ORG"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<Integer, String>> rows = readXlsxRows(result);
        assertThat(rows.get(0))
                .containsEntry(0, "标签名称")
                .containsEntry(1, "机构名称");
        assertThat(rows.get(1)).containsEntry(1, "城东支行");
    }

    @Test
    void memberImportTemplate_bothDims_shouldStreamXlsx() throws Exception {
        mockMvc.perform(get("/api/admin/sys/person-tags/member-import-template")).andExpect(status().isOk());
        MvcResult result = mockMvc.perform(
                        get("/api/admin/sys/person-tags/member-import-template").param("dim", "ORG"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<Integer, String>> rows = readXlsxRows(result);
        assertThat(rows.get(0)).containsEntry(0, "机构名称");
        assertThat(rows.get(1)).containsEntry(0, "城东支行");
    }

    /** 读取模板全部行（含第 0 行表头），用于断言实际下载内容。 */
    private static List<Map<Integer, String>> readXlsxRows(MvcResult result) {
        return EasyExcel.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))
                .headRowNumber(0)
                .sheet()
                .doReadSync();
    }
}
