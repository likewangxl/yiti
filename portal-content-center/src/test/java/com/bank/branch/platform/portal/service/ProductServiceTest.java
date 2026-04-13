package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ProductService 单元测试
 * <p>TDD RED-GREEN 闭环：先写测试，再实现 Service</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductInfoMapper productInfoMapper;

    @InjectMocks
    ProductService productService;

    /**
     * Mapper 返回多条记录时，Service 应转换为对应的 ProductSimpleDTO 列表
     */
    @Test
    void listSupportAvailableShouldReturnSimpleDTOsFromMapper() {
        ProductInfo p1 = new ProductInfo();
        p1.setId("P001");
        p1.setProductCode("DEPOSIT_001");
        p1.setProductName("活期存款");
        p1.setProductCategory("CAT_DEPOSIT");
        p1.setProductDeptOrgCode("ORG_HQ_FIN");

        ProductInfo p2 = new ProductInfo();
        p2.setId("P002");
        p2.setProductCode("LOAN_001");
        p2.setProductName("个人消费贷");
        p2.setProductCategory("CAT_LOAN");
        p2.setProductDeptOrgCode("ORG_HQ_LOAN");

        when(productInfoMapper.listSupportAvailable()).thenReturn(Arrays.asList(p1, p2));

        List<ProductSimpleDTO> result = productService.listSupportAvailable();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductCode()).isEqualTo("DEPOSIT_001");
        assertThat(result.get(0).getProductName()).isEqualTo("活期存款");
        assertThat(result.get(1).getProductCode()).isEqualTo("LOAN_001");
    }

    /**
     * Mapper 返回空列表时，Service 应返回空列表（非 null）
     */
    @Test
    void listSupportAvailableShouldReturnEmptyListWhenMapperReturnsEmpty() {
        when(productInfoMapper.listSupportAvailable()).thenReturn(Collections.emptyList());
        List<ProductSimpleDTO> result = productService.listSupportAvailable();
        assertThat(result).isEmpty();
    }
}
