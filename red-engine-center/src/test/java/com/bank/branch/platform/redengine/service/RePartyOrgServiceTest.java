package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.RePartyOrgTreeDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RePartyOrgService 单元测试 -- 纯 JUnit 5 + Mockito，不连数据库。
 * 覆盖 Task 7 简报要求的 2 个核心用例（树构建 1 父 2 子层级正确 / delete 有子节点抛 RE-40002 新增防呆），
 * 并补充 getById/add/update/delete(无子节点) 的基础行为用例。
 */
@ExtendWith(MockitoExtension.class)
class RePartyOrgServiceTest {

    @Mock
    private RePartyOrgMapper rePartyOrgMapper;

    @InjectMocks
    private RePartyOrgService rePartyOrgService;

    private static RePartyOrg org(Long id, Long parentId, String name) {
        RePartyOrg o = new RePartyOrg();
        o.setId(id);
        o.setParentId(parentId);
        o.setOrgName(name);
        o.setOrgLevel(parentId == null ? 1 : 2);
        return o;
    }

    @Test
    void getOrgTree_oneParentTwoChildren_buildsCorrectHierarchy() {
        RePartyOrg parent = org(1L, null, "党委");
        RePartyOrg child1 = org(2L, 1L, "第一支部");
        RePartyOrg child2 = org(3L, 1L, "第二支部");
        when(rePartyOrgMapper.selectList(any())).thenReturn(List.of(parent, child1, child2));

        List<RePartyOrgTreeDTO> tree = rePartyOrgService.getOrgTree();

        // 只有 1 个根节点（parentId 为 null 的党委），其余 2 个挂在其 children 下
        assertThat(tree).hasSize(1);
        RePartyOrgTreeDTO root = tree.get(0);
        assertThat(root.getId()).isEqualTo(1L);
        assertThat(root.getOrgName()).isEqualTo("党委");
        assertThat(root.getChildren()).hasSize(2);
        assertThat(root.getChildren())
                .extracting(RePartyOrgTreeDTO::getId)
                .containsExactly(2L, 3L);
        // 子节点自身无下级，children 应为空列表而非 null
        assertThat(root.getChildren().get(0).getChildren()).isEmpty();
    }

    @Test
    void getOrgTree_noOrgs_returnsEmptyList() {
        when(rePartyOrgMapper.selectList(any())).thenReturn(new ArrayList<>());

        List<RePartyOrgTreeDTO> tree = rePartyOrgService.getOrgTree();

        assertThat(tree).isEmpty();
    }

    @Test
    void getById_found_returnsDto() {
        RePartyOrg entity = org(5L, null, "党委");
        when(rePartyOrgMapper.selectById(5L)).thenReturn(entity);

        RePartyOrgTreeDTO dto = rePartyOrgService.getById(5L);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getOrgName()).isEqualTo("党委");
    }

    @Test
    void getById_notFound_returnsNull() {
        when(rePartyOrgMapper.selectById(404L)).thenReturn(null);

        RePartyOrgTreeDTO dto = rePartyOrgService.getById(404L);

        assertThat(dto).isNull();
    }

    @Test
    void add_insertsAndReturnsGeneratedId() {
        RePartyOrg newOrg = org(null, null, "新支部");
        // 模拟 MyBatis-Plus insert 后回填自增主键
        when(rePartyOrgMapper.insert(ArgumentMatchers.any(RePartyOrg.class))).thenAnswer(invocation -> {
            RePartyOrg arg = invocation.getArgument(0);
            arg.setId(99L);
            return 1;
        });

        Long id = rePartyOrgService.add(newOrg);

        assertThat(id).isEqualTo(99L);
        verify(rePartyOrgMapper).insert(newOrg);
    }

    @Test
    void update_setsPathIdAndDelegatesToUpdateById() {
        RePartyOrg payload = org(null, 1L, "改名支部");

        rePartyOrgService.update(7L, payload);

        assertThat(payload.getId()).isEqualTo(7L);
        verify(rePartyOrgMapper).updateById(payload);
    }

    @Test
    void delete_noChildren_deletesSuccessfully() {
        when(rePartyOrgMapper.selectCount(any())).thenReturn(0L);

        rePartyOrgService.delete(10L);

        verify(rePartyOrgMapper).deleteById(10L);
    }

    @Test
    void delete_hasChildren_throwsRe40002() {
        // 新增防呆：源系统 PartyOrgServiceImpl.delete 无此校验，本任务补齐——存在下级党组织时禁止删除
        when(rePartyOrgMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> rePartyOrgService.delete(1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo("RE-40002");
                    assertThat(bizEx.getMessage()).isEqualTo("存在下级党组织不可删除");
                });

        // 校验未通过必须直接短路，绝不触发实际删除
        verify(rePartyOrgMapper, never()).deleteById(ArgumentMatchers.anyLong());
    }
}
