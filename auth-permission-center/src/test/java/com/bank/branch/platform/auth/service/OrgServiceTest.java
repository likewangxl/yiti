package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.OrgTreeNodeDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrgServiceTest {

    @Mock OrgMapper orgMapper;
    @Mock UserOrgMapper userOrgMapper;
    @InjectMocks OrgService orgService;

    private ExtOrgInfo branch;
    private ExtOrgInfo sub1;
    private ExtOrgInfo sub2;

    @BeforeEach
    void setUp() {
        branch = new ExtOrgInfo();
        branch.setOrgCode("ORG001");
        branch.setOrgName("测试分行");
        branch.setOrgLevel(2);
        branch.setPId(null);
        branch.setOrganState(0);

        sub1 = new ExtOrgInfo();
        sub1.setOrgCode("ORG001001");
        sub1.setOrgName("测试支行1");
        sub1.setOrgLevel(3);
        sub1.setPId("ORG001");
        sub1.setOrganState(0);

        sub2 = new ExtOrgInfo();
        sub2.setOrgCode("ORG001002");
        sub2.setOrgName("测试支行2");
        sub2.setOrgLevel(3);
        sub2.setPId("ORG001");
        sub2.setOrganState(0);
    }

    @Test
    void getOrgsByCodes_mapsBatchResultsToDtos() {
        when(orgMapper.selectByOrgCodes(any())).thenReturn(List.of(branch, sub1));
        List<OrgDTO> dtos = orgService.getOrgsByCodes(List.of("ORG001", "ORG001001"));
        assertThat(dtos).extracting(OrgDTO::getOrgCode)
                .containsExactlyInAnyOrder("ORG001", "ORG001001");
    }

    @Test
    void getOrgsByCodes_emptyOrNull_returnsEmptyNoQuery() {
        assertThat(orgService.getOrgsByCodes(null)).isEmpty();
        assertThat(orgService.getOrgsByCodes(List.of())).isEmpty();
        assertThat(orgService.getOrgsByCodes(List.of("  "))).isEmpty();
        verify(orgMapper, never()).selectByOrgCodes(any());
    }

    @Test
    void getOrg_shouldReturnDtoWhenExists() {
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(branch);

        OrgDTO dto = orgService.getOrg("ORG001");

        assertThat(dto.getOrgCode()).isEqualTo("ORG001");
        assertThat(dto.getOrgName()).isEqualTo("测试分行");
        assertThat(dto.getOrgLevel()).isEqualTo(2);
    }

    @Test
    void getOrg_shouldReturnNullWhenNotFound() {
        when(orgMapper.selectByOrgCode("NONE")).thenReturn(null);

        OrgDTO dto = orgService.getOrg("NONE");

        assertThat(dto).isNull();
    }

    @Test
    void getOrgSubtree_shouldReturnSelfAndChildren() {
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(branch);
        when(orgMapper.selectChildren("ORG001")).thenReturn(List.of(sub1, sub2));
        when(orgMapper.selectChildren("ORG001001")).thenReturn(List.of());
        when(orgMapper.selectChildren("ORG001002")).thenReturn(List.of());

        List<OrgDTO> subtree = orgService.getOrgSubtree("ORG001");

        assertThat(subtree).hasSize(3);
        assertThat(subtree).extracting(OrgDTO::getOrgCode)
            .containsExactlyInAnyOrder("ORG001", "ORG001001", "ORG001002");
    }

    @Test
    void getOrgSubtreeCodes_shouldReturnAllCodesIncludingSelf() {
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(branch);
        when(orgMapper.selectChildren("ORG001")).thenReturn(List.of(sub1, sub2));
        when(orgMapper.selectChildren("ORG001001")).thenReturn(List.of());
        when(orgMapper.selectChildren("ORG001002")).thenReturn(List.of());

        Set<String> codes = orgService.getOrgSubtreeCodes("ORG001");

        assertThat(codes).containsExactlyInAnyOrder("ORG001", "ORG001001", "ORG001002");
    }

    @Test
    void getOrgSubtreeCodes_singleOrgNoChildren_shouldReturnSelfOnly() {
        when(orgMapper.selectByOrgCode("ORG001001")).thenReturn(sub1);
        when(orgMapper.selectChildren("ORG001001")).thenReturn(List.of());

        Set<String> codes = orgService.getOrgSubtreeCodes("ORG001001");

        assertThat(codes).containsExactly("ORG001001");
    }

    @Test
    void getUserMainOrg_shouldReturnOrgForUser() {
        ExtUserOrg userOrg = new ExtUserOrg();
        userOrg.setUserId("E001");
        userOrg.setOrgCode("ORG001");

        when(userOrgMapper.selectByUserId("E001")).thenReturn(userOrg);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(branch);

        OrgDTO dto = orgService.getUserMainOrg("E001");

        assertThat(dto.getOrgCode()).isEqualTo("ORG001");
    }

    @Test
    void getUserMainOrg_shouldThrowWhenUserHasNoOrg() {
        when(userOrgMapper.selectByUserId("E999")).thenReturn(null);

        assertThatThrownBy(() -> orgService.getUserMainOrg("E999"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40403"));
    }

    @Test
    void createOrg_shouldAutoGenerateOrgCodeAndPersistDeptNo() {
        // 新增：前端不传 orgCode，后端按 max 数字编码 +1 自增；deptNo 由用户输入落库
        com.bank.branch.platform.auth.api.dto.OrgCreateReqDTO req =
            new com.bank.branch.platform.auth.api.dto.OrgCreateReqDTO();
        req.setOrgName("新支行");
        req.setPId("1");
        req.setDeptNo("720199");

        ExtOrgInfo parent = new ExtOrgInfo();
        parent.setOrgCode("1");
        parent.setOrgLevel(1);
        when(orgMapper.selectByOrgCode("1")).thenReturn(parent);
        when(orgMapper.selectMaxNumericOrgCode()).thenReturn(572L);
        // 自增后回查新机构（getOrg 用）
        ExtOrgInfo created = new ExtOrgInfo();
        created.setOrgCode("573");
        created.setOrgName("新支行");
        created.setOrgLevel(2);
        created.setDeptNo("720199");
        when(orgMapper.selectByOrgCode("573")).thenReturn(created);

        OrgDTO dto = orgService.createOrg(req);

        assertThat(dto.getOrgCode()).isEqualTo("573");
        // 落库实体：orgCode=573（自增）、deptNo=720199、level=父级+1=2
        verify(orgMapper).insert(org.mockito.ArgumentMatchers.<ExtOrgInfo>argThat(e ->
            "573".equals(e.getOrgCode())
            && "720199".equals(e.getDeptNo())
            && e.getOrgLevel() == 2
            && "1".equals(e.getPId())));
    }

    @Test
    void updateOrg_disable_withUsers_shouldThrow() {
        // 机构下有用户时禁止禁用
        com.bank.branch.platform.auth.api.dto.OrgUpdateReqDTO req =
            new com.bank.branch.platform.auth.api.dto.OrgUpdateReqDTO();
        req.setOrganState(1);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(branch);
        when(userOrgMapper.countUsersByOrgCode("ORG001", null)).thenReturn(3L);

        assertThatThrownBy(() -> orgService.updateOrg("ORG001", req))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40303"));
        verify(orgMapper, never()).updateById(org.mockito.ArgumentMatchers.<com.bank.branch.platform.auth.entity.ExtOrgInfo>any());
    }

    @Test
    void updateOrg_disable_noUsers_shouldSetOrganState1() {
        com.bank.branch.platform.auth.api.dto.OrgUpdateReqDTO req =
            new com.bank.branch.platform.auth.api.dto.OrgUpdateReqDTO();
        req.setOrganState(1);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(branch);
        when(userOrgMapper.countUsersByOrgCode("ORG001", null)).thenReturn(0L);

        orgService.updateOrg("ORG001", req);

        assertThat(branch.getOrganState()).isEqualTo(1);
        verify(orgMapper).updateById(org.mockito.ArgumentMatchers.<com.bank.branch.platform.auth.entity.ExtOrgInfo>eq(branch));
    }

    @Test
    void updateOrg_enable_shouldNotCheckUsers() {
        // 启用(organState=0)无需校验用户数
        com.bank.branch.platform.auth.api.dto.OrgUpdateReqDTO req =
            new com.bank.branch.platform.auth.api.dto.OrgUpdateReqDTO();
        req.setOrganState(0);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(branch);

        orgService.updateOrg("ORG001", req);

        assertThat(branch.getOrganState()).isEqualTo(0);
        verify(userOrgMapper, never()).countUsersByOrgCode(anyString(), any());
        verify(orgMapper).updateById(org.mockito.ArgumentMatchers.<com.bank.branch.platform.auth.entity.ExtOrgInfo>eq(branch));
    }

    @Test
    void searchOrgs_shouldDelegateToMapper() {
        when(orgMapper.searchByKeyword("测试", 10)).thenReturn(List.of(branch));

        List<OrgDTO> results = orgService.searchOrgs("测试", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrgName()).isEqualTo("测试分行");
    }

    @Test
    void getOrgTree_shouldBuildHierarchyFromAllOrgs() {
        // 模拟三层结构：root → branch → sub1/sub2
        ExtOrgInfo root = makeOrg("ROOT", "总行", null);
        branch.setPId("ROOT"); // 将分行设为总行的子节点
        when(orgMapper.selectAll()).thenReturn(List.of(root, branch, sub1, sub2));

        List<OrgTreeNodeDTO> tree = orgService.getOrgTree();

        // 顶层只有总行
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getOrgCode()).isEqualTo("ROOT");
        // 总行有一个子节点（分行）
        assertThat(tree.get(0).getChildren()).hasSize(1);
        // 分行有两个子节点
        assertThat(tree.get(0).getChildren().get(0).getChildren()).hasSize(2);
    }

    private ExtOrgInfo makeOrg(String orgCode, String orgName, String parentOrgCode) {
        ExtOrgInfo org = new ExtOrgInfo();
        org.setOrgCode(orgCode);
        org.setOrgName(orgName);
        org.setPId(parentOrgCode);
        return org;
    }
}
