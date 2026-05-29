package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalTagServiceTest {

    @Mock
    private EvalTagMapper evalTagMapper;

    @InjectMocks
    private EvalTagService evalTagService;

    @Test
    @DisplayName("创建标签成功")
    void create_success() {
        when(evalTagMapper.selectByNameAndType("省分行正行长", 2)).thenReturn(null);
        when(evalTagMapper.insert(any(EvalTag.class))).thenReturn(1);

        EvalTag result = evalTagService.create("省分行正行长", 2);

        assertThat(result.getTagName()).isEqualTo("省分行正行长");
        assertThat(result.getTagType()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(1);
        verify(evalTagMapper).insert(any(EvalTag.class));
    }

    @Test
    @DisplayName("标签名称+类型重复时抛 PERF-40050")
    void create_whenDuplicate_throws40050() {
        EvalTag existing = new EvalTag();
        existing.setTagId(1L);
        when(evalTagMapper.selectByNameAndType("省分行正行长", 2)).thenReturn(existing);

        assertThatThrownBy(() -> evalTagService.create("省分行正行长", 2))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.EVAL_TAG_NAME_DUP));
    }

    @Test
    @DisplayName("更新标签名称成功")
    void update_success() {
        EvalTag existing = new EvalTag();
        existing.setTagId(1L);
        existing.setTagName("旧名称");
        existing.setTagType(1);
        when(evalTagMapper.selectById(1L)).thenReturn(existing);
        when(evalTagMapper.selectByNameAndType("新名称", 1)).thenReturn(null);

        evalTagService.update(1L, "新名称", 1);

        verify(evalTagMapper).updateById(any(EvalTag.class));
    }

    @Test
    @DisplayName("删除标签成功")
    void delete_success() {
        EvalTag existing = new EvalTag();
        existing.setTagId(1L);
        when(evalTagMapper.selectById(1L)).thenReturn(existing);

        evalTagService.delete(1L);

        verify(evalTagMapper).deleteById(1L);
    }

    @Test
    @DisplayName("listAll_按类型和状态过滤返回列表")
    void listAll_withTagTypeAndStatus_returnsFiltered() {
        EvalTag t1 = new EvalTag();
        t1.setTagId(10L);
        t1.setTagName("被评人A");
        t1.setTagType(1);
        t1.setStatus(1);
        when(evalTagMapper.selectAll(1, 1)).thenReturn(List.of(t1));

        List<EvalTag> result = evalTagService.listAll(1, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTagName()).isEqualTo("被评人A");
        verify(evalTagMapper).selectAll(1, 1);
    }

    @Test
    @DisplayName("listAll_不传参数时返回全部标签")
    void listAll_withNullParams_returnsAll() {
        EvalTag t1 = new EvalTag();
        t1.setTagId(10L);
        t1.setTagType(1);
        EvalTag t2 = new EvalTag();
        t2.setTagId(11L);
        t2.setTagType(2);
        when(evalTagMapper.selectAll(null, null)).thenReturn(List.of(t1, t2));

        List<EvalTag> result = evalTagService.listAll(null, null);

        assertThat(result).hasSize(2);
        verify(evalTagMapper).selectAll(null, null);
    }
}
