package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
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
 * PersonTagService 单元测试（Mock Mapper + UserApi + OrgApi）。
 */
@ExtendWith(MockitoExtension.class)
class PersonTagServiceTest {

    @Mock
    private PersonTagMapper tagMapper;
    @Mock
    private PersonTagRelMapper relMapper;
    @Mock
    private UserApi userApi;
    @Mock
    private OrgApi orgApi;

    private PersonTagService service;

    @BeforeEach
    void setUp() {
        service = new PersonTagService(tagMapper, relMapper, userApi, orgApi);
    }

    private static PersonTag tag(Long id, String name) {
        PersonTag t = new PersonTag();
        t.setTagId(id);
        t.setTagName(name);
        return t;
    }

    private static PersonTagRel empRel(Long id, Long tagId, String username) {
        PersonTagRel r = new PersonTagRel();
        r.setId(id);
        r.setTagId(tagId);
        r.setDimType(PersonTagRel.DIM_EMP);
        r.setUsername(username);
        return r;
    }

    private static PersonTagRel orgRel(Long id, Long tagId, String deptNo) {
        PersonTagRel r = new PersonTagRel();
        r.setId(id);
        r.setTagId(tagId);
        r.setDimType(PersonTagRel.DIM_ORG);
        r.setOrgDeptNo(deptNo);
        return r;
    }

