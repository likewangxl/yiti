package com.bank.branch.platform.governance.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.PersonTagImportResultDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberImportRow;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonTagImportService 单元测试：用 EasyExcel 现场生成内存 xlsx，走真实解析链路。
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

    private PersonTagImportService service;

    @BeforeEach
    void setUp() {
        service = new PersonTagImportService(tagMapper, relMapper, personTagService, userApi);
    }

    /** 生成全局导入 xlsx（标签名称/工号/姓名 三列）。 */
    private static MockMultipartFile globalXlsx(List<PersonTagImportRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, PersonTagImportRow.class).sheet("s").doWrite(rows);
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    /** 生成成员导入 xlsx（工号/姓名 两列）。 */
    private static MockMultipartFile memberXlsx(List<PersonTagMemberImportRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, PersonTagMemberImportRow.class).sheet("s").doWrite(rows);
        return new MockMultipartFile("file", "members.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private static PersonTagImportRow row(String tagName, String username, String displayName) {
        PersonTagImportRow r = new PersonTagImportRow();
        r.setTagName(tagName);
        r.setUsername(username);
        r.setDisplayName(displayName);
        return r;
    }

    private static PersonTagMemberImportRow memberRow(String username, String displayName) {
        PersonTagMemberImportRow r = new PersonTagMemberImportRow();
        r.setUsername(username);
        r.setDisplayName(displayName);
        return r;
    }

    private static PersonTag tag(Long id, String name) {
        PersonTag t = new PersonTag();
        t.setTagId(id);
        t.setTagName(name);
        return t;
    }

    // ===== 全局导入 =====

    @Test
    void importGlobal_shouldCreateMissingTagAndInsertRels() {
        // 已有标签"骨干"，"新星"需自动新建；两工号均有效
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001", "100002"));
        when(tagMapper.selectByTagNames(anyList())).thenReturn(List.of(tag(1L, "骨干")));
        doAnswer(inv -> {
            PersonTag t = inv.getArgument(0);
            t.setTagId(2L);
            return 1;
        }).when(tagMapper).insert(any(PersonTag.class));
        when(relMapper.selectByTagIds(anyList())).thenReturn(List.of());

        PersonTagImportResultDTO result = service.importGlobal(
                globalXlsx(List.of(row("骨干", "100001", "张三"), row("新星", "100002", "李四"))), "OP1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getCreatedTagCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        verify(relMapper).insertBatch(anyList());
    }

    @Test
    void importGlobal_existingRel_shouldSkipNotDuplicate() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));
        when(tagMapper.selectByTagNames(anyList())).thenReturn(List.of(tag(1L, "骨干")));
        PersonTagRel existing = new PersonTagRel();
        existing.setTagId(1L);
        existing.setUsername("100001");
        when(relMapper.selectByTagIds(anyList())).thenReturn(List.of(existing));

        PersonTagImportResultDTO result = service.importGlobal(
                globalXlsx(List.of(row("骨干", "100001", null))), "OP1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void importGlobal_invalidUsername_shouldFailAtomicallyWithRowError() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        PersonTagImportResultDTO result = service.importGlobal(
                globalXlsx(List.of(row("骨干", "100001", null), row("骨干", "BAD", null))), "OP1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(2);
        assertThat(result.getErrors().get(0).getMessage()).contains("不存在");
        // 原子性：一条都不写
        verify(tagMapper, never()).insert(any(PersonTag.class));
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void importGlobal_blankAndDuplicateRows_shouldReportPerRowErrors() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        PersonTagImportResultDTO result = service.importGlobal(globalXlsx(List.of(
                row("", "100001", null),          // 标签为空
                row("骨干", "", null),             // 工号为空
                row("骨干", "100001", null),
                row("骨干", "100001", null))), "OP1"); // 文件内重复

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(3);
    }

    @Test
    void importGlobal_emptyFile_shouldThrow42206() {
        MockMultipartFile empty = new MockMultipartFile("file", "e.xlsx", "application/octet-stream",
                new byte[0]);

        assertThatThrownBy(() -> service.importGlobal(empty, "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("为空");
    }

    // ===== 成员导入（全量覆盖） =====

    @Test
    void importMembers_shouldOverwriteAllRels() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001", "100002"));
        when(relMapper.deleteByTagId(1L)).thenReturn(3);

        PersonTagImportResultDTO result = service.importMembers(1L,
                memberXlsx(List.of(memberRow("100001", "张三"), memberRow("100002", "李四"))), "OP1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        verify(personTagService).requireTag(1L);
        verify(relMapper).deleteByTagId(1L);
        verify(relMapper).insertBatch(anyList());
    }

    @Test
    void importMembers_anyRowError_shouldNotTouchExistingData() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        PersonTagImportResultDTO result = service.importMembers(1L,
                memberXlsx(List.of(memberRow("100001", null), memberRow("BAD", null))), "OP1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        // 覆盖前置校验失败：不得清空原关联
        verify(relMapper, never()).deleteByTagId(anyLong());
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void importMembers_duplicateUsernameInFile_shouldReportRowError() {
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        PersonTagImportResultDTO result = service.importMembers(1L,
                memberXlsx(List.of(memberRow("100001", null), memberRow("100001", null))), "OP1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("重复");
    }
}
