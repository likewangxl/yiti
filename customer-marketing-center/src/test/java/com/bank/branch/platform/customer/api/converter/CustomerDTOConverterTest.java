package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustomerDTOConverter 单元测试（TDD Red 阶段）
 * 测试类先于实现类存在，用于驱动实现。
 * <p>
 * 注意：CustMaster 实体无 tagIds 字段（标签关系存储在 cust_tag_rel 表），
 * 因此 CustomerDTO.tagIds 在转换器层暂置 null，待上层 Service 补充填充。
 * </p>
 */
class CustomerDTOConverterTest {

    // ==================== toDTO ====================

    @Test
    void toDTO_mapsAllScalarFields() {
        // given
        CustMaster entity = new CustMaster();
        entity.setId("cust-001");
        entity.setCustNo("C20240001");
        entity.setCustName("测试企业有限公司");
        entity.setUnifiedCreditCode("91110000XXXXXXXX01");
        entity.setIndustry("MANUFACTURING");
        entity.setGroupType("LARGE");
        entity.setCustomerType("CORPORATE");
        entity.setIsKeystone(1);
        entity.setEnterpriseType("SOE");
        entity.setIsAccountOpened(0);
        entity.setCustomerDesc("重点跟进客户");
        entity.setCreditAmount(new BigDecimal("5000000.00"));
        entity.setCreditExposureAmount(new BigDecimal("3000000.00"));
        entity.setOwnerOrgId("ORG_001");
        entity.setLeadId("lead-001");
        entity.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now.plusDays(1));

        // when
        CustomerDTO dto = CustomerDTOConverter.toDTO(entity);

        // then: 标量字段完整映射
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("cust-001");
        assertThat(dto.getCustNo()).isEqualTo("C20240001");
        assertThat(dto.getCustName()).isEqualTo("测试企业有限公司");
        assertThat(dto.getUnifiedCreditCode()).isEqualTo("91110000XXXXXXXX01");
        assertThat(dto.getIndustry()).isEqualTo("MANUFACTURING");
        assertThat(dto.getGroupType()).isEqualTo("LARGE");
        assertThat(dto.getCustomerType()).isEqualTo("CORPORATE");
        assertThat(dto.getIsKeystone()).isTrue();
        assertThat(dto.getEnterpriseType()).isEqualTo("SOE");
        assertThat(dto.getIsAccountOpened()).isFalse();
        assertThat(dto.getCustomerDesc()).isEqualTo("重点跟进客户");
        assertThat(dto.getCreditAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        assertThat(dto.getCreditExposureAmount()).isEqualByComparingTo(new BigDecimal("3000000.00"));
        assertThat(dto.getOwnerOrgId()).isEqualTo("ORG_001");
        assertThat(dto.getLeadId()).isEqualTo("lead-001");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        // 时间字段：Entity createdTime/updatedTime → DTO createdAt/updatedAt
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getUpdatedAt()).isEqualTo(now.plusDays(1));
    }

    @Test
    void toDTO_enrichableFieldsAreNull() {
        // given: 需二次查询才能填充的字段，转换器层暂置 null
        CustMaster entity = new CustMaster();
        entity.setId("cust-002");

        // when
        CustomerDTO dto = CustomerDTOConverter.toDTO(entity);

        // then
        assertThat(dto.getOwnerOrgName()).isNull();   // 需查 OrgApi
        assertThat(dto.getIndustryName()).isNull();   // 需查 DictApi
        assertThat(dto.getTagIds()).isNull();          // 需查 cust_tag_rel 表
    }

    @Test
    void toDTO_isKeystoneIntegerMappedToBoolean() {
        // given
        CustMaster entity1 = new CustMaster();
        entity1.setId("cust-003");
        entity1.setIsKeystone(1);
        entity1.setIsAccountOpened(0);

        CustMaster entity2 = new CustMaster();
        entity2.setId("cust-004");
        entity2.setIsKeystone(0);
        entity2.setIsAccountOpened(1);

        CustMaster entityNull = new CustMaster();
        entityNull.setId("cust-005");
        entityNull.setIsKeystone(null);
        entityNull.setIsAccountOpened(null);

        // when + then
        CustomerDTO dto1 = CustomerDTOConverter.toDTO(entity1);
        assertThat(dto1.getIsKeystone()).isTrue();
        assertThat(dto1.getIsAccountOpened()).isFalse();

        CustomerDTO dto2 = CustomerDTOConverter.toDTO(entity2);
        assertThat(dto2.getIsKeystone()).isFalse();
        assertThat(dto2.getIsAccountOpened()).isTrue();

        CustomerDTO dtoNull = CustomerDTOConverter.toDTO(entityNull);
        assertThat(dtoNull.getIsKeystone()).isNull();
        assertThat(dtoNull.getIsAccountOpened()).isNull();
    }

    @Test
    void toDTO_returnsNullForNullEntity() {
        assertThat(CustomerDTOConverter.toDTO(null)).isNull();
    }

    @Test
    void toDTOList_mapsEachAndFiltersNulls() {
        // given
        CustMaster e1 = new CustMaster();
        e1.setId("cust-001");
        e1.setIsKeystone(1);

        CustMaster e2 = new CustMaster();
        e2.setId("cust-002");
        e2.setIsKeystone(0);

        List<CustMaster> list = Arrays.asList(e1, null, e2);

        // when
        List<CustomerDTO> dtos = CustomerDTOConverter.toDTOList(list);

        // then: null 元素被过滤，其余正常映射
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getId()).isEqualTo("cust-001");
        assertThat(dtos.get(0).getIsKeystone()).isTrue();
        assertThat(dtos.get(1).getId()).isEqualTo("cust-002");
        assertThat(dtos.get(1).getIsKeystone()).isFalse();
    }

    @Test
    void toDTOList_returnsEmptyForNullInput() {
        assertThat(CustomerDTOConverter.toDTOList(null)).isEmpty();
    }

    @Test
    void toDTOList_returnsEmptyForEmptyList() {
        assertThat(CustomerDTOConverter.toDTOList(Collections.emptyList())).isEmpty();
    }
}
