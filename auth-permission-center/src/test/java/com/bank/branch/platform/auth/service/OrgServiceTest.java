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
