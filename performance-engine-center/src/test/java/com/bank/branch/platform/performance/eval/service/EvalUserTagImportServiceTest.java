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

    /** 4 参 helper：empId / beEval / eval / enabledText（"是"或"否"）。 */
    private EvalUserTagImportRow row(String empId, String beEval, String eval, String enabledText) {
        EvalUserTagImportRow r = new EvalUserTagImportRow();
        r.setEmpId(empId);
        r.setBeEvalRoleName(beEval);
        r.setEvalRoleNames(eval);
        r.setEvalEnabledText(enabledText);
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
                row("2280", "支行行长", "副行长,客户经理", "是"),
                row("E001", "", "副行长", "否"));

        EvalUserTagImportResultDTO res = service.importRows(rows);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getImportedCount()).isEqualTo(2);
        assertThat(res.getErrors()).isEmpty();
        verify(evalUserTagService).saveUserRolesWithSetting("2280", 1L, List.of(2L, 3L), 1);
        verify(evalUserTagService).saveUserRolesWithSetting("E001", null, List.of(2L), 0);
    }

    @Test
    void importRows_anyError_savesNothing() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> rows = List.of(
                row("2280", "支行行长", "副行长", "是"),
                row("9999", "支行行长", "副行长", "是"));

        EvalUserTagImportResultDTO res = service.importRows(rows);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getImportedCount()).isZero();
        assertThat(res.getErrors()).hasSize(1);
        assertThat(res.getErrors().get(0).getRow()).isEqualTo(2);
        assertThat(res.getErrors().get(0).getEmpId()).isEqualTo("9999");
        verify(evalUserTagService, never()).saveUserRolesWithSetting(anyString(), any(), anyList(), any());
    }

    @Test
    void importRows_empIdNotExist_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长", "副行长", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("工号不存在");
    }

    @Test
    void importRows_nonNumericEmpId_importsOk() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("E001")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("E001", "支行行长", "副行长", "是")));
        assertThat(res.isSuccess()).isTrue();
        verify(evalUserTagService).saveUserRolesWithSetting("E001", 1L, List.of(2L), 1);
    }

    @Test
    void importRows_beEvalRoleNotFound_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "不存在角色", "副行长", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("被评价角色");
    }

    @Test
    @DisplayName("导入：被评价角色名在扁平池中不存在 → 该行报不存在错误")
    void importRows_beEvalRoleNotInFlatPool_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        // 扁平池只有支行行长/副行长/客户经理，"不在池里的角色"查不到
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "不在池里的角色", "副行长", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("被评价角色不存在");
    }

    @Test
    void importRows_beEvalMultiple_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长,副行长", "客户经理", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("只能");
    }

    @Test
    @DisplayName("导入：评价角色名在扁平池中不存在 → 该行报不存在错误")
    void importRows_evalRoleNotInFlatPool_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        // 扁平池只有支行行长/副行长/客户经理，"不在池里的评价角色"查不到
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "支行行长", "不在池里的评价角色", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("评价角色不存在");
    }

    @Test
    @DisplayName("导入：评价角色含被评价角色 → 该行报冲突，整体不入库")
    void importRows_roleConflict_failsRow() {
        com.bank.branch.platform.performance.eval.entity.EvalTag t = new com.bank.branch.platform.performance.eval.entity.EvalTag();
        t.setTagId(10L); t.setTagName("店长"); t.setStatus(1);
        when(evalTagMapper.selectAll(null, 1)).thenReturn(java.util.List.of(t));
        com.bank.branch.platform.auth.api.dto.UserDTO u = new com.bank.branch.platform.auth.api.dto.UserDTO();
        u.setEmpId("1001");
        when(userApi.getUserByEmpIds(anyList())).thenReturn(java.util.List.of(u));
        EvalUserTagImportRow row = new EvalUserTagImportRow();
        row.setEmpId("1001"); row.setBeEvalRoleName("店长"); row.setEvalRoleNames("店长"); row.setEvalEnabledText("是");
        EvalUserTagImportResultDTO res = service.importRows(java.util.List.of(row));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors()).anySatisfy(e -> assertThat(e.getMessage()).contains("评价角色不能与被评价角色相同"));
    }

    @Test
    void importRows_bothEmpty_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(row("2280", "", "", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("不能同时为空");
    }

    @Test
    void importRows_duplicateEmpIdInFile_rowError() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        EvalUserTagImportResultDTO res = service.importRows(List.of(
                row("2280", "支行行长", "副行长", "是"),
                row("2280", "支行行长", "客户经理", "是")));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().stream().anyMatch(e -> e.getMessage().contains("重复"))).isTrue();
    }

    @Test
    void importRows_evalRoleDedup() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        service.importRows(List.of(row("2280", "", "副行长,副行长", "是")));
        verify(evalUserTagService).saveUserRolesWithSetting("2280", null, List.of(2L), 1);
    }

    @Test
    void importRows_invalidEnabledText_savesNothing() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> rows = List.of(row("2280", "支行行长", "副行长", "Y"));
        EvalUserTagImportResultDTO res = service.importRows(rows);
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getImportedCount()).isZero();
        assertThat(res.getErrors()).hasSize(1);
        assertThat(res.getErrors().get(0).getMessage()).contains("是否参与评价");
        verify(evalUserTagService, never()).saveUserRolesWithSetting(any(), any(), anyList(), any());
    }

    @Test
    void importRows_emptyEnabledText_savesNothing() {
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(user("2280")));
        List<EvalUserTagImportRow> rows = List.of(row("2280", "支行行长", "副行长", ""));
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
        r.setBeEvalRoleName("支行行长");
        r.setEvalRoleNames("副行长,客户经理");
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
        verify(evalUserTagService).saveUserRolesWithSetting("2280", 1L, List.of(2L, 3L), 1);
    }
}
