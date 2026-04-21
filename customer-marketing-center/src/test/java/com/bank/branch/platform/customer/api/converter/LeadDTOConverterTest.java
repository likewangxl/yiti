package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.LeadDTO;
import com.bank.branch.platform.customer.entity.CustLead;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LeadDTOConverter 单元测试（TDD Red 阶段）
 * 测试类先于实现类存在，用于驱动实现。
 */
class LeadDTOConverterTest {

    // ==================== toDTO ====================

    @Test
    void toDTO_mapsAllScalarFields() {
        // given
        CustLead entity = new CustLead();
        entity.setId("lead-001");
        entity.setLeadNo("L20240001");
        entity.setLeadOp("CREATE");
        entity.setSourceCustId(null);
        entity.setPrevLeadId(null);
        entity.setVersionNo(1);
        entity.setIsLatest(1);
        entity.setCustName("测试企业有限公司");
        entity.setUnifiedCreditCode("91110000XXXXXXXX01");
        entity.setIndustry("MANUFACTURING");
        entity.setGroupType("LARGE");
        entity.setCustomerType("CORPORATE");
        entity.setIsKeystone(1);
        entity.setEnterpriseType("SOE");
        entity.setIsAccountOpened(0);
        entity.setCustomerDesc("线索描述");
        entity.setCreditAmount(new BigDecimal("8000000.00"));
        entity.setCreditExposureAmount(new BigDecimal("5000000.00"));
        entity.setLeadStatus("APPROVED");
        entity.setOwnerOrgId("ORG_001");
        entity.setCreatedBy("EMP_001");
        entity.setBusinessKey("LEAD:lead-001");
        entity.setImportBatchId("batch-001");
        LocalDateTime now = LocalDateTime.of(2024, 2, 20, 9, 0, 0);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now.plusHours(2));

        // when
        LeadDTO dto = LeadDTOConverter.toDTO(entity);

        // then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("lead-001");
        assertThat(dto.getCustName()).isEqualTo("测试企业有限公司");
        assertThat(dto.getUnifiedCreditCode()).isEqualTo("91110000XXXXXXXX01");
        assertThat(dto.getIndustry()).isEqualTo("MANUFACTURING");
        assertThat(dto.getGroupType()).isEqualTo("LARGE");
        assertThat(dto.getCustomerType()).isEqualTo("CORPORATE");
        assertThat(dto.getIsKeystone()).isTrue();
        assertThat(dto.getEnterpriseType()).isEqualTo("SOE");
        assertThat(dto.getIsAccountOpened()).isFalse();
        assertThat(dto.getCustomerDesc()).isEqualTo("线索描述");
        assertThat(dto.getCreditAmount()).isEqualByComparingTo(new BigDecimal("8000000.00"));
        assertThat(dto.getCreditExposureAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        assertThat(dto.getLeadOp()).isEqualTo("CREATE");
        assertThat(dto.getVersionNo()).isEqualTo(1);
        assertThat(dto.getIsLatest()).isTrue();
        assertThat(dto.getLeadStatus()).isEqualTo("APPROVED");
        assertThat(dto.getOwnerOrgId()).isEqualTo("ORG_001");
        assertThat(dto.getCreatedBy()).isEqualTo("EMP_001");
        assertThat(dto.getBusinessKey()).isEqualTo("LEAD:lead-001");
        assertThat(dto.getImportBatchId()).isEqualTo("batch-001");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getUpdatedAt()).isEqualTo(now.plusHours(2));
    }

    @Test
    void toDTO_isLatestZeroMappedToFalse() {
        // given
        CustLead entity = new CustLead();
        entity.setId("lead-002");
        entity.setIsLatest(0);
        entity.setIsKeystone(0);
        entity.setIsAccountOpened(0);

        // when
        LeadDTO dto = LeadDTOConverter.toDTO(entity);

        // then
        assertThat(dto.getIsLatest()).isFalse();
        assertThat(dto.getIsKeystone()).isFalse();
        assertThat(dto.getIsAccountOpened()).isFalse();
    }

    @Test
    void toDTO_nullIntegersMappedToNull() {
        // given: Integer 字段为 null
        CustLead entity = new CustLead();
        entity.setId("lead-003");
        entity.setIsLatest(null);
        entity.setIsKeystone(null);
        entity.setIsAccountOpened(null);

        // when
        LeadDTO dto = LeadDTOConverter.toDTO(entity);

        // then: null Integer → null Boolean
        assertThat(dto.getIsLatest()).isNull();
        assertThat(dto.getIsKeystone()).isNull();
        assertThat(dto.getIsAccountOpened()).isNull();
    }

    @Test
    void toDTO_returnsNullForNullEntity() {
        assertThat(LeadDTOConverter.toDTO(null)).isNull();
    }

    @Test
    void toDTOList_mapsEachAndFiltersNulls() {
        // given
        CustLead e1 = new CustLead();
        e1.setId("lead-001");
        e1.setIsLatest(1);

        CustLead e2 = new CustLead();
        e2.setId("lead-002");
        e2.setIsLatest(0);

        List<CustLead> list = Arrays.asList(e1, null, e2);

        // when
        List<LeadDTO> dtos = LeadDTOConverter.toDTOList(list);

        // then
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getId()).isEqualTo("lead-001");
        assertThat(dtos.get(0).getIsLatest()).isTrue();
        assertThat(dtos.get(1).getId()).isEqualTo("lead-002");
        assertThat(dtos.get(1).getIsLatest()).isFalse();
    }

    @Test
    void toDTOList_returnsEmptyForNullInput() {
        assertThat(LeadDTOConverter.toDTOList(null)).isEmpty();
    }

    @Test
    void toDTOList_returnsEmptyForEmptyList() {
        assertThat(LeadDTOConverter.toDTOList(Collections.emptyList())).isEmpty();
    }
}
