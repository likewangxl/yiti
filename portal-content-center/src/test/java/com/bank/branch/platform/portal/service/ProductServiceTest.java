package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.config.MemoryCacheService;
import com.bank.branch.platform.portal.api.dto.ProductCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductUpdateReqDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ProductService 产品 CRUD 与负责人关系编排测试。 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductInfoMapper productInfoMapper;
    @Mock AddrbookQueryService addrbookQueryService;
    @Mock UserProductRelationService userProductRelationService;
    @Mock CurrentUserApi currentUserApi;
    @Mock BizScopeApi bizScopeApi;
    @Mock AuditApi auditApi;
    @Mock MemoryCacheService memoryCacheService;

    @InjectMocks ProductService productService;

    @Test
    void createProduct_persistsProductAndReplacesRelationSet() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("PRD001");
        req.setProductName("测试产品");
        req.setProductCategory("LOAN");
        req.setDescription("测试描述");
        req.setSupportForSupportRequest(true);
        req.setProductDeptOrgCode("ORG001");
        req.setFileObjectId("file-001");
        req.setResponsibleEmpIds(Arrays.asList(" E001 ", "E002", "E001", " "));
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("PRD001")).thenReturn(null);
        when(addrbookQueryService.findInvalidEmpIds(List.of("E001", "E002"))).thenReturn(Collections.emptyList());

        ProductInfo result = productService.createProduct(req);

        assertThat(result.getProductCode()).isEqualTo("PRD001");
        assertThat(result.getCreatedBy()).isEqualTo("OPERATOR01");
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(productInfoMapper).insert(any(ProductInfo.class));
        verify(userProductRelationService).replaceUsersForProduct(
                eq(result.getId()), eq(List.of("E001", "E002")), eq("OPERATOR01"));
        verify(addrbookQueryService).findInvalidEmpIds(List.of("E001", "E002"));
        verify(auditApi).log(any());
    }

    @Test
    void createProduct_duplicateCode_doesNotWrite() {
        ProductCreateReqDTO req = createRequest("EXISTING_CODE");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("EXISTING_CODE")).thenReturn(new ProductInfo());

        assertThatThrownBy(() -> productService.createProduct(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.PRODUCT_CODE_DUPLICATE.getCode()));
        verify(productInfoMapper, never()).insert(any(ProductInfo.class));
        verify(userProductRelationService, never()).replaceUsersForProduct(anyString(), any(), anyString());
    }

    @Test
    void createProduct_invalidEmployee_doesNotWrite() {
        ProductCreateReqDTO req = createRequest("PRD002");
        req.setResponsibleEmpIds(List.of("E001", "E002"));
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("PRD002")).thenReturn(null);
        when(addrbookQueryService.findInvalidEmpIds(List.of("E001", "E002"))).thenReturn(List.of("E002"));

        assertThatThrownBy(() -> productService.createProduct(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.EMPLOYEE_RESIGNED.getCode()));
        verify(productInfoMapper, never()).insert(any(ProductInfo.class));
    }

    @Test
    void updateProduct_withoutResponsibleField_keepsRelationSet() {
        ProductInfo existing = buildProduct("P001", "PRD001");
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("新名称");

        ProductInfo result = productService.updateProduct("P001", req);

        assertThat(result.getProductName()).isEqualTo("新名称");
        verify(productInfoMapper).updateById(any(ProductInfo.class));
        verify(userProductRelationService, never()).replaceUsersForProduct(anyString(), any(), anyString());
        verify(auditApi).log(any());
    }

    @Test
    void updateProduct_withResponsibleField_validatesAndReplacesRelations() {
        ProductInfo existing = buildProduct("P001", "PRD001");
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(addrbookQueryService.findInvalidEmpIds(List.of("E002", "E003"))).thenReturn(Collections.emptyList());
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setResponsibleEmpIds(Arrays.asList(" E002 ", "E003", "E002"));

        productService.updateProduct("P001", req);

        verify(addrbookQueryService).findInvalidEmpIds(List.of("E002", "E003"));
        verify(userProductRelationService).replaceUsersForProduct("P001", List.of("E002", "E003"), "OPERATOR01");
    }

    @Test
    void updateProduct_emptyResponsibleField_clearsRelations() {
        ProductInfo existing = buildProduct("P001", "PRD001");
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setResponsibleEmpIds(Collections.emptyList());

        productService.updateProduct("P001", req);

        verify(userProductRelationService).replaceUsersForProduct("P001", Collections.emptyList(), "OPERATOR01");
        verify(addrbookQueryService, never()).findInvalidEmpIds(any());
    }

    @Test
    void updateProduct_notFound_doesNotWrite() {
        when(productInfoMapper.selectByIdForUpdate("P999")).thenReturn(null);
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("x");

        assertThatThrownBy(() -> productService.updateProduct("P999", req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.PRODUCT_NOT_FOUND.getCode()));
        verify(productInfoMapper, never()).updateById(any(ProductInfo.class));
    }

    @Test
    void deleteProduct_withoutRelations_softDeletes() {
        ProductInfo existing = buildProduct("P001", "PRD001");
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(userProductRelationService.countUsersByProductId("P001")).thenReturn(0);

        productService.deleteProduct("P001");

        verify(userProductRelationService).countUsersByProductId("P001");
        verify(productInfoMapper).softDeleteById("P001", "OPERATOR01");
        verify(auditApi).log(any());
    }

    @Test
    void deleteProduct_withRelations_isRejectedBeforeSoftDelete() {
        ProductInfo existing = buildProduct("P001", "PRD001");
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(userProductRelationService.countUsersByProductId("P001")).thenReturn(2);

        assertThatThrownBy(() -> productService.deleteProduct("P001"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.PRODUCT_STILL_REFERRED.getCode()));
        verify(productInfoMapper, never()).softDeleteById(anyString(), anyString());
    }

    @Test
    void deleteProduct_orgMismatch_isRejected() {
        ProductInfo existing = buildProduct("P001", "PRD001");
        existing.setProductDeptOrgCode("ORG_OWNER");
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_OTHER");

        assertThatThrownBy(() -> productService.deleteProduct("P001"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.NO_RIGHT_TO_PRODUCT_DEPT.getCode()));
        verify(userProductRelationService, never()).countUsersByProductId(anyString());
    }

    @Test
    void listSupportAvailable_cacheMiss_queriesAndCaches() {
        when(memoryCacheService.get("portal:product:support-available")).thenReturn(null);
        List<ProductInfo> dbResult = List.of(buildProduct("P001", "PRD001"), buildProduct("P002", "PRD002"));
        when(productInfoMapper.listSupportAvailable()).thenReturn(dbResult);

        assertThat(productService.listSupportAvailable()).containsExactlyElementsOf(dbResult);

        verify(productInfoMapper).listSupportAvailable();
        verify(memoryCacheService).put(eq("portal:product:support-available"), eq(dbResult), any());
    }

    @Test
    void listSupportAvailable_cacheHit_skipsDb() {
        List<ProductInfo> cached = List.of(buildProduct("P001", "PRD001"));
        when(memoryCacheService.get("portal:product:support-available")).thenReturn(cached);

        assertThat(productService.listSupportAvailable()).containsExactlyElementsOf(cached);

        verify(productInfoMapper, never()).listSupportAvailable();
    }

    @Test
    void listProducts_passesSupportAndDepartmentFilters() {
        ProductQueryReqDTO req = new ProductQueryReqDTO();
        req.setSupportForSupportRequest(true);
        req.setProductDeptOrgCode("ORG001");
        req.setPageNo(1);
        req.setPageSize(20);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(productInfoMapper.countProducts(any())).thenReturn(1L);
        when(productInfoMapper.listProducts(any())).thenReturn(List.of(buildProduct("P001", "PRD001")));

        productService.listProducts(req);

        ArgumentCaptor<com.bank.branch.platform.portal.service.dto.ProductListQuery> captor =
                ArgumentCaptor.forClass(com.bank.branch.platform.portal.service.dto.ProductListQuery.class);
        verify(productInfoMapper).listProducts(captor.capture());
        assertThat(captor.getValue().getSupportForSupportRequest()).isTrue();
        assertThat(captor.getValue().getProductDeptOrgCode()).isEqualTo("ORG001");
    }

    private ProductCreateReqDTO createRequest(String productCode) {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode(productCode);
        req.setProductName("测试产品");
        req.setProductCategory("LOAN");
        req.setSupportForSupportRequest(false);
        req.setProductDeptOrgCode("ORG001");
        return req;
    }

    private ProductInfo buildProduct(String id, String code) {
        ProductInfo product = new ProductInfo();
        product.setId(id);
        product.setProductCode(code);
        product.setProductName("产品-" + code);
        product.setProductCategory("LOAN");
        product.setProductDeptOrgCode("ORG001");
        product.setStatus("ACTIVE");
        product.setCreatedBy("CREATOR01");
        product.setCreatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        product.setUpdatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        product.setDeleted(0);
        return product;
    }
}
