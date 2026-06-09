package com.bank.branch.platform.report.service;

import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO;
import com.bank.branch.platform.report.controller.dto.AllocPreviewRespDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link AllocPreviewService} 单元测试：委托 perf AllocApi 装配原业绩分配预览.
 */
@ExtendWith(MockitoExtension.class)
class AllocPreviewServiceTest {

    @Mock
    private AllocApi allocApi;

    @InjectMocks
    private AllocPreviewService service;

    private static AllocAdjustPreviewItemDTO src(String dim, String acct, String username,
                                                 String chn, String orgCode, String orgName, String ratio) {
        AllocAdjustPreviewItemDTO d = new AllocAdjustPreviewItemDTO();
        d.setAllocDim(dim);
        d.setAccountNo(acct);
        d.setUsername(username);
        d.setEmpChnName(chn);
        d.setOrgCode(orgCode);
        d.setOrgName(orgName);
        d.setRatio(ratio != null ? new BigDecimal(ratio) : null);
        return d;
    }

    @Test
    @DisplayName("有数据 → 字段映射正确，hasData=true，ratio 以纯字符串输出")
    void mapsItems() {
        when(allocApi.getLastApprovedAllocPreview("C001", null)).thenReturn(List.of(
                src("RULE", null, "rm_zhang", "张客户经理", "BJ_CY", "北京分行朝阳支行", "60"),
                src("ACCOUNT", "62200000001", "corp_zhao", "赵公司部审核", "BJ_HQ", "北京分行总部", "40")));

        AllocPreviewRespDTO resp = service.preview("C001", null);

        assertThat(resp.isHasData()).isTrue();
        assertThat(resp.getAllocList()).hasSize(2);

        AllocPreviewRespDTO.AllocItem rule = resp.getAllocList().get(0);
        assertThat(rule.getAllocDim()).isEqualTo("RULE");
        assertThat(rule.getAcctNo()).isNull();
        assertThat(rule.getUsername()).isEqualTo("rm_zhang");
        assertThat(rule.getEmpChnName()).isEqualTo("张客户经理");
        assertThat(rule.getOrgCode()).isEqualTo("BJ_CY");
        assertThat(rule.getOrgName()).isEqualTo("北京分行朝阳支行");
        assertThat(rule.getRatio()).isEqualTo("60");

        AllocPreviewRespDTO.AllocItem acct = resp.getAllocList().get(1);
        assertThat(acct.getAcctNo()).isEqualTo("62200000001");
        assertThat(acct.getRatio()).isEqualTo("40");
    }

    @Test
    @DisplayName("无数据 → 空列表 + hasData=false")
    void emptyWhenNoData() {
        when(allocApi.getLastApprovedAllocPreview("C404", null)).thenReturn(List.of());

        AllocPreviewRespDTO resp = service.preview("C404", null);

        assertThat(resp.isHasData()).isFalse();
        assertThat(resp.getAllocList()).isEmpty();
    }

    @Test
    @DisplayName("AllocApi 返回 null → 安全降级为空列表")
    void nullSafe() {
        when(allocApi.getLastApprovedAllocPreview("CNULL", null)).thenReturn(null);

        AllocPreviewRespDTO resp = service.preview("CNULL", null);

        assertThat(resp.isHasData()).isFalse();
        assertThat(resp.getAllocList()).isEmpty();
    }
}
