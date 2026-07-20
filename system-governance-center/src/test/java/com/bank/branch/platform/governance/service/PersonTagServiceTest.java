package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberRespDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagRespDTO;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.bank.branch.platform.governance.mapper.PersonTagMapper;
import com.bank.branch.platform.governance.mapper.PersonTagRelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonTagService 单元测试（Mock Mapper + UserApi）。
 */
@ExtendWith(MockitoExtension.class)
class PersonTagServiceTest {

    @Mock
    private PersonTagMapper tagMapper;
    @Mock
    private PersonTagRelMapper relMapper;
    @Mock
    private UserApi userApi;

    private PersonTagService service;

    @BeforeEach
    void setUp() {
        service = new PersonTagService(tagMapper, relMapper, userApi);
    }

    private static PersonTag tag(Long id, String name) {
        PersonTag t = new PersonTag();
        t.setTagId(id);
        t.setTagName(name);
        return t;
    }

    private static PersonTagRel rel(Long id, Long tagId, String username) {
        PersonTagRel r = new PersonTagRel();
        r.setId(id);
        r.setTagId(tagId);
        r.setUsername(username);
        return r;
    }

    private static UserDTO user(String username, String displayName, String orgCode, String orgName) {
        UserDTO u = new UserDTO();
        u.setUsername(username);
        u.setDisplayName(displayName);
        u.setMainOrgCode(orgCode);
        u.setMainOrgName(orgName);
        return u;
    }

    // ===== 标签 CRUD =====

    @Test
    void pageTags_shouldReturnRecordsWithTotal() {
        when(tagMapper.countByKeyword("重点")).thenReturn(1L);
        PersonTagRespDTO row = new PersonTagRespDTO();
        row.setTagId(1L);
        row.setTagName("重点培养");
        row.setMemberCount(3L);
        when(tagMapper.selectPageWithMemberCount("重点", 0, 20)).thenReturn(List.of(row));

        PageResult<PersonTagRespDTO> page = service.pageTags("重点", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getMemberCount()).isEqualTo(3L);
    }

    @Test
    void pageTags_totalZero_shouldSkipPageQuery() {
        when(tagMapper.countByKeyword(null)).thenReturn(0L);

        PageResult<PersonTagRespDTO> page = service.pageTags(null, 1, 20);

        assertThat(page.getRecords()).isEmpty();
        verify(tagMapper, never()).selectPageWithMemberCount(anyString(), anyInt(), anyInt());
    }

    @Test
    void createTag_shouldTrimNameAndInsert() {
        when(tagMapper.selectByTagName("骨干")).thenReturn(null);

        PersonTag created = service.createTag(" 骨干 ", "备注", "OP1");

        assertThat(created.getTagName()).isEqualTo("骨干");
        assertThat(created.getCreateBy()).isEqualTo("OP1");
        verify(tagMapper).insert(any(PersonTag.class));
    }

    @Test
    void createTag_duplicateName_shouldThrow40904() {
        when(tagMapper.selectByTagName("骨干")).thenReturn(tag(9L, "骨干"));

        assertThatThrownBy(() -> service.createTag("骨干", null, "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
        verify(tagMapper, never()).insert(any(PersonTag.class));
    }

    @Test
    void updateTag_nameTakenByOtherTag_shouldThrow40904() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "旧名"));
        when(tagMapper.selectByTagName("新名")).thenReturn(tag(2L, "新名"));

        assertThatThrownBy(() -> service.updateTag(1L, "新名", null, "OP1"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void updateTag_sameTagKeepName_shouldPass() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));
        when(tagMapper.selectByTagName("骨干")).thenReturn(tag(1L, "骨干"));

        service.updateTag(1L, "骨干", "新备注", "OP1");

