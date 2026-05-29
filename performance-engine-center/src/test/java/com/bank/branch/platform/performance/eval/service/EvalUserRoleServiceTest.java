package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.portal.api.AddressBookApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId(1001L); old1.setTagId(7L);
        EvalUserTag old2 = new EvalUserTag(); old2.setUserId(1001L); old2.setTagId(8L);
        when(evalUserTagMapper.selectByUserId(1001L)).thenReturn(List.of(old1, old2));
        when(evalTagMapper.selectById(1L)).thenReturn(tag(1L, 1));
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));
        when(evalTagMapper.selectById(3L)).thenReturn(tag(3L, 2));

        service.saveUserRoles(1001L, 1L, List.of(2L, 3L));

        verify(evalUserTagMapper).batchDelete(eq(1001L), eq(List.of(7L, 8L)));
        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId=null 表示清空被评价角色，仅插入评价人标签")
    void saveUserRoles_nullBeEval_onlyEvalTags() {
        when(evalUserTagMapper.selectByUserId(1001L)).thenReturn(List.of());
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));

        service.saveUserRoles(1001L, null, List.of(2L));

        verify(evalUserTagMapper, never()).batchDelete(anyLong(), anyList());
        verify(evalUserTagMapper).batchInsert(insertCaptor.capture());
        assertThat(insertCaptor.getValue()).extracting(EvalUserTag::getTagId).containsExactly(2L);
    }

    @Test
    @DisplayName("saveUserRoles 全部清空：beEvalTagId=null + evalTagIds 空，仅删除不插入")
    void saveUserRoles_clearAll() {
        EvalUserTag old1 = new EvalUserTag(); old1.setUserId(1001L); old1.setTagId(7L);
        when(evalUserTagMapper.selectByUserId(1001L)).thenReturn(List.of(old1));

        service.saveUserRoles(1001L, null, List.of());

        verify(evalUserTagMapper).batchDelete(eq(1001L), eq(List.of(7L)));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId 指向评价人标签(tagType=2) → PERF-40059")
    void saveUserRoles_beEvalWrongType_throws() {
        when(evalTagMapper.selectById(2L)).thenReturn(tag(2L, 2));

        assertThatThrownBy(() -> service.saveUserRoles(1001L, 2L, List.of()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH));
        verify(evalUserTagMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("saveUserRoles evalTagIds 含被评价标签(tagType=1) → PERF-40059")
    void saveUserRoles_evalWrongType_throws() {
        when(evalTagMapper.selectById(1L)).thenReturn(tag(1L, 1));

        assertThatThrownBy(() -> service.saveUserRoles(1001L, null, List.of(1L)))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH));
    }

    @Test
    @DisplayName("saveUserRoles beEvalTagId 指向不存在标签 → PERF-40058")
    void saveUserRoles_beEvalNotFound_throws() {
        when(evalTagMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveUserRoles(1001L, 99L, List.of()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_RULE_NOT_FOUND));
    }
}
