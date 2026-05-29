package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AllocAdjustPreviewService} 单元测试：原业绩分配预览（取审批通过的最后一条申请明细）.
 */
@ExtendWith(MockitoExtension.class)
class AllocAdjustPreviewServiceTest {

    @Mock
    private PerfAllocAdjustApplyMapper applyMapper;
    @Mock
    private PerfAllocAdjustItemMapper itemMapper;
    @Mock
    private CustomerQueryApi customerQueryApi;
    @Mock
    private UserApi userApi;

    @InjectMocks
    private AllocAdjustPreviewService service;

    private static PerfAllocAdjustApply apply(String id, String allocDim, String accountNo) {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId(id);
        a.setAllocDim(allocDim);
        a.setAccountNo(accountNo);
        a.setStatus("APPROVED");
        return a;
    }

    private static PerfAllocAdjustItem item(String empId, String ratio) {
        PerfAllocAdjustItem it = new PerfAllocAdjustItem();
        it.setEmpId(empId);
        it.setRatio(new BigDecimal(ratio));
        return it;
    }

    private static UserDTO user(String empId, String username, String chnName, String orgCode, String orgName) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setUsername(username);
        u.setDisplayName(chnName);
        u.setMainOrgCode(orgCode);
        u.setMainOrgName(orgName);
        return u;
    }

    @Test
    @DisplayName("custNo 空 → 返回空列表，不查任何表")
    void blankCustNo_returnsEmpty() {
        assertThat(service.getLastApprovedAllocPreview("  ")).isEmpty();
        assertThat(service.getLastApprovedAllocPreview(null)).isEmpty();
    }

    @Test
    @DisplayName("custNo 经 CustomerQueryApi 解析为内部主键后查询 apply")
    void resolvesCustNoToInternalId() {
        CustomerDTO cust = new CustomerDTO();
        cust.setId("INTERNAL_001");
        when(customerQueryApi.getCustomerByCustNo("C001")).thenReturn(Optional.of(cust));
        when(applyMapper.selectLastApprovedByCustAndDim("INTERNAL_001", "RULE")).thenReturn(null);
        when(applyMapper.selectLastApprovedByCustAndDim("INTERNAL_001", "ACCOUNT")).thenReturn(null);

        assertThat(service.getLastApprovedAllocPreview("C001")).isEmpty();

        verify(applyMapper).selectLastApprovedByCustAndDim("INTERNAL_001", "RULE");
        verify(applyMapper).selectLastApprovedByCustAndDim("INTERNAL_001", "ACCOUNT");
    }

    @Test
    @DisplayName("解析不到客户主键 → 回退用 custNo 匹配 apply.cust_id")
    void fallbackToCustNoWhenCustomerNotFound() {
        when(customerQueryApi.getCustomerByCustNo("RAW")).thenReturn(Optional.empty());
        when(applyMapper.selectLastApprovedByCustAndDim("RAW", "RULE")).thenReturn(null);
        when(applyMapper.selectLastApprovedByCustAndDim("RAW", "ACCOUNT")).thenReturn(null);

        assertThat(service.getLastApprovedAllocPreview("RAW")).isEmpty();
        verify(applyMapper).selectLastApprovedByCustAndDim("RAW", "RULE");
    }

    @Test
    @DisplayName("RULE + ACCOUNT 各一条审批通过申请 → 合并明细，RULE 账号空、ACCOUNT 账号取申请，员工/机构补全")
    void mergesRuleAndAccountWithEnrichment() {
        when(customerQueryApi.getCustomerByCustNo("C001")).thenReturn(Optional.empty());

        PerfAllocAdjustApply ruleApply = apply("APPLY_RULE", "RULE", null);
        PerfAllocAdjustApply acctApply = apply("APPLY_ACCT", "ACCOUNT", "62200000001");
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "RULE")).thenReturn(ruleApply);
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT")).thenReturn(acctApply);

        when(itemMapper.selectByApplyId("APPLY_RULE")).thenReturn(List.of(item("E10001", "60")));
        when(itemMapper.selectByApplyId("APPLY_ACCT")).thenReturn(List.of(item("E30001", "40")));

        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(
                user("E10001", "rm_zhang", "张客户经理", "BJ_CY", "北京分行朝阳支行"),
                user("E30001", "corp_zhao", "赵公司部审核", "BJ_HQ", "北京分行总部")));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("C001");

        assertThat(result).hasSize(2);

        AllocAdjustPreviewItemDTO rule = result.get(0);
        assertThat(rule.getAllocDim()).isEqualTo("RULE");
        assertThat(rule.getAccountNo()).isNull();
        assertThat(rule.getEmpId()).isEqualTo("E10001");
        assertThat(rule.getUsername()).isEqualTo("rm_zhang");
        assertThat(rule.getEmpChnName()).isEqualTo("张客户经理");
        assertThat(rule.getOrgCode()).isEqualTo("BJ_CY");
        assertThat(rule.getOrgName()).isEqualTo("北京分行朝阳支行");
        assertThat(rule.getRatio()).isEqualByComparingTo("60");

        AllocAdjustPreviewItemDTO acct = result.get(1);
        assertThat(acct.getAllocDim()).isEqualTo("ACCOUNT");
        assertThat(acct.getAccountNo()).isEqualTo("62200000001");
        assertThat(acct.getUsername()).isEqualTo("corp_zhao");
        assertThat(acct.getRatio()).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("员工工号解析不到 → username 回退展示工号、机构留空")
    void unresolvedEmployeeFallsBackToEmpId() {
        when(customerQueryApi.getCustomerByCustNo("C001")).thenReturn(Optional.empty());
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "RULE"))
                .thenReturn(apply("APPLY_RULE", "RULE", null));
        lenient().when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT")).thenReturn(null);
        when(itemMapper.selectByApplyId("APPLY_RULE")).thenReturn(List.of(item("GHOST", "100")));
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        when(userApi.getUsersByUsernames(anyList())).thenReturn(List.of());

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("C001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("GHOST");
        assertThat(result.get(0).getEmpChnName()).isNull();
        assertThat(result.get(0).getOrgCode()).isNull();
        assertThat(result.get(0).getOrgName()).isNull();
    }

    @Test
    @DisplayName("emp_id 存的是登录名(非工号) → 工号解析落空后按登录名兜底，中文名/机构正确补全")
    void resolvesByUsernameWhenEmpIdIsLoginName() {
        when(customerQueryApi.getCustomerByCustNo("C001")).thenReturn(Optional.empty());
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "RULE"))
                .thenReturn(apply("APPLY_RULE", "RULE", null));
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT")).thenReturn(null);
        // emp_id = "rm_zhang"（登录名，非工号 E10001）
        when(itemMapper.selectByApplyId("APPLY_RULE")).thenReturn(List.of(item("rm_zhang", "100")));
        // 按工号解析 rm_zhang 落空
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        // 按登录名兜底命中（内部已按 USER_ID 补全中文名 + 机构）
        when(userApi.getUsersByUsernames(anyList())).thenReturn(List.of(
                user("E10001", "rm_zhang", "张客户经理", "107", "对公一部")));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("C001");

        assertThat(result).hasSize(1);
        AllocAdjustPreviewItemDTO dto = result.get(0);
        assertThat(dto.getUsername()).isEqualTo("rm_zhang");
        assertThat(dto.getEmpChnName()).isEqualTo("张客户经理");
        assertThat(dto.getOrgCode()).isEqualTo("107");
        assertThat(dto.getOrgName()).isEqualTo("对公一部");
    }

    @Test
    @DisplayName("申请存在但无明细 → 跳过该维度")
    void applyWithoutItemsIsSkipped() {
        when(customerQueryApi.getCustomerByCustNo("C001")).thenReturn(Optional.empty());
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "RULE"))
                .thenReturn(apply("APPLY_RULE", "RULE", null));
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT")).thenReturn(null);
        when(itemMapper.selectByApplyId("APPLY_RULE")).thenReturn(List.of());

        assertThat(service.getLastApprovedAllocPreview("C001")).isEmpty();
    }
}