    private static OrgDTO org(String deptNo, String orgName) {
        OrgDTO o = new OrgDTO();
        o.setDeptNo(deptNo);
        o.setOrgName(orgName);
        return o;
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

    // ===== 成员查询（分维度） =====

    @Test
    void pageMembers_empDim_shouldResolveDisplayNameInSingleBatch() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));
        when(relMapper.countByTagId(1L, "EMP")).thenReturn(2L);
        when(relMapper.selectPageByTagId(1L, "EMP", 0, 20))
                .thenReturn(List.of(empRel(11L, 1L, "100001"), empRel(12L, 1L, "100002")));
        UserDTO user = new UserDTO();
        user.setUsername("100001");
        user.setDisplayName("张三");
        when(userApi.getUsersByUsernames(List.of("100001", "100002")))
                .thenReturn(List.of(user));

        PageResult<PersonTagMemberRespDTO> page = service.pageMembers(1L, "EMP", 1, 20);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords().get(0).getDimType()).isEqualTo("EMP");
        assertThat(page.getRecords().get(0).getUsername()).isEqualTo("100001");
        assertThat(page.getRecords().get(0).getDisplayName()).isEqualTo("张三");
        assertThat(page.getRecords().get(1).getDisplayName()).isNull();
        verify(userApi).getUsersByUsernames(List.of("100001", "100002"));
    }

    @Test
    void pageMembers_orgDim_shouldResolveOrgNameByDeptNo() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "重点机构"));
        when(relMapper.countByTagId(1L, "ORG")).thenReturn(2L);
        when(relMapper.selectPageByTagId(1L, "ORG", 0, 20))
                .thenReturn(List.of(orgRel(21L, 1L, "0101"), orgRel(22L, 1L, "9999")));
        when(orgApi.getOrgsByDeptNos(List.of("0101", "9999")))
                .thenReturn(List.of(org("0101", "城东支行")));

        PageResult<PersonTagMemberRespDTO> page = service.pageMembers(1L, "ORG", 1, 20);

        PersonTagMemberRespDTO first = page.getRecords().get(0);
        assertThat(first.getDimType()).isEqualTo("ORG");
        assertThat(first.getOrgDeptNo()).isEqualTo("0101");
        assertThat(first.getOrgName()).isEqualTo("城东支行");
        // 机构不存在解析不到：保留编号，名称为空
        assertThat(page.getRecords().get(1).getOrgName()).isNull();
    }

    // ===== 成员新增（双维度） =====

    @Test
    void addMembers_bothDims_shouldSkipExistingAndInsertRest() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));
        when(userApi.filterExistingUsernames(List.of("100001", "100002")))
                .thenReturn(List.of("100001", "100002"));
        when(relMapper.selectUsernamesByTagId(1L)).thenReturn(List.of("100001"));
        when(orgApi.getOrgsByDeptNos(List.of("0101", "0102")))
                .thenReturn(List.of(org("0101", "城东"), org("0102", "城西")));
        when(relMapper.selectDeptNosByTagId(1L)).thenReturn(List.of("0102"));

        int added = service.addMembers(1L,
                List.of(" 100001 ", "100002", "100002"), List.of("0101", "0102"), "OP1");

        // 员工新增 100002（100001 已存在跳过）+ 机构新增 0101（0102 已存在跳过）= 2
        assertThat(added).isEqualTo(2);
        verify(relMapper).insertBatch(argThatSize(2));
    }

    @Test
    void addMembers_onlyOrg_shouldInsertOrgRows() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "重点机构"));
        when(orgApi.getOrgsByDeptNos(List.of("0101"))).thenReturn(List.of(org("0101", "城东")));
        when(relMapper.selectDeptNosByTagId(1L)).thenReturn(List.of());

        int added = service.addMembers(1L, null, List.of("0101"), "OP1");

        assertThat(added).isEqualTo(1);
        verify(userApi, never()).filterExistingUsernames(anyList());
    }

    @Test
    void addMembers_invalidUsername_shouldThrow42208() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of("100001"));

        assertThatThrownBy(() -> service.addMembers(1L, List.of("100001", "BAD"), null, "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("BAD");
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void addMembers_invalidDeptNo_shouldThrow42209() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "重点机构"));
        when(orgApi.getOrgsByDeptNos(anyList())).thenReturn(List.of(org("0101", "城东")));

        assertThatThrownBy(() -> service.addMembers(1L, null, List.of("0101", "BADORG"), "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("BADORG");
        verify(relMapper, never()).insertBatch(anyList());
    }

    @Test
    void addMembers_bothEmpty_shouldThrow42210() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "骨干"));

        assertThatThrownBy(() -> service.addMembers(1L, List.of(), List.of(), "OP1"))
                .isInstanceOf(BizException.class);
        verify(relMapper, never()).insertBatch(anyList());
    }

    // ===== 成员修改（按行维度分流） =====

    @Test
    void updateMember_empRow_shouldValidateAndUpdate() {
        when(relMapper.selectById(11L)).thenReturn(empRel(11L, 1L, "100001"));
        when(userApi.filterExistingUsernames(List.of("100002"))).thenReturn(List.of("100002"));
        when(relMapper.selectUsernamesByTagId(1L)).thenReturn(List.of("100001"));

        service.updateMember(1L, 11L, "100002", null, "OP1");

        verify(relMapper).updateById(any(PersonTagRel.class));
    }

    @Test
    void updateMember_orgRow_shouldValidateDeptNoAndUpdate() {
        when(relMapper.selectById(21L)).thenReturn(orgRel(21L, 1L, "0101"));
        when(orgApi.getOrgsByDeptNos(List.of("0102"))).thenReturn(List.of(org("0102", "城西")));
        when(relMapper.selectDeptNosByTagId(1L)).thenReturn(List.of("0101"));

        service.updateMember(1L, 21L, null, "0102", "OP1");

        verify(relMapper).updateById(any(PersonTagRel.class));
    }

    @Test
    void updateMember_duplicateInTag_shouldThrow40905() {
        when(relMapper.selectById(11L)).thenReturn(empRel(11L, 1L, "100001"));
        when(userApi.filterExistingUsernames(List.of("100002"))).thenReturn(List.of("100002"));
        when(relMapper.selectUsernamesByTagId(1L)).thenReturn(List.of("100001", "100002"));

        assertThatThrownBy(() -> service.updateMember(1L, 11L, "100002", null, "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已在标签下");
    }

    @Test
    void updateMember_relBelongsToOtherTag_shouldThrow40009() {
        when(relMapper.selectById(11L)).thenReturn(empRel(11L, 2L, "100001"));

        assertThatThrownBy(() -> service.updateMember(1L, 11L, "100002", null, "OP1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("成员不存在");
    }

    @Test
    void removeMember_shouldDeleteById() {
        when(relMapper.selectById(11L)).thenReturn(empRel(11L, 1L, "100001"));

        service.removeMember(1L, 11L);

        verify(relMapper).deleteById(11L);
    }

    @Test
    void removeMember_wrongTag_shouldThrow40009() {
        when(relMapper.selectById(11L)).thenReturn(empRel(11L, 2L, "100001"));

        assertThatThrownBy(() -> service.removeMember(1L, 11L))
                .isInstanceOf(BizException.class);
        verify(relMapper, never()).deleteById(eq(11L));
    }

    /** 断言批量插入行数的 List 匹配器。 */
    private static List<PersonTagRel> argThatSize(int size) {
        return org.mockito.ArgumentMatchers.argThat(list -> list != null && list.size() == size);
    }
}
