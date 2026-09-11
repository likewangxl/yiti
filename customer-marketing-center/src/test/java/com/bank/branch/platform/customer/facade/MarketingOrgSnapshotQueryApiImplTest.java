package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.dto.MarketingOrgSnapshotDTO;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingOrgSnapshotRow;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerClaimMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 机构营销快照公共契约的输入归一化、零值和当前状态口径测试。 */
@ExtendWith(MockitoExtension.class)
class MarketingOrgSnapshotQueryApiImplTest {

    @Mock
    private MarketingCustomerClaimMapper claimMapper;

    @InjectMocks
    private MarketingOrgSnapshotQueryApiImpl api;

    @Test
    void batchQueryOrgSnapshots_normalizesCodesAndReturnsOneRowPerRequestedOrg() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 10);
        LocalDateTime sourceUpdatedAt = LocalDateTime.of(2026, 9, 10, 15, 30);
        when(claimMapper.selectOrgMarketingSnapshots(List.of("ORG-A", "ORG-B")))
                .thenReturn(List.of(
                        row("ORG-A", 3L, 2L, sourceUpdatedAt),
                        row("ORG-B", 0L, 0L, null)));

        List<MarketingOrgSnapshotDTO> result = api.batchQueryOrgSnapshots(
                java.util.Arrays.asList(" ORG-A ", "ORG-A", null, "", "ORG-B"), asOfDate);

        assertThat(result).extracting(MarketingOrgSnapshotDTO::getOrgCode)
                .containsExactly("ORG-A", "ORG-B");
        assertThat(result.get(0).getValidCustomerCount()).isEqualTo(3L);
        assertThat(result.get(0).getPendingFollowUpTaskCount()).isEqualTo(2L);
        assertThat(result.get(1).getValidCustomerCount()).isZero();
        assertThat(result.get(1).getPendingFollowUpTaskCount()).isZero();
        assertThat(result.get(0).getAsOfDate()).isEqualTo(asOfDate);
        assertThat(result.get(0).getSourceAsOfDate()).isEqualTo(LocalDate.now());
        assertThat(result.get(0).getSourceMode()).isEqualTo("CURRENT_STATE");
        assertThat(result.get(0).getSourceUpdatedAt()).isEqualTo(sourceUpdatedAt);
        assertThat(result.get(1).getSourceUpdatedAt()).isNull();
        verify(claimMapper).selectOrgMarketingSnapshots(List.of("ORG-A", "ORG-B"));
    }

    @Test
    void batchQueryOrgSnapshots_returnsEmptyWithoutQueryForNullOrBlankInput() {
        assertThat(api.batchQueryOrgSnapshots(null, LocalDate.now())).isEmpty();
        assertThat(api.batchQueryOrgSnapshots(java.util.Arrays.asList(" ", null), LocalDate.now())).isEmpty();

        verify(claimMapper, never()).selectOrgMarketingSnapshots(List.of());
    }

    @Test
    void batchQueryOrgSnapshots_rejectsMoreThan500DistinctCodes() {
        List<String> codes = java.util.stream.IntStream.range(0, 501)
                .mapToObj(i -> "ORG-" + i)
                .toList();

        assertThatThrownBy(() -> api.batchQueryOrgSnapshots(codes, LocalDate.now()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "COMMON-40000");
        verify(claimMapper, never()).selectOrgMarketingSnapshots(codes);
    }

    @Test
    void batchQueryOrgSnapshots_rejectsFutureDate() {
        assertThatThrownBy(() -> api.batchQueryOrgSnapshots(List.of("ORG-A"), LocalDate.now().plusDays(1)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "COMMON-40000");

        verify(claimMapper, never()).selectOrgMarketingSnapshots(List.of("ORG-A"));
    }

    @Test
    void batchQueryOrgSnapshots_failsClosedWhenMapperReturnsNull() {
        when(claimMapper.selectOrgMarketingSnapshots(List.of("ORG-A", "ORG-B"))).thenReturn(null);

        assertThatThrownBy(() -> api.batchQueryOrgSnapshots(List.of("ORG-A", "ORG-B"), LocalDate.now()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void batchQueryOrgSnapshots_failsClosedWhenMapperRowsDoNotCoverRequestedCodes() {
        when(claimMapper.selectOrgMarketingSnapshots(List.of("ORG-A", "ORG-B")))
                .thenReturn(List.of(row("ORG-A", 1L, 0L, null)));

        assertThatThrownBy(() -> api.batchQueryOrgSnapshots(List.of("ORG-A", "ORG-B"), LocalDate.now()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void batchQueryOrgSnapshots_failsClosedWhenMapperRowsDuplicateAnOrg() {
        when(claimMapper.selectOrgMarketingSnapshots(List.of("ORG-A")))
                .thenReturn(List.of(row("ORG-A", 1L, 0L, null), row("ORG-A", 1L, 0L, null)));

        assertThatThrownBy(() -> api.batchQueryOrgSnapshots(List.of("ORG-A"), LocalDate.now()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void batchQueryOrgSnapshots_failsClosedWhenMapperCountIsNullOrNegative() {
        MarketingOrgSnapshotRow nullCount = row("ORG-A", 0L, 0L, null);
        nullCount.setValidCustomerCount(null);
        when(claimMapper.selectOrgMarketingSnapshots(List.of("ORG-A")))
                .thenReturn(List.of(nullCount));

        assertThatThrownBy(() -> api.batchQueryOrgSnapshots(List.of("ORG-A"), LocalDate.now()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.INTERNAL_ERROR.getCode());

        MarketingOrgSnapshotRow negativeCount = row("ORG-A", -1L, 0L, null);
        when(claimMapper.selectOrgMarketingSnapshots(List.of("ORG-A")))
                .thenReturn(List.of(negativeCount));

        assertThatThrownBy(() -> api.batchQueryOrgSnapshots(List.of("ORG-A"), LocalDate.now()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void batchQueryOrgSnapshots_failsClosedWhenMapperThrows() {
        when(claimMapper.selectOrgMarketingSnapshots(List.of("ORG-A")))
                .thenThrow(new IllegalStateException("database details must not escape"));

        assertThatThrownBy(() -> api.batchQueryOrgSnapshots(List.of("ORG-A"), LocalDate.now()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.INTERNAL_ERROR.getCode());
    }

    private MarketingOrgSnapshotRow row(String orgCode, long customers, long tasks,
                                        LocalDateTime sourceUpdatedAt) {
        MarketingOrgSnapshotRow row = new MarketingOrgSnapshotRow();
        row.setOrgCode(orgCode);
        row.setValidCustomerCount(customers);
        row.setPendingFollowUpTaskCount(tasks);
        row.setSourceUpdatedAt(sourceUpdatedAt);
        return row;
    }
}
