package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import com.bank.branch.platform.customer.entity.CustClaim;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustClaimDTOConverter 单元测试（TDD Red 阶段）
 * 测试类先于实现类存在，用于驱动实现。
 */
class CustClaimDTOConverterTest {

    // ==================== toDTO ====================

    @Test
    void toDTO_mapsAllScalarFields() {
        // given
        CustClaim entity = new CustClaim();
        entity.setId("claim-001");
        entity.setCustId("cust-001");
        entity.setOrgId("ORG_001");
        entity.setClaimedBy("EMP_001");
        entity.setMaintainerEmpId("EMP_002");
        entity.setClaimStatus("CLAIMED");
        LocalDateTime claimTime = LocalDateTime.of(2024, 3, 1, 10, 0, 0);
        entity.setClaimTime(claimTime);
        entity.setCancelTime(null);
        entity.setCancelReason(null);
        entity.setCreatedTime(claimTime);
        entity.setUpdatedTime(claimTime.plusHours(1));

        // when
        CustClaimDTO dto = CustClaimDTOConverter.toDTO(entity);

        // then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("claim-001");
        assertThat(dto.getCustId()).isEqualTo("cust-001");
        assertThat(dto.getOrgId()).isEqualTo("ORG_001");
        assertThat(dto.getMaintainerEmpId()).isEqualTo("EMP_002");
        assertThat(dto.getClaimStatus()).isEqualTo("CLAIMED");
        assertThat(dto.getClaimedAt()).isEqualTo(claimTime);
        assertThat(dto.getCancelledAt()).isNull();
        assertThat(dto.getCancelReason()).isNull();
        // 暂置 null 字段
        assertThat(dto.getCustName()).isNull();
        assertThat(dto.getOrgName()).isNull();
        assertThat(dto.getMaintainerEmpName()).isNull();
    }

    @Test
    void toDTO_mapsCancelledClaim() {
        // given: 已取消的认领记录
        CustClaim entity = new CustClaim();
        entity.setId("claim-002");
        entity.setCustId("cust-002");
        entity.setOrgId("ORG_002");
        entity.setMaintainerEmpId("EMP_003");
        entity.setClaimStatus("CANCELLED");
        LocalDateTime claimTime = LocalDateTime.of(2024, 3, 1, 10, 0, 0);
        LocalDateTime cancelTime = LocalDateTime.of(2024, 4, 1, 15, 30, 0);
        entity.setClaimTime(claimTime);
        entity.setCancelTime(cancelTime);
        entity.setCancelReason("客户已转移至其他机构");

        // when
        CustClaimDTO dto = CustClaimDTOConverter.toDTO(entity);

        // then
        assertThat(dto.getClaimStatus()).isEqualTo("CANCELLED");
        assertThat(dto.getClaimedAt()).isEqualTo(claimTime);
        assertThat(dto.getCancelledAt()).isEqualTo(cancelTime);
        assertThat(dto.getCancelReason()).isEqualTo("客户已转移至其他机构");
    }

    @Test
    void toDTO_returnsNullForNullEntity() {
        assertThat(CustClaimDTOConverter.toDTO(null)).isNull();
    }

    @Test
    void toDTOList_mapsEachAndFiltersNulls() {
        // given
        CustClaim e1 = new CustClaim();
        e1.setId("claim-001");
        e1.setClaimStatus("CLAIMED");
        e1.setClaimTime(LocalDateTime.now());

        CustClaim e2 = new CustClaim();
        e2.setId("claim-002");
        e2.setClaimStatus("CANCELLED");

        List<CustClaim> list = Arrays.asList(e1, null, e2);

        // when
        List<CustClaimDTO> dtos = CustClaimDTOConverter.toDTOList(list);

        // then
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getId()).isEqualTo("claim-001");
        assertThat(dtos.get(0).getClaimStatus()).isEqualTo("CLAIMED");
        assertThat(dtos.get(1).getId()).isEqualTo("claim-002");
        assertThat(dtos.get(1).getClaimStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void toDTOList_returnsEmptyForNullInput() {
        assertThat(CustClaimDTOConverter.toDTOList(null)).isEmpty();
    }

    @Test
    void toDTOList_returnsEmptyForEmptyList() {
        assertThat(CustClaimDTOConverter.toDTOList(Collections.emptyList())).isEmpty();
    }
}
