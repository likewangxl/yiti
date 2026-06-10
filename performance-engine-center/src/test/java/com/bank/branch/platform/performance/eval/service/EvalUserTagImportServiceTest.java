package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    private EvalTag tag(long id, String name) {
        EvalTag t = new EvalTag();
        t.setTagId(id);
        t.setTagName(name);
        t.setStatus(1);
        return t;
    }

    private UserDTO user(String empId) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        return u;
    }

    /** 3 参 helper：empId / role / enabledText（"是"或"否"）。 */
    private EvalUserTagImportRow row(String empId, String role, String enabledText) {
        EvalUserTagImportRow r = new EvalUserTagImportRow();
        r.setEmpId(empId);
        r.setRoleName(role);
        r.setEvalEnabledText(enabledText);
        return r;
    }

    @BeforeEach
    void setUp() {
        service = new EvalUserTagImportService(evalTagMapper, userApi, evalUserTagService);
        lenient().when(evalTagMapper.selectAll(eq(1))).thenReturn(List.of(
                tag(1, "支行行长"), tag(2, "副行长"), tag(3, "客户经理")));
    }

    @Test
    void importRows_allValid_savesEachAndReturnsSuccess() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280"), user("E001")));
        List<EvalUserTagImportRow> rows = List.of(
                row("2280", "支行行长", "是"),
                row("E001", "副行长", "否"));

        EvalUserTagImportResultDTO res = service.importRows(rows);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getImportedCount()).isEqualTo(2);
        assertThat(res.getErrors()).isEmpty();
        verify(evalUserTagService).saveUserRoleWithSetting("2280", 1L, 1);
        verify(evalUserTagService).saveUserRoleWithSetting("E001", 2L, 0);
    }

    @Test
    void importRows_anyError_savesNothing() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> rows = List.of(
                row("2280", "支行行长", "是"),
                row("9999", "支行行长", "是"));

        EvalUserTagImportResultDTO res = service.importRows(rows);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getImportedCount()).isZero();
        assertThat(res.getErrors()).hasSize(1);
        assertThat(res.getErrors().get(0).getRow()).isEqualTo(2);
        assertThat(res.getErrors().get(0).getEmpId()).isEqualTo("9999");
        verify(evalUserTagService, never()).saveUserRoleWithSetting(anyString(), any(), any());
    }

    @Test
    void importRows_empIdNotExist_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("工号不存在");
    }

    @Test
    void importRows_nonNumericEmpId_importsOk() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("E001")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("E001", "支行行长", "是")));
        assertThat(res.isSuccess()).isTrue();
        verify(evalUserTagService).saveUserRoleWithSetting("E001", 1L, 1);
    }

    @Test
    @DisplayName("导入：角色名在扁平池中不存在 → 该行报不存在错误")
    void importRows_roleNotInFlatPool_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "不在池里的角色", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("角色不存在");
    }

    @Test
    @DisplayName("导入：角色填多个 → 该行报只能填一个")
    void importRows_multipleRole_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长,副行长", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("只能");
    }

    @Test
    @DisplayName("导入：角色为空 → 该行报角色不能为空")
    void importRows_roleEmpty_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("角色不能为空");
    }

    @Test
    void importRows_duplicateEmpIdInFile_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(
                row("2280", "支行行长", "是"),
                row("2280", "客户经理", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().stream().anyMatch(e -> e.getMessage().contains("重复"))).isTrue();
    }

    @Test
    void importRows_invalidEnabledText_savesNothing() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> rows = List.of(row("2280", "支行行长", "Y"));
        EvalUserTagImportResultDTO res = service.importRows(rows);
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getImportedCount()).isZero();
        assertThat(res.getErrors()).hasSize(1);
        assertThat(res.getErrors().get(0).getMessage()).contains("是否参与评价");
        verify(evalUserTagService, never()).saveUserRoleWithSetting(any(), any(), any());
    }

    @Test
    void importRows_emptyEnabledText_savesNothing() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> rows = List.of(row("2280", "支行行长", ""));
        EvalUserTagImportResultDTO res = service.importRows(rows);
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("是否参与评价");
    }

    @Test
    void importExcel_parsesXlsxAndImports() throws Exception {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> data = new ArrayList<>();
        EvalUserTagImportRow r = new EvalUserTagImportRow();
        r.setEmpId("2280");
        r.setRoleName("支行行长");
        r.setEvalEnabledText("是");
        data.add(r);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, EvalUserTagImportRow.class).sheet("人员角色").doWrite(data);
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bos.toByteArray());

        EvalUserTagImportResultDTO res = service.importExcel(file);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getImportedCount()).isEqualTo(1);
        verify(evalUserTagService).saveUserRoleWithSetting("2280", 1L, 1);
    }
}
