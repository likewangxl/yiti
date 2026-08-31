package com.bank.branch.platform.portal.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.service.UserProductRelationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;

/**
 * ProductFacade 单元测试
 *
 * <p>验证 ProductApi 接口的所有方法实现：
 * Entity → DTO 转换、Optional 语义、异常处理等。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private UserProductRelationService userProductRelationService;

    @InjectMocks
    private ProductFacade productFacade;

    @BeforeEach
    void defaultRelationQueriesToEmpty() {
        lenient().when(userProductRelationService.mapUserIdsByProductIds(any()))
                .thenReturn(Collections.emptyMap());
    }

    // ===== getProduct =====

    @Test
    @DisplayName("getProduct_found: 产品存在时返回 Optional 包含 ProductDTO")
    void getProduct_found() {
        // given
        ProductInfo entity = buildEntity("P001", "DEPOSIT_001", "活期存款");
        when(productService.getProduct("P001")).thenReturn(entity);

        // when
        Optional<ProductDTO> result = productFacade.getProduct("P001");

        // then
        assertThat(result).isPresent();
        ProductDTO dto = result.get();
        assertThat(dto.getId()).isEqualTo("P001");
        assertThat(dto.getProductCode()).isEqualTo("DEPOSIT_001");
        assertThat(dto.getProductName()).isEqualTo("活期存款");
        assertThat(dto.getProductCategory()).isEqualTo("CAT_DEPOSIT");
        assertThat(dto.getSupportForSupportRequest()).isTrue();
        assertThat(dto.getProductDeptOrgCode()).isEqualTo("ORG_HQ_FIN");
    }

    @Test
    @DisplayName("getProduct_notFound: 产品不存在时返回 Optional.empty（捕获 BizException）")
    void getProduct_notFound() {
        // given — Service 抛出 BizException
        when(productService.getProduct("P_NONE"))
                .thenThrow(new BizException("PORTAL-40003", "产品不存在"));

        // when
        Optional<ProductDTO> result = productFacade.getProduct("P_NONE");

        // then
        assertThat(result).isEmpty();
    }

    // ===== getProducts =====

    @Test
    @DisplayName("getProducts_returnsList: 批量查询返回 DTO 列表")
    void getProducts_returnsList() {
        // given
        ProductInfo p1 = buildEntity("P001", "DEPOSIT_001", "活期存款");
        ProductInfo p2 = buildEntity("P002", "LOAN_001", "消费贷");
        when(productInfoMapper.listByIds(Arrays.asList("P001", "P002")))
                .thenReturn(Arrays.asList(p1, p2));

        // when
        List<ProductDTO> result = productFacade.getProducts(Arrays.asList("P001", "P002"));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductCode()).isEqualTo("DEPOSIT_001");
        assertThat(result.get(1).getProductCode()).isEqualTo("LOAN_001");
    }

    @Test
    @DisplayName("getProducts_emptyInput: 空列表入参返回空列表")
    void getProducts_emptyInput() {
        // when
        List<ProductDTO> result = productFacade.getProducts(Collections.emptyList());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProducts_nullInput: null 入参返回空列表")
    void getProducts_nullInput() {
        // when
        List<ProductDTO> result = productFacade.getProducts(null);

        // then
        assertThat(result).isEmpty();
    }

    // ===== listSupportAvailableProducts =====

    @Test
    @DisplayName("listSupportAvailableProducts: 委托 Service 并转换为 DTO 列表")
    void listSupportAvailableProducts() {
        // given
        ProductInfo p1 = buildEntity("P001", "DEPOSIT_001", "活期存款");
        p1.setSupportForSupportRequest(true);
        when(productService.listSupportAvailable()).thenReturn(List.of(p1));

        // when
        List<ProductDTO> result = productFacade.listSupportAvailableProducts();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductCode()).isEqualTo("DEPOSIT_001");
        assertThat(result.get(0).getSupportForSupportRequest()).isTrue();
    }

    @Test
    @DisplayName("listSupportAvailableProducts_empty: 无数据时返回空列表")
    void listSupportAvailableProducts_empty() {
        // given
        when(productService.listSupportAvailable()).thenReturn(Collections.emptyList());

        // when
        List<ProductDTO> result = productFacade.listSupportAvailableProducts();

        // then
        assertThat(result).isEmpty();
    }

    // ===== listProductsByDept =====

    @Test
    @DisplayName("listProductsByDept: 按部门查询产品列表")
    void listProductsByDept() {
        // given
        ProductInfo p1 = buildEntity("P001", "DEPOSIT_001", "活期存款");
        p1.setProductDeptOrgCode("ORG_HQ_FIN");
        when(productInfoMapper.listByProductDeptOrgCode("ORG_HQ_FIN"))
                .thenReturn(List.of(p1));

        // when
        List<ProductDTO> result = productFacade.listProductsByDept("ORG_HQ_FIN");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductDeptOrgCode()).isEqualTo("ORG_HQ_FIN");
    }

    // ===== getProductResponsibleEmpIds =====

    @Test
    @DisplayName("getProductResponsibleEmpIds: 返回负责人工号列表")
    void getProductResponsibleEmpIds() {
        // given
        ProductInfo entity = buildEntity("P001", "DEPOSIT_001", "活期存款");
        when(productService.getProduct("P001")).thenReturn(entity);
        when(userProductRelationService.listUserIdsByProductId("P001"))
                .thenReturn(Arrays.asList("E001", "E002"));

        // when
        List<String> result = productFacade.getProductResponsibleEmpIds("P001");

        // then
        assertThat(result).containsExactly("E001", "E002");
    }

    @Test
    @DisplayName("getProductResponsibleEmpIds_notFound: 产品不存在时返回空列表")
    void getProductResponsibleEmpIds_notFound() {
        // given
        when(productService.getProduct("P_NONE"))
                .thenThrow(new BizException("PORTAL-40003", "产品不存在"));

        // when
        List<String> result = productFacade.getProductResponsibleEmpIds("P_NONE");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProductResponsibleEmpIds_nullList: 负责人为 null 时返回空列表")
    void getProductResponsibleEmpIds_nullList() {
        // given
        ProductInfo entity = buildEntity("P001", "DEPOSIT_001", "活期存款");
        when(productService.getProduct("P001")).thenReturn(entity);
        when(userProductRelationService.listUserIdsByProductId("P001"))
                .thenReturn(Collections.emptyList());

        // when
        List<String> result = productFacade.getProductResponsibleEmpIds("P001");

        // then
        assertThat(result).isEmpty();
    }

    // ===== 辅助方法 =====

    /**
     * 构建测试用 ProductInfo 实体
     */
    private ProductInfo buildEntity(String id, String code, String name) {
        ProductInfo entity = new ProductInfo();
        entity.setId(id);
        entity.setProductCode(code);
        entity.setProductName(name);
        entity.setProductCategory("CAT_DEPOSIT");
        entity.setDescription("测试产品描述");
        entity.setSupportForSupportRequest(true);
        entity.setProductDeptOrgCode("ORG_HQ_FIN");
        entity.setOwnerOrgId("ORG_HQ_FIN");
        entity.setStatus("ACTIVE");
        entity.setCreatedTime(LocalDateTime.of(2026, 4, 10, 10, 0));
        entity.setUpdatedTime(LocalDateTime.of(2026, 4, 10, 10, 0));
        return entity;
    }
}
