package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SupportProductSplitService 单元测试（TDD）。
 * 共 5 个测试用例。
 */
@ExtendWith(MockitoExtension.class)
class SupportProductSplitServiceTest {

    @Mock
    private SupportRequestMapper supportMapper;

    @Mock
    private BizNoGenerator bizNoGenerator;

    @Mock
    private ProductApi productApi;

    @InjectMocks
    private SupportProductSplitService splitService;

    @Test
    void splitByProducts_singleProduct_shouldCreateOneRecord() {
        // given
        when(productApi.getProduct("P001")).thenReturn(Optional.of(buildProduct("P001", true)));
        when(productApi.getProductResponsibleEmpIds("P001")).thenReturn(List.of("E10001"));
        when(supportMapper.countActiveByCustomerAndProduct("CUST001", "P001")).thenReturn(0L);
        when(bizNoGenerator.generateSupportNo()).thenReturn("SR20260414000001");
        when(supportMapper.insert(any(SupportRequest.class))).thenReturn(1);

        // when
        List<SupportRequest> result = splitService.splitByProducts(
                List.of("P001"), "CUST001", null, "E10001", "ORG001");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo("P001");
        verify(supportMapper, times(1)).insert(any(SupportRequest.class));
    }

    @Test
    void splitByProducts_multipleProducts_shouldShareSubmitGroupId() {
        // given
        when(productApi.getProduct("P001")).thenReturn(Optional.of(buildProduct("P001", true)));
        when(productApi.getProduct("P002")).thenReturn(Optional.of(buildProduct("P002", true)));
        when(productApi.getProductResponsibleEmpIds("P001")).thenReturn(List.of("E10001"));
        when(productApi.getProductResponsibleEmpIds("P002")).thenReturn(List.of("E10002"));
        when(supportMapper.countActiveByCustomerAndProduct(eq("CUST001"), any())).thenReturn(0L);
        when(bizNoGenerator.generateSupportNo())
                .thenReturn("SR20260414000001")
                .thenReturn("SR20260414000002");
        when(supportMapper.insert(any(SupportRequest.class))).thenReturn(1);

        // when
        List<SupportRequest> result = splitService.splitByProducts(
                List.of("P001", "P002"), "CUST001", null, "E10001", "ORG001");

        // then
        assertThat(result).hasSize(2);
        String groupId = result.get(0).getSubmitGroupId();
        assertThat(groupId).isNotNull();
        // 两条记录共享同一个 submitGroupId
        assertThat(result.get(1).getSubmitGroupId()).isEqualTo(groupId);
    }

    @Test
    void splitByProducts_productNotAvailable_shouldThrowBIZ40901() {
        // given: product exists but supportForSupportRequest=false
        when(productApi.getProduct("P001")).thenReturn(Optional.of(buildProduct("P001", false)));

        // when / then
        assertThatThrownBy(() -> splitService.splitByProducts(
                List.of("P001"), "CUST001", null, "E10001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40901");
    }

    @Test
    void splitByProducts_duplicateActiveRequest_shouldThrowBIZ40906() {
        // given: product OK but 同客户同产品已有 2 条活跃申请
        when(productApi.getProduct("P001")).thenReturn(Optional.of(buildProduct("P001", true)));
        when(supportMapper.countActiveByCustomerAndProduct("CUST001", "P001")).thenReturn(2L);

        // when / then
        assertThatThrownBy(() -> splitService.splitByProducts(
                List.of("P001"), "CUST001", null, "E10001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40906");
    }

    @Test
    void splitByProducts_shouldAssignProductResponsibleEmp() {
        // given: 产品负责人 E20001
        when(productApi.getProduct("P001")).thenReturn(Optional.of(buildProduct("P001", true)));
        when(productApi.getProductResponsibleEmpIds("P001")).thenReturn(List.of("E20001", "E30001"));
        when(supportMapper.countActiveByCustomerAndProduct("CUST001", "P001")).thenReturn(0L);
        when(bizNoGenerator.generateSupportNo()).thenReturn("SR20260414000001");
        when(supportMapper.insert(any(SupportRequest.class))).thenReturn(1);

        // when
        List<SupportRequest> result = splitService.splitByProducts(
                List.of("P001"), "CUST001", null, "E10001", "ORG001");

        // then: 应取第一个负责人
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).insert(captor.capture());
        assertThat(captor.getValue().getAssignedEmpId()).isEqualTo("E20001");
    }

    // ==================== 辅助方法 ====================

    private ProductDTO buildProduct(String id, boolean supportAvailable) {
        return ProductDTO.builder()
                .id(id)
                .productCode("PC" + id)
                .productName("产品" + id)
                .supportForSupportRequest(supportAvailable)
                .status("ACTIVE")
                .build();
    }
}
