package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalUserRoleServiceTest {

    @Mock private EvalUserTagMapper evalUserTagMapper;
    @Mock private EvalTagMapper evalTagMapper;
    @Mock private UserApi userApi;
    @Mock private AddressBookApi addressBookApi;

    @InjectMocks private EvalUserTagService service;

    @Captor private ArgumentCaptor<List<EvalUserTag>> insertCaptor;

    private EvalTag tag(long id, int type) {
        EvalTag t = new EvalTag();
        t.setTagId(id);
        t.setTagType(type);
        return t;
    }

    @Test
    @DisplayName("saveUserRoles 覆盖：先删该用户全部旧标签，再插入新被评价+评价人组合")
    void saveUserRoles_overwrite() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId("1001"); old1.setTagId(7L);
        EvalUserTag old2 = new EvalUserTag(); old2.setUserId("1001"); old2.setTagId(8L);
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of(old1, old2));
        when(evalTagMapper.selectById(1L)).thenReturn(tag(1L, 1));
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));
        when(evalTagMapper.selectById(3L)).thenReturn(tag(3L, 2));

        service.saveUserRoles("1001", 1L, List.of(2L, 3L));

        verify(evalUserTagMapper).batchDelete(eq("1001"), eq(List.of(7L, 8L)));
        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId=null 表示清空被评价角色，仅插入评价人标签")
    void saveUserRoles_nullBeEval_onlyEvalTags() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));

        service.saveUserRoles("1001", null, List.of(2L));

        verify(evalUserTagMapper, never()).batchDelete(anyString(), anyList());
        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(2L);
    }

    @Test
    @DisplayName("saveUserRoles 全部清空：beEvalTagId=null + evalTagIds 空，仅删除不插入")
    void saveUserRoles_clearAll() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId("1001"); old1.setTagId(7L);
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of(old1));

        service.saveUserRoles("1001", null, List.of());

        verify(evalUserTagMapper).batchDelete(eq("1001"), eq(List.of(7L)));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId 指向评价人标签(tagType=2) → PERF-40059")
    void saveUserRoles_beEvalWrongType_throws() {
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));

        assertThatThrownBy(() -> service.saveUserRoles("1001", 2L, List.of()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRoles evalTagIds 含被评价标签(tagType=1) → PERF-40059")
    void saveUserRoles_evalWrongType_throws() {
        when(evalTagMapper.selectById(1L)).thenReturn(tag(1L, 1));

        assertThatThrownBy(() -> service.saveUserRoles("1001", null, List.of(1L)))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH));
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId 指向不存在标签 → PERF-40058")
    void saveUserRoles_beEvalNotFound_throws() {
        when(evalTagMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveUserRoles("1001", 99L, List.of()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_RULE_NOT_FOUND));
    }

    private UserDTO user(String empId, String name) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setDisplayName(name);
        return u;
    }

    private EvalUserTagRow tagRow(String uid, long tid, String name, int type) {
        EvalUserTagRow r = new EvalUserTagRow();
        r.setUserId(uid); r.setTagId(tid); r.setTagName(name); r.setTagType(type);
        return r;
    }

    @Test
    @DisplayName("pageUserRoles 拼装：被评价取单个、评价人取列表、部门/岗位/角色到位")
    void pageUserRoles_assembles() {
        when(userApi.pageUsers("张", 1, 20))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user("1001", "张三"))));
        when(addressBookApi.getEmployees(List.of("1001")))
                .thenReturn(List.of(EmployeeDTO.builder().empId("1001").orgName("某支行").position("行长").build()));
        RoleSimpleDTO role = new RoleSimpleDTO();
        role.setRoleChName("管理员");
        when(userApi.getRolesByUserIds(List.of("1001"))).thenReturn(Map.of("1001", List.of(role)));
        when(evalUserTagMapper.selectUserTagsByUserIds(List.of("1001")))
                .thenReturn(List.of(
                        tagRow("1001", 1L, "支行行长", 1),
                        tagRow("1001", 2L, "副行长评委", 2),
                        tagRow("1001", 3L, "同级评委", 2)));

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles("张", 1, 20);

        assertThat(r.getTotal()).isEqualTo(1L);
        EvalUserRoleRowDTO row = r.getRecords().get(0);
        assertThat(row.getUserId()).isEqualTo("1001");
        assertThat(row.getUserName()).isEqualTo("张三");
        assertThat(row.getOrgName()).isEqualTo("某支行");
        assertThat(row.getPosition()).isEqualTo("行长");
        assertThat(row.getRoleNames()).containsExactly("管理员");
        assertThat(row.getBeEvalTag().getTagName()).isEqualTo("支行行长");
        assertThat(row.getEvalTags()).extracting(t -> t.getTagName())
                .containsExactlyInAnyOrder("副行长评委", "同级评委");
    }

    @Test
    @DisplayName("pageUserRoles 空页：用户列表为空时直接返回空 records，不查下游")
    void pageUserRoles_emptyPage() {
        when(userApi.pageUsers(null, 1, 20)).thenReturn(PageResult.of(1, 20, 0L, List.of()));

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, 1, 20);

        assertThat(r.getRecords()).isEmpty();
        verify(addressBookApi, never()).getEmployees(anyList());
    }

    @Test
    @DisplayName("saveUserRoles evalTagIds 含重复 id 时去重后只插入一次")
    void saveUserRoles_dedupEvalTagIds() {
        when(evalUserTagMapper.selectByUserId("1001")).thenReturn(List.of());
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));

        service.saveUserRoles("1001", null, List.of(2L, 2L, 2L));

        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(2L);
    }

    @Test
    @DisplayName("pageUserRoles 非数值工号：直接以 String 查 EVAL_USER_TAG，无匹配则标签列为空但不报错")
    void pageUserRoles_nonNumericEmpId() {
        when(userApi.pageUsers(null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user("U_ABC123", "李四"))));
        when(addressBookApi.getEmployees(List.of("U_ABC123"))).thenReturn(List.of());
        when(userApi.getRolesByUserIds(List.of("U_ABC123"))).thenReturn(Map.of());
        when(evalUserTagMapper.selectUserTagsByUserIds(List.of("U_ABC123"))).thenReturn(List.of());

        PageResult<EvalUserRoleRowDTO> r = service.pageUserRoles(null, 1, 20);

        EvalUserRoleRowDTO row = r.getRecords().get(0);
        assertThat(row.getBeEvalTag()).isNull();
        assertThat(row.getEvalTags()).isEmpty();
        assertThat(row.getRoleNames()).isEmpty();
        verify(evalUserTagMapper).selectUserTagsByUserIds(List.of("U_ABC123"));
    }
}
