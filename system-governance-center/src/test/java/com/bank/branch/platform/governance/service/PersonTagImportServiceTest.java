package com.bank.branch.platform.governance.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.PersonTagImportResultDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagOrgImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagOrgMemberImportRow;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.bank.branch.platform.governance.mapper.PersonTagMapper;
import com.bank.branch.platform.governance.mapper.PersonTagRelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonTagImportService 单元测试：用 EasyExcel 现场生成内存 xlsx，走真实解析链路（含 EMP/ORG 两维度）。
 */
@ExtendWith(MockitoExtension.class)
class PersonTagImportServiceTest {

    @Mock
    private PersonTagMapper tagMapper;
    @Mock
    private PersonTagRelMapper relMapper;
    @Mock
    private PersonTagService personTagService;
    @Mock
    private UserApi userApi;
    @Mock
    private OrgApi orgApi;

    private PersonTagImportService service;

    @BeforeEach
    void setUp() {
        service = new PersonTagImportService(tagMapper, relMapper, personTagService, userApi, orgApi);
    }

    /** 生成员工全局导入 xlsx（标签名称/工号 两列）。 */
    private static MockMultipartFile globalXlsx(List<PersonTagImportRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, PersonTagImportRow.class).sheet("s").doWrite(rows);
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    /** 生成机构全局导入 xlsx（标签名称/机构号 两列）。 */
    private static MockMultipartFile orgGlobalXlsx(List<PersonTagOrgImportRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, PersonTagOrgImportRow.class).sheet("s").doWrite(rows);
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    /** 生成员工成员导入 xlsx（工号 一列）。 */
    private static MockMultipartFile memberXlsx(List<PersonTagMemberImportRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, PersonTagMemberImportRow.class).sheet("s").doWrite(rows);
        return new MockMultipartFile("file", "members.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    /** 生成机构成员导入 xlsx（机构号 一列）。 */
    private static MockMultipartFile orgMemberXlsx(List<PersonTagOrgMemberImportRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, PersonTagOrgMemberImportRow.class).sheet("s").doWrite(rows);
        return new MockMultipartFile("file", "members.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private static PersonTagImportRow row(String tagName, String username) {
        PersonTagImportRow r = new PersonTagImportRow();
        r.setTagName(tagName);
        r.setUsername(username);
        return r;
    }

    private static PersonTagOrgImportRow orgRow(String tagName, String deptNo) {
        PersonTagOrgImportRow r = new PersonTagOrgImportRow();
        r.setTagName(tagName);
        r.setOrgDeptNo(deptNo);
        return r;
    }

    private static PersonTagMemberImportRow memberRow(String username) {
        PersonTagMemberImportRow r = new PersonTagMemberImportRow();
        r.setUsername(username);
        return r;
    }

    private static PersonTagOrgMemberImportRow orgMemberRow(String deptNo) {
        PersonTagOrgMemberImportRow r = new PersonTagOrgMemberImportRow();
        r.setOrgDeptNo(deptNo);
        return r;
    }

    private static PersonTag tag(Long id, String name) {
        PersonTag t = new PersonTag();
        t.setTagId(id);
        t.setTagName(name);
        return t;
    }

    private static OrgDTO org(String deptNo) {
        OrgDTO o = new OrgDTO();
        o.setDeptNo(deptNo);
        return o;
    }

    // ===== 全局导入（员工维度） =====

    @Test
    void importGlobal_emp_shouldCreateMissingTagAndInsertRels() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001", "100002"));
        when(tagMapper.selectByTagNames(anyList())).thenReturn(List.of(tag(1L, "骨干")));
        doAnswer(inv -> {
            PersonTag t = inv.getArgument(0);
            t.setTagId(2L);
            return 1;
        }).when(tagMapper).insert(any(PersonTag.class));
        when(relMapper.selectByTagIds(anyList())).thenReturn(List.of());

        PersonTagImportResultDTO result = service.importGlobal(
                globalXlsx(List.of(row("骨干", "100001"), row("新星", "100002"))), "EMP", "OP1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getCreatedTagCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        verify(relMapper).insertBatch(anyList());
    }

    @Test
    void importGlobal_emp_existingRel_shouldSkipNotDuplicate() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));
        when(tagMapper.selectByTagNames(anyList())).thenReturn(List.of(tag(1L, "骨干")));
        PersonTagRel existing = new PersonTagRel();
        existing.setTagId(1L);
        existing.setDimType(PersonTagRel.DIM_EMP);
        existing.setUsername("100001");
        when(relMapper.selectByTagIds(anyList())).thenReturn(List.of(existing));

        PersonTagImportResultDTO result = service.importGlobal(
                globalXlsx(List.of(row("骨干", "100001"))), "EMP", "OP1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void importGlobal_emp_invalidUsername_shouldFailAtomicallyWithRowError() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        PersonTagImportResultDTO result = service.importGlobal(
                globalXlsx(List.of(row("骨干", "100001"), row("骨干", "BAD"))), "EMP", "OP1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(2);
        assertThat(result.getErrors().get(0).getMessage()).contains("不存在");
        verify(tagMapper, never()).insert(any(PersonTag.class));
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void importGlobal_emp_blankAndDuplicateRows_shouldReportPerRowErrors() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        PersonTagImportResultDTO result = service.importGlobal(globalXlsx(List.of(
                row("", "100001"),          // 标签为空
                row("骨干", ""),             // 工号为空
                row("骨干", "100001"),
                row("骨干", "100001"))), "EMP", "OP1"); // 文件内重复

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(3);
    }

    @Test
    void importGlobal_emptyFile_shouldThrow42206() {
        MockMultipartFile empty = new MockMultipartFile("file", "e.xlsx", "application/octet-stream",
                new byte[0]);

        assertThatThrownBy(() -> service.importGlobal(empty, "EMP", "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("为空");
    }

    // ===== 全局导入（机构维度） =====

    @Test
    void importGlobal_org_shouldValidateDeptNoAndInsertOrgRels() {
        when(orgApi.getOrgsByDeptNos(anyList())).thenReturn(List.of(org("0101"), org("0102")));
        when(tagMapper.selectByTagNames(anyList())).thenReturn(List.of(tag(1L, "重点机构")));
        when(relMapper.selectByTagIds(anyList())).thenReturn(List.of());

        PersonTagImportResultDTO result = service.importGlobal(
                orgGlobalXlsx(List.of(orgRow("重点机构", "0101"), orgRow("重点机构", "0102"))), "ORG", "OP1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        verify(userApi, never()).filterExistingUsernames(anyList());
        verify(relMapper).insertBatch(anyList());
    }

    @Test
    void importGlobal_org_invalidDeptNo_shouldFailWithRowError() {
        when(orgApi.getOrgsByDeptNos(anyList())).thenReturn(List.of(org("0101")));

        PersonTagImportResultDTO result = service.importGlobal(
                orgGlobalXlsx(List.of(orgRow("重点机构", "0101"), orgRow("重点机构", "BADORG"))), "ORG", "OP1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(2);
        verify(relMapper, never()).insertBatch(anyList());
    }

    // ===== 成员导入（员工维度，按维度全量覆盖） =====

    @Test
    void importMembers_emp_shouldOverwriteEmpDimOnly() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001", "100002"));
        when(relMapper.deleteByTagIdAndDim(1L, "EMP")).thenReturn(3);

        PersonTagImportResultDTO result = service.importMembers(1L,
                memberXlsx(List.of(memberRow("100001"), memberRow("100002"))), "EMP", "OP1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        verify(personTagService).requireTag(1L);
        verify(relMapper).deleteByTagIdAndDim(1L, "EMP");
        verify(relMapper, never()).deleteByTagId(anyLong());
        verify(relMapper).insertBatch(anyList());
    }

    @Test
    void importMembers_org_shouldOverwriteOrgDimOnly() {
        when(orgApi.getOrgsByDeptNos(anyList())).thenReturn(List.of(org("0101"), org("0102")));
        when(relMapper.deleteByTagIdAndDim(1L, "ORG")).thenReturn(1);

        PersonTagImportResultDTO result = service.importMembers(1L,
                orgMemberXlsx(List.of(orgMemberRow("0101"), orgMemberRow("0102"))), "ORG", "OP1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        verify(relMapper).deleteByTagIdAndDim(1L, "ORG");
        verify(relMapper).insertBatch(anyList());
    }

    @Test
    void importMembers_anyRowError_shouldNotTouchExistingData() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        PersonTagImportResultDTO result = service.importMembers(1L,
                memberXlsx(List.of(memberRow("100001"), memberRow("BAD"))), "EMP", "OP1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        verify(relMapper, never()).deleteByTagIdAndDim(anyLong(), anyString());
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void importMembers_duplicateUsernameInFile_shouldReportRowError() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        PersonTagImportResultDTO result = service.importMembers(1L,
                memberXlsx(List.of(memberRow("100001"), memberRow("100001"))), "EMP", "OP1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("重复");
    }
}
