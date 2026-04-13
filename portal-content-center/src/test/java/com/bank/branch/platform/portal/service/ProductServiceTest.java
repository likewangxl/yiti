package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductDetailDTO;
import com.bank.branch.platform.portal.api.dto.ProductListReqDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.portal.service.dto.ProductListQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductService 单元测试
 * <p>TDD RED-GREEN 闭环：先写测试，再实现 Service</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductInfoMapper productInfoMapper;

    @Mock
    DictApi dictApi;

    @Mock
    OrgApi orgApi;

    @Mock
    FileApi fileApi;

    @Mock
    AddrbookQueryService addrbookQueryService;

    @Mock
    CurrentUserApi currentUserApi;

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

    // ========== D.1 listProducts 测试 ==========

    /**
     * listProducts 应返回正确映射的分页结果
     */
    @Test
    void listProductsShouldReturnPageResultWithMappedDTO() {
        // Arrange
        DataScopeContext scope = newScope(DataScopeType.ORG_SUBTREE, "E10001", "ORG_SZ_001",
                Set.of("ORG_SZ_001", "ORG_SZ_002"));

        ProductListReqDTO req = new ProductListReqDTO();
        req.setPageNo(1);
        req.setPageSize(20);

        ProductInfo entity = new ProductInfo();
        entity.setId("P001");
        entity.setProductCode("DEPOSIT_001");
        entity.setProductName("活期存款");
        entity.setProductCategory("CAT_DEPOSIT");
        entity.setProductDeptOrgCode("ORG_SZ_001");
        entity.setSupportForSupportRequest(true);
        entity.setStatus("ACTIVE");

        when(productInfoMapper.countProducts(any(ProductListQuery.class))).thenReturn(1L);
        when(productInfoMapper.listProducts(any(ProductListQuery.class)))
                .thenReturn(List.of(entity));

        // Act
        PageResult<ProductDTO> result = productService.listProducts(req, scope);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);

        ProductDTO dto = result.getRecords().get(0);
        assertThat(dto.getId()).isEqualTo("P001");
        assertThat(dto.getProductCode()).isEqualTo("DEPOSIT_001");
        assertThat(dto.getProductName()).isEqualTo("活期存款");
        assertThat(dto.getProductCategory()).isEqualTo("CAT_DEPOSIT");
        assertThat(dto.getSupportForSupportRequest()).isTrue();
    }

    /**
     * countProducts 返回 0 时应直接返回空分页，不调用 listProducts
     */
    @Test
    void listProductsShouldReturnEmptyPageWhenCountIsZero() {
        // Arrange
        DataScopeContext scope = newScope(DataScopeType.ORG, "E10001", "ORG_SZ_001", null);

        ProductListReqDTO req = new ProductListReqDTO();
        req.setPageNo(1);
        req.setPageSize(20);

        when(productInfoMapper.countProducts(any(ProductListQuery.class))).thenReturn(0L);

        // Act
        PageResult<ProductDTO> result = productService.listProducts(req, scope);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getRecords()).isEmpty();
        // listProducts should NOT be called when count is 0
        verify(productInfoMapper, never()).listProducts(any(ProductListQuery.class));
    }

    // ========== D.2 getProduct 测试 ==========

    /**
     * 产品不存在时应抛出 BizException
     */
    @Test
    void getProductShouldThrowWhenProductNotFound() {
        when(productInfoMapper.selectById("P999")).thenReturn(null);
        assertThatThrownBy(() -> productService.getProduct("P999"))
                .isInstanceOf(BizException.class);
    }

    /**
     * 产品存在时应聚合字典翻译/机构名/附件链接/负责人信息
     */
    @Test
    void getProductShouldAggregateAllSources() {
        ProductInfo entity = new ProductInfo();
        entity.setId("P001");
        entity.setProductCode("DEPOSIT_001");
        entity.setProductCategory("CAT_DEPOSIT");
        entity.setProductDeptOrgCode("ORG_SZ_001");
        entity.setFileObjectId("FILE_ABC");
        entity.setResponsibleEmpIds(List.of("E10001"));

        when(productInfoMapper.selectById("P001")).thenReturn(entity);
        when(dictApi.getDictLabel("PRODUCT_CATEGORY", "CAT_DEPOSIT")).thenReturn("存款类");
        OrgDTO org = new OrgDTO();
        org.setOrgName("深圳分行");
        when(orgApi.getOrg("ORG_SZ_001")).thenReturn(org);
        when(fileApi.getDownloadUrl("FILE_ABC")).thenReturn("https://minio/file/abc");
        ResponsibleEmpDTO empDTO = new ResponsibleEmpDTO();
        empDTO.setEmpId("E10001");
        empDTO.setEmpName("张三");
        when(addrbookQueryService.listResponsibleEmps(List.of("E10001"))).thenReturn(List.of(empDTO));
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");

        ProductDetailDTO result = productService.getProduct("P001");

        assertThat(result.getProductCategoryDesc()).isEqualTo("存款类");
        assertThat(result.getProductDeptOrgName()).isEqualTo("深圳分行");
        assertThat(result.getFileDownloadUrl()).isEqualTo("https://minio/file/abc");
        assertThat(result.getResponsibleEmps()).hasSize(1);
        assertThat(result.getCanEdit()).isTrue();
    }

    /**
     * 构建 DataScopeContext 辅助方法。
     */
    private DataScopeContext newScope(DataScopeType scope, String empId, String orgCode, Set<String> subtree) {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setBizType(BizType.PRODUCT);
        ctx.setAction(BizAction.LIST);
        ctx.setScope(scope);
        ctx.setEmpId(empId);
        ctx.setOrgCode(orgCode);
        ctx.setOrgSubtreeCodes(subtree);
        return ctx;
    }
}
