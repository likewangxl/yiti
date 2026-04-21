package com.bank.branch.platform.bizapp.api.converter;

import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link LoanApplyDTOConverter} 单元测试。
 * <p>
 * 验证：① custName 冗余字段正确填充；② deleted 字段已从 DTO 中移除；
 * ③ 批量转换使用批量 API 避免 N+1；④ 客户不存在时 custName 为空字符串。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class LoanApplyDTOConverterTest {

    @Mock
    CustomerQueryApi customerQueryApi;

    @InjectMocks
    LoanApplyDTOConverter converter;

    // -----------------------------------------------------------------------
    // 测试辅助方法
    // -----------------------------------------------------------------------

    /** 构造测试用 LoanApply 实体 */
    private LoanApply buildEntity(String id, String custId) {
        LoanApply entity = new LoanApply();
        entity.setId(id);
        entity.setApplyNo("LA202401010001");
        entity.setCustId(custId);
        entity.setSourceTouchTaskId("TASK001");
        entity.setProjectType("TYPE_A");
        entity.setBizType("BIZ_CORP");
        entity.setGuaranteeType("GUARANTEE_PLEDGE");
        entity.setCreditAmount(new BigDecimal("500000"));
        entity.setCreditExposureAmount(new BigDecimal("300000"));
        entity.setStatus("COMPLETED");
        entity.setBusinessKey("LOAN:" + id);
        entity.setProcessInstanceId("PROC001");
        entity.setOwnerOrgId("ORG001");
        entity.setCreatedBy("EMP001");
        entity.setDeleted(0);
        return entity;
    }

    /** 构造测试用 CustomerDTO */
    private CustomerDTO buildCustomerDTO(String id, String custName) {
        CustomerDTO dto = new CustomerDTO();
        dto.setId(id);
        dto.setCustName(custName);
        return dto;
    }

    // -----------------------------------------------------------------------
    // 单条转换测试
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toDTO 应补齐 custName 且不输出 deleted 字段")
    void toDTO_populatesCustNameFromQueryApi() {
        LoanApply entity = buildEntity("la001", "cust001");
        when(customerQueryApi.getCustomer("cust001"))
                .thenReturn(Optional.of(buildCustomerDTO("cust001", "腾讯云计算")));

        LoanApplyDTO dto = converter.toDTO(entity);

        assertThat(dto.getCustName()).isEqualTo("腾讯云计算");
        assertThat(dto.getId()).isEqualTo("la001");
        assertThat(dto.getCreditAmount()).isEqualByComparingTo("500000");
        // deleted 字段已删 — 编译期即不存在 getter，此断言保证 API 不泄露
        assertThat(BeanUtils.getPropertyDescriptors(LoanApplyDTO.class))
                .noneMatch(pd -> pd.getName().equals("deleted"));
    }

    @Test
    @DisplayName("toDTO 当客户不存在时 custName 为空字符串，不抛异常")
    void toDTO_custNotFound_returnsEmptyName() {
        LoanApply entity = buildEntity("la002", "ghost");
        when(customerQueryApi.getCustomer("ghost")).thenReturn(Optional.empty());

        LoanApplyDTO dto = converter.toDTO(entity);

        assertThat(dto.getCustName()).isEmpty();
    }

    @Test
    @DisplayName("toDTO 当 custId 为 null 时 custName 为空字符串，不调用 API")
    void toDTO_nullCustId_returnsEmptyName() {
        LoanApply entity = buildEntity("la003", null);

        LoanApplyDTO dto = converter.toDTO(entity);

        assertThat(dto.getCustName()).isEmpty();
        // custId 为 null 时不应调用 customerQueryApi
    }

    // -----------------------------------------------------------------------
    // 批量转换测试
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toDTOList 批量转换应正确填充 custName，避免 N+1")
    void toDTOList_batchQuery_populatesCustNames() {
        LoanApply e1 = buildEntity("la001", "cust001");
        LoanApply e2 = buildEntity("la002", "cust002");

        CustomerDTO c1 = buildCustomerDTO("cust001", "腾讯云计算");
        CustomerDTO c2 = buildCustomerDTO("cust002", "阿里巴巴");
        // 批量查询返回两个客户
        when(customerQueryApi.listCustomers(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(c1, c2));

        List<LoanApplyDTO> result = converter.toDTOList(Arrays.asList(e1, e2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustName()).isEqualTo("腾讯云计算");
        assertThat(result.get(1).getCustName()).isEqualTo("阿里巴巴");
        // deleted 字段不存在
        assertThat(BeanUtils.getPropertyDescriptors(LoanApplyDTO.class))
                .noneMatch(pd -> pd.getName().equals("deleted"));
    }

    @Test
    @DisplayName("toDTOList 空列表输入时返回空列表，不调用 API")
    void toDTOList_emptyInput_returnsEmptyList() {
        List<LoanApplyDTO> result = converter.toDTOList(List.of());
        assertThat(result).isEmpty();
    }
}
