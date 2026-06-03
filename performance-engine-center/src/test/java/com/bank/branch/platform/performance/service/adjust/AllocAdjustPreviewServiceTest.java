package com.bank.branch.platform.performance.service.adjust;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AllocAdjustPreviewService} 单元测试：原业绩分配预览。
 *
 * <p>预览按客户编号(cust_no)直接匹配 apply（与提交侧 apply.cust_no 落地一致）。
 * 手工录入客户 apply.cust_id 为 null、仅 cust_no 有值，故必须按 cust_no 匹配，否则查不到。
 *
 * <p>员工 username/中文名/部门已在提交时快照存入 PERF_ALLOC_ADJUST_ITEM，预览直接读这些字段，
 * 不再关联 PT_USER/机构表。
 */
@ExtendWith(MockitoExtension.class)
class AllocAdjustPreviewServiceTest {

    @Mock
    private PerfAllocAdjustApplyMapper applyMapper;
    @Mock
    private PerfAllocAdjustItemMapper itemMapper;

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

    /** 带快照字段的明细项. */
    private static PerfAllocAdjustItem item(String empId, String username, String chnName,
                                            String orgCode, String orgName, String ratio) {
        PerfAllocAdjustItem it = new PerfAllocAdjustItem();
        it.setEmpId(empId);
        it.setUsername(username);
        it.setEmpChnName(chnName);
        it.setOrgCode(orgCode);
        it.setOrgName(orgName);
        it.setRatio(new BigDecimal(ratio));
        return it;
    }

    @Test
    @DisplayName("custNo 空 → 返回空列表，不查任何表")
    void blankCustNo_returnsEmpty() {
        assertThat(service.getLastApprovedAllocPreview("  ", null)).isEmpty();
        assertThat(service.getLastApprovedAllocPreview(null, null)).isEmpty();
    }

    @Test
    @DisplayName("直接按用户输入的客户编号(cust_no)查询 apply，不再二次解析内部主键")
    void queriesApplyByCustNoDirectly() {
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "RULE")).thenReturn(null);
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT")).thenReturn(null);

        assertThat(service.getLastApprovedAllocPreview("C001", null)).isEmpty();

        verify(applyMapper).selectLastApprovedByCustAndDim("C001", "RULE");
        verify(applyMapper).selectLastApprovedByCustAndDim("C001", "ACCOUNT");
    }

    @Test
    @DisplayName("手工录入客户(cust_id 为 null) → 按 cust_no='bbc' 仍能命中审批通过申请")
    void manualCustomer_matchedByCustNo() {
        when(applyMapper.selectLastApprovedByCustAndDim("bbc", "RULE"))
                .thenReturn(apply("APPLY_BBC", "RULE", null));
        lenient().when(applyMapper.selectLastApprovedByCustAndDim("bbc", "ACCOUNT")).thenReturn(null);
        when(itemMapper.selectByApplyIdAndKind("APPLY_BBC", "NEW")).thenReturn(List.of(
                item("rm_li", "rm_li", "李客户经理", "107", "营业部", "10"),
                item("rm_zhang", "rm_zhang", "张客户经理", "107", "营业部", "90")));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("bbc", null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AllocAdjustPreviewItemDTO::getEmpId)
                .containsExactly("rm_li", "rm_zhang");
        verify(applyMapper).selectLastApprovedByCustAndDim("bbc", "RULE");
    }

    @Test
    @DisplayName("RULE + ACCOUNT 各一条审批通过申请 → 合并明细，直接读快照的员工/部门字段")
    void mergesRuleAndAccountReadsSnapshot() {
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "RULE"))
                .thenReturn(apply("APPLY_RULE", "RULE", null));
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT"))
                .thenReturn(apply("APPLY_ACCT", "ACCOUNT", "62200000001"));

        when(itemMapper.selectByApplyIdAndKind("APPLY_RULE", "NEW")).thenReturn(List.of(
                item("E10001", "rm_zhang", "张客户经理", "BJ_CY", "北京分行朝阳支行", "60")));
        when(itemMapper.selectByApplyIdAndKind("APPLY_ACCT", "NEW")).thenReturn(List.of(
                item("E30001", "corp_zhao", "赵公司部审核", "BJ_HQ", "北京分行总部", "40")));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("C001", null);

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
        assertThat(acct.getEmpChnName()).isEqualTo("赵公司部审核");
        assertThat(acct.getRatio()).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("快照 username 为空（历史旧数据）→ 回退展示工号，部门留空")
    void blankSnapshotFallsBackToEmpId() {
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "RULE"))
                .thenReturn(apply("APPLY_RULE", "RULE", null));
        lenient().when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT")).thenReturn(null);
        when(itemMapper.selectByApplyIdAndKind("APPLY_RULE", "NEW")).thenReturn(List.of(
                item("GHOST", null, null, null, null, "100")));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("C001", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("GHOST");
        assertThat(result.get(0).getEmpChnName()).isNull();
        assertThat(result.get(0).getOrgCode()).isNull();
        assertThat(result.get(0).getOrgName()).isNull();
    }

    @Test
    @DisplayName("申请存在但无明细 → 跳过该维度")
    void applyWithoutItemsIsSkipped() {
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "RULE"))
                .thenReturn(apply("APPLY_RULE", "RULE", null));
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT")).thenReturn(null);
        when(itemMapper.selectByApplyIdAndKind("APPLY_RULE", "NEW")).thenReturn(List.of());

        assertThat(service.getLastApprovedAllocPreview("C001", null)).isEmpty();
    }

    @Test
    @DisplayName("allocDim=ACCOUNT → 只查 ACCOUNT 维度，不查 RULE")
    void accountDim_onlyQueriesAccount() {
        when(applyMapper.selectLastApprovedByCustAndDim("C001", "ACCOUNT"))
                .thenReturn(apply("APPLY_ACCT", "ACCOUNT", "62200000001"));
        when(itemMapper.selectByApplyIdAndKind("APPLY_ACCT", "NEW")).thenReturn(List.of(
                item("E10001", "rm_zhang", "张客户经理", "107", "营业部", "100")));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("C001", "ACCOUNT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAllocDim()).isEqualTo("ACCOUNT");
        assertThat(result.get(0).getAccountNo()).isEqualTo("62200000001");
        assertThat(result.get(0).getOrgName()).isEqualTo("营业部");
        // 关键：不应查询 RULE 维度
        org.mockito.Mockito.verify(applyMapper, org.mockito.Mockito.never())
                .selectLastApprovedByCustAndDim(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("RULE"));
    }
}
