package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalUserTagImportServiceTest {

    @Mock EvalTagMapper evalTagMapper;
    @Mock UserApi userApi;
    @Mock EvalUserTagService evalUserTagService;
    EvalUserTagImportService service;

    private EvalTag tag(long id, String name, int type) {
        EvalTag t = new EvalTag();
        t.setTagId(id);
        t.setTagName(name);
        t.setTagType(type);
        t.setStatus(1);
        return t;
    }

    private UserDTO user(String empId) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        return u;
    }

    private EvalUserTagImportRow row(String empId, String beEval, String eval) {
        EvalUserTagImportRow r = new EvalUserTagImportRow();
        r.setEmpId(empId);
        r.setBeEvalRoleName(beEval);
        r.setEvalRoleNames(eval);
        return r;
    }

    @BeforeEach
    void setUp() {
        service = new EvalUserTagImportService(evalTagMapper, userApi, evalUserTagService);
        lenient().when(evalTagMapper.selectAll(isNull(), eq(1))).thenReturn(List.of(
                tag(1, "支行行长", 1), tag(2, "副行长", 2), tag(3, "客户经理", 2)));
    }

    @Test
    void importRows_allValid_savesEachAndReturnsSuccess() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280"), user("E001")));
        List<EvalUserTagImportRow> rows = List.of(
                row("2280", "支行行长", "副行长,客户经理"),
                row("E001", "", "副行长"));

        EvalUserTagImportResultDTO res = service.importRows(rows);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getImportedCount()).isEqualTo(2);
        assertThat(res.getErrors()).isEmpty();
        verify(evalUserTagService).saveUserRoles("2280", 1L, List.of(2L, 3L));
        verify(evalUserTagService).saveUserRoles("E001", null, List.of(2L));
    }

    @Test
    void importRows_anyError_savesNothing() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> rows = List.of(
                row("2280", "支行行长", "副行长"),
                row("9999", "支行行长", "副行长"));

        EvalUserTagImportResultDTO res = service.importRows(rows);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getImportedCount()).isZero();
        assertThat(res.getErrors()).hasSize(1);
        assertThat(res.getErrors().get(0).getRow()).isEqualTo(2);
        assertThat(res.getErrors().get(0).getEmpId()).isEqualTo("9999");
        verify(evalUserTagService, never()).saveUserRoles(anyString(), any(), anyList());
    }

    @Test
    void importRows_empIdNotExist_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长", "副行长")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("工号不存在");
    }

    @Test
    void importRows_nonNumericEmpId_importsOk() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("E001")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("E001", "支行行长", "副行长")));
        assertThat(res.isSuccess()).isTrue();
        verify(evalUserTagService).saveUserRoles("E001", 1L, List.of(2L));
    }

    @Test
    void importRows_beEvalRoleNotFound_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "不存在角色", "副行长")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("被评价角色");
    }

    @Test
    void importRows_beEvalRoleWrongType_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "副行长", "客户经理")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("被评价角色");
    }

    @Test
    void importRows_beEvalMultiple_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长,副行长", "客户经理")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("只能");
    }

    @Test
    void importRows_evalRoleWrongType_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长", "支行行长")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("评价角色");
    }

    @Test
    void importRows_bothEmpty_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "", "")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("不能同时为空");
    }

    @Test
    void importRows_duplicateEmpIdInFile_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(
                row("2280", "支行行长", "副行长"),
                row("2280", "支行行长", "客户经理")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().stream().anyMatch(e -> e.getMessage().contains("重复"))).isTrue();
    }

    @Test
    void importRows_evalRoleDedup() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        service.importRows(List.of(row("2280", "", "副行长,副行长")));
        verify(evalUserTagService).saveUserRoles("2280", null, List.of(2L));
    }

    @Test
    void importExcel_parsesXlsxAndImports() throws Exception {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> data = new ArrayList<>();
        data.add(row("2280", "支行行长", "副行长,客户经理"));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, EvalUserTagImportRow.class).sheet("人员角色").doWrite(data);
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bos.toByteArray());

        EvalUserTagImportResultDTO res = service.importExcel(file);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getImportedCount()).isEqualTo(1);
        verify(evalUserTagService).saveUserRoles("2280", 1L, List.of(2L, 3L));
    }
}