        verify(tagMapper).updateById(any(PersonTag.class));
    }

    @Test
    void deleteTag_shouldCascadeRelBeforeTag() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));
        when(relMapper.deleteByTagId(1L)).thenReturn(5);

        service.deleteTag(1L);

        InOrder inOrder = inOrder(relMapper, tagMapper);
        inOrder.verify(relMapper).deleteByTagId(1L);
        inOrder.verify(tagMapper).deleteById(1L);
    }

    @Test
    void deleteTag_notFound_shouldThrow40008() {
        when(tagMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteTag(99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
        verify(relMapper, never()).deleteByTagId(any());
    }

    // ===== 成员查询 =====

    @Test
    void pageMembers_shouldResolveNameAndOrgByUsername() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));
        when(relMapper.countByTagId(1L)).thenReturn(2L);
        when(relMapper.selectPageByTagId(1L, 0, 20))
                .thenReturn(List.of(rel(11L, 1L, "100001"), rel(12L, 1L, "gone")));
        when(userApi.getUsersByUsernames(List.of("100001", "gone")))
                .thenReturn(List.of(user("100001", "张三", "107", "城东支行")));

        PageResult<PersonTagMemberRespDTO> page = service.pageMembers(1L, 1, 20);

        assertThat(page.getTotal()).isEqualTo(2);
        PersonTagMemberRespDTO first = page.getRecords().get(0);
        assertThat(first.getDisplayName()).isEqualTo("张三");
        assertThat(first.getOrgName()).isEqualTo("城东支行");
        // 已删除用户解析不到：保留工号，姓名/机构为空
        PersonTagMemberRespDTO second = page.getRecords().get(1);
        assertThat(second.getUsername()).isEqualTo("gone");
        assertThat(second.getDisplayName()).isNull();
    }

    // ===== 成员增删改 =====

    @Test
    void addMembers_shouldSkipExistingAndInsertRest() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));
        when(userApi.filterExistingUsernames(List.of("100001", "100002")))
                .thenReturn(List.of("100001", "100002"));
        when(relMapper.selectUsernamesByTagId(1L)).thenReturn(List.of("100001"));

        int added = service.addMembers(1L, List.of(" 100001 ", "100002", "100002"), "OP1");

        assertThat(added).isEqualTo(1);
        verify(relMapper).insertBatch(argThatSize(1));
    }

    @Test
    void addMembers_invalidUsername_shouldThrow42208() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        assertThatThrownBy(() -> service.addMembers(1L, List.of("100001", "BAD"), "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("BAD");
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void updateMember_shouldValidateAndUpdate() {
        when(relMapper.selectById(11L)).thenReturn(rel(11L, 1L, "100001"));
        when(userApi.filterExistingUsernames(List.of("100002"))).thenReturn(List.of("100002"));
        when(relMapper.selectUsernamesByTagId(1L)).thenReturn(List.of("100001"));

        service.updateMember(1L, 11L, "100002", "OP1");

        verify(relMapper).updateById(any(PersonTagRel.class));
    }

    @Test
    void updateMember_duplicateInTag_shouldThrow40905() {
        when(relMapper.selectById(11L)).thenReturn(rel(11L, 1L, "100001"));
        when(userApi.filterExistingUsernames(List.of("100002"))).thenReturn(List.of("100002"));
        when(relMapper.selectUsernamesByTagId(1L)).thenReturn(List.of("100001", "100002"));

        assertThatThrownBy(() -> service.updateMember(1L, 11L, "100002", "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已在标签下");
    }

    @Test
    void updateMember_relBelongsToOtherTag_shouldThrow40009() {
        when(relMapper.selectById(11L)).thenReturn(rel(11L, 2L, "100001"));

        assertThatThrownBy(() -> service.updateMember(1L, 11L, "100002", "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("成员不存在");
    }

    @Test
    void removeMember_shouldDeleteById() {
        when(relMapper.selectById(11L)).thenReturn(rel(11L, 1L, "100001"));

        service.removeMember(1L, 11L);

        verify(relMapper).deleteById(11L);
    }

    @Test
    void removeMember_wrongTag_shouldThrow40009() {
        when(relMapper.selectById(11L)).thenReturn(rel(11L, 2L, "100001"));

        assertThatThrownBy(() -> service.removeMember(1L, 11L))
                .isInstanceOf(BizException.class);
        verify(relMapper, never()).deleteById(eq(11L));
    }

    /** 断言批量插入行数的 List 匹配器。 */
    private static List<PersonTagRel> argThatSize(int size) {
        return org.mockito.ArgumentMatchers.argThat(list -> list != null && list.size() == size);
    }
}
