package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AllocAdjustPreviewService} 单元测试：原业绩分配预览。
 *
 * <p>数据源已改为 {@code cust_alloc_relation} 当前生效分配（{@code is_original='2'}）；
 * 中文姓名/部门直接读快照列 fullname / dept_no / dept_name，username 经 UserApi 批量反查，
 * 未命中时回退展示工号(emp_id)。
 */
@ExtendWith(MockitoExtension.class)
class AllocAdjustPreviewServiceTest {

    @Mock
    private CustAllocRelationMapper allocRelationMapper;

    @Mock
    private UserApi userApi;

    @InjectMocks
    private AllocAdjustPreviewService service;

    /** 构造一条当前生效分配（is_original='2'）. */
    private static CustAllocRelation rel(String allocDim, String accountNo, String empId,
                                         String fullname, String deptNo, String deptName, String ratio) {
        CustAllocRelation r = new CustAllocRelation();
        r.setAllocDim(allocDim);
        r.setAccountNo(accountNo);
        r.setEmpId(empId);
        r.setFullname(fullname);
        r.setDeptNo(deptNo);
        r.setDeptName(deptName);
        r.setRatio(new BigDecimal(ratio));
        r.setIsOriginal("2");
        return r;
    }

    @Test
    @DisplayName("custId 空 → 返回空列表，不查表")
    void blankCustId_returnsEmpty() {
        assertThat(service.getLastApprovedAllocPreview("  ", null)).isEmpty();
        assertThat(service.getLastApprovedAllocPreview(null, null)).isEmpty();
        verify(allocRelationMapper, never()).selectCurrentOriginalByCust(any(), any());
    }

    @Test
    @DisplayName("无当前生效分配(is_original=2) → 返回空列表（前端切手工录入）")
    void noCurrentAlloc_returnsEmpty() {
        when(allocRelationMapper.selectCurrentOriginalByCust("C001", null)).thenReturn(List.of());
        assertThat(service.getLastApprovedAllocPreview("C001", null)).isEmpty();
        verify(userApi, never()).mapEmpIdsToUsername(any());
    }

    @Test
    @DisplayName("读 cust_alloc_relation is_original=2，直接映射 fullname/dept_no/dept_name，username 回退工号")
    void mapsSnapshotColumnsDirectly() {
        when(allocRelationMapper.selectCurrentOriginalByCust("bbc", null)).thenReturn(List.of(
                rel("RULE", null, "rm_li", "李客户经理", "107", "营业部", "10"),
                rel("RULE", null, "rm_zhang", "张客户经理", "107", "营业部", "90")));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("bbc", null);

        assertThat(result).hasSize(2);
        AllocAdjustPreviewItemDTO a = result.get(0);
        assertThat(a.getAllocDim()).isEqualTo("RULE");
        assertThat(a.getAccountNo()).isNull();
        assertThat(a.getEmpId()).isEqualTo("rm_li");
        assertThat(a.getUsername()).isEqualTo("rm_li");          // username 回退工号
        assertThat(a.getEmpChnName()).isEqualTo("李客户经理");    // ← fullname
        assertThat(a.getOrgCode()).isEqualTo("107");              // ← dept_no
        assertThat(a.getOrgName()).isEqualTo("营业部");           // ← dept_name
        assertThat(a.getRatio()).isEqualByComparingTo("10");
        assertThat(result.get(1).getEmpId()).isEqualTo("rm_zhang");
        assertThat(result.get(1).getRatio()).isEqualByComparingTo("90");
    }

    @Test
    @DisplayName("批量按去重 USER_ID 映射 username，未命中时回退 empId，中文姓名仍取关系快照")
    void mapsUsernamesInOneBatchAndFallsBackForUnknown() {
        when(allocRelationMapper.selectCurrentOriginalByCust("bbc", null)).thenReturn(List.of(
                rel("RULE", null, "USER_100", "关系快照姓名", "107", "营业部", "60"),
                rel("RULE", null, "USER_100", "关系快照姓名", "107", "营业部", "20"),
                rel("RULE", null, "USER_200", "另一快照姓名", "108", "支行", "20")));
        when(userApi.mapEmpIdsToUsername(List.of("USER_100", "USER_200")))
                .thenReturn(java.util.Map.of("USER_100", "employee100"));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("bbc", null);

        assertThat(result).extracting(AllocAdjustPreviewItemDTO::getUsername)
                .containsExactly("employee100", "employee100", "USER_200");
        assertThat(result).extracting(AllocAdjustPreviewItemDTO::getEmpChnName)
                .containsExactly("关系快照姓名", "关系快照姓名", "另一快照姓名");
        verify(userApi).mapEmpIdsToUsername(List.of("USER_100", "USER_200"));
    }

    @Test
    @DisplayName("无预览数据时不调用 UserApi")
    void emptyPreview_doesNotCallUserApi() {
        when(allocRelationMapper.selectCurrentOriginalByCust("C_EMPTY", "ACCOUNT"))
                .thenReturn(List.of());

        assertThat(service.getLastApprovedAllocPreview("C_EMPTY", "ACCOUNT")).isEmpty();

        verify(userApi, never()).mapEmpIdsToUsername(any());
    }

    @Test
    @DisplayName("ACCOUNT 维度账号反显 + allocDim 透传给 Mapper（维度过滤在 SQL 内）")
    void accountDim_passedThrough() {
        when(allocRelationMapper.selectCurrentOriginalByCust("C001", "ACCOUNT")).thenReturn(List.of(
                rel("ACCOUNT", "62200000001", "E10001", "张客户经理", "107", "营业部", "100")));

        List<AllocAdjustPreviewItemDTO> result = service.getLastApprovedAllocPreview("C001", "ACCOUNT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAllocDim()).isEqualTo("ACCOUNT");
        assertThat(result.get(0).getAccountNo()).isEqualTo("62200000001");
        // allocDim 原样透传，由 Mapper SQL 做 RULE/ACCOUNT 过滤
        verify(allocRelationMapper).selectCurrentOriginalByCust("C001", "ACCOUNT");
        verify(allocRelationMapper, never()).selectCurrentOriginalByCust(eq("C001"), eq("RULE"));
    }
}
