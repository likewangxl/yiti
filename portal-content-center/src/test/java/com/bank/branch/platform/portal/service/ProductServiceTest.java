package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.portal.api.dto.ProductCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductUpdateReqDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProductService 单元测试 -- 纯 JUnit 5 + Mockito，无需 Spring 上下文
 *
 * <p>TDD RED-GREEN 闭环：先写测试（Red），再实现 Service（Green）。
 * 涵盖 CRUD、双向同步、缓存、事件发布等核心场景。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductInfoMapper productInfoMapper;
    @Mock AddrbookEmployeeMapper addrbookEmployeeMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock BizScopeApi bizScopeApi;
    @Mock AuditApi auditApi;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ProductService productService;

    @Test
    void createProduct_success() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("PRD001"); req.setProductName("测试产品"); req.setProductCategory("LOAN");
        req.setDescription("测试描述"); req.setSupportForSupportRequest(true);
        req.setProductDeptOrgCode("ORG001"); req.setFileObjectId("file-001");
        req.setResponsibleEmpIds(Arrays.asList("E001", "E002"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("PRD001")).thenReturn(null);
        when(addrbookEmployeeMapper.countActiveByEmpIds(Arrays.asList("E001", "E002"))).thenReturn(2);
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(buildEmployee("E001", new ArrayList<>()));
        when(addrbookEmployeeMapper.selectByEmpId("E002")).thenReturn(buildEmployee("E002", new ArrayList<>()));
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(anyString(), any(), any(), anyString())).thenReturn(1);
        ProductInfo result = productService.createProduct(req);
        assertThat(result).isNotNull();
        assertThat(result.getProductCode()).isEqualTo("PRD001");
        assertThat(result.getCreatedBy()).isEqualTo("OPERATOR01");
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(productInfoMapper).insert(any(ProductInfo.class));
        verify(eventPublisher).publishEvent(any(ProductResponsibleUpdatedEvent.class));
        verify(auditApi).log(any());
    }

    @Test
    void createProduct_duplicateCode() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("EXISTING_CODE"); req.setProductName("重复产品"); req.setProductCategory("LOAN");
        req.setSupportForSupportRequest(false); req.setProductDeptOrgCode("ORG001");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("EXISTING_CODE")).thenReturn(new ProductInfo());
        assertThatThrownBy(() -> productService.createProduct(req)).isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(PortalErrorCode.PRODUCT_CODE_DUPLICATE.getCode()));
        verify(productInfoMapper, never()).insert(any());
    }

    @Test
    void createProduct_resignedEmployee() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("PRD002"); req.setProductName("产品2"); req.setProductCategory("DEPOSIT");
        req.setSupportForSupportRequest(true); req.setProductDeptOrgCode("ORG002");
        req.setResponsibleEmpIds(Arrays.asList("E001", "E002", "E003"));
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("PRD002")).thenReturn(null);
        when(addrbookEmployeeMapper.countActiveByEmpIds(Arrays.asList("E001", "E002", "E003"))).thenReturn(2);
        assertThatThrownBy(() -> productService.createProduct(req)).isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(PortalErrorCode.EMPLOYEE_RESIGNED.getCode()));
        verify(productInfoMapper, never()).insert(any());
    }

    @Test
    void updateProduct_success() {
        String productId = "prod-001";
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("更新后名称"); req.setDescription("更新后描述");
        when(productInfoMapper.selectByIdForUpdate(productId)).thenReturn(buildProduct(productId, "PRD001", Arrays.asList("E001")));
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        ProductInfo result = productService.updateProduct(productId, req);
        assertThat(result).isNotNull();
        verify(productInfoMapper).updateById(any(ProductInfo.class));
        verify(auditApi).log(any());
    }

    @Test
    void updateProduct_notFound() {
        when(productInfoMapper.selectByIdForUpdate("nonexistent")).thenReturn(null);
        ProductUpdateReqDTO req = new ProductUpdateReqDTO(); req.setProductName("x");
        assertThatThrownBy(() -> productService.updateProduct("nonexistent", req)).isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(PortalErrorCode.PRODUCT_NOT_FOUND.getCode()));
        verify(productInfoMapper, never()).updateById(any());
    }

    @Test
    void updateProduct_responsibleSync() {
        String productId = "prod-002";
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setResponsibleEmpIds(Arrays.asList("E002", "E003"));
        when(productInfoMapper.selectByIdForUpdate(productId)).thenReturn(buildProduct(productId, "PRD002", Arrays.asList("E001", "E002")));
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(addrbookEmployeeMapper.countActiveByEmpIds(Arrays.asList("E002", "E003"))).thenReturn(2);
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(buildEmployee("E001", new ArrayList<>(Collections.singletonList(productId))));
        when(addrbookEmployeeMapper.selectByEmpId("E003")).thenReturn(buildEmployee("E003", new ArrayList<>()));
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(anyString(), any(), any(), anyString())).thenReturn(1);
        productService.updateProduct(productId, req);
        verify(addrbookEmployeeMapper).selectByEmpId("E001");
        verify(addrbookEmployeeMapper).selectByEmpId("E003");
        verify(addrbookEmployeeMapper, times(2)).updateResponsibleProductsWithOptimisticLock(anyString(), any(), any(), anyString());
        verify(eventPublisher).publishEvent(any(ProductResponsibleUpdatedEvent.class));
    }

    @Test
    void deleteProduct_success_cleansReferences() {
        String productId = "prod-003";
        when(productInfoMapper.selectByIdForUpdate(productId)).thenReturn(buildProduct(productId, "PRD003", Arrays.asList("E001", "E002")));
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(buildEmployee("E001", new ArrayList<>(Collections.singletonList(productId))));
        when(addrbookEmployeeMapper.selectByEmpId("E002")).thenReturn(buildEmployee("E002", new ArrayList<>(Collections.singletonList(productId))));
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(anyString(), any(), any(), anyString())).thenReturn(1);
        productService.deleteProduct(productId);
        verify(productInfoMapper).softDeleteById(productId, "OPERATOR01");
        verify(addrbookEmployeeMapper, times(2)).updateResponsibleProductsWithOptimisticLock(anyString(), any(), any(), anyString());
        ArgumentCaptor<ProductResponsibleUpdatedEvent> cap = ArgumentCaptor.forClass(ProductResponsibleUpdatedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().getAfterEmpIds()).isEmpty();
        assertThat(cap.getValue().getBeforeEmpIds()).containsExactlyInAnyOrder("E001", "E002");
        verify(auditApi).log(any());
    }

    @Test
    void deleteProduct_notFound() {
        when(productInfoMapper.selectByIdForUpdate("nonexistent")).thenReturn(null);
        assertThatThrownBy(() -> productService.deleteProduct("nonexistent")).isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(PortalErrorCode.PRODUCT_NOT_FOUND.getCode()));
        verify(productInfoMapper, never()).softDeleteById(anyString(), anyString());
    }

    @Test @SuppressWarnings("unchecked")
    void listSupportAvailable_cached() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        List<ProductInfo> dbResult = Arrays.asList(buildProduct("p1", "PRD001", Collections.emptyList()), buildProduct("p2", "PRD002", Collections.emptyList()));
        when(valueOps.get("portal:product:support-available")).thenReturn(null, (Object) dbResult);
        when(productInfoMapper.listSupportAvailable()).thenReturn(dbResult);
        List<ProductInfo> r1 = productService.listSupportAvailable();
        assertThat(r1).hasSize(2);
        verify(productInfoMapper, times(1)).listSupportAvailable();
        verify(valueOps).set(eq("portal:product:support-available"), eq(dbResult), any());
        List<ProductInfo> r2 = productService.listSupportAvailable();
        assertThat(r2).hasSize(2);
        verify(productInfoMapper, times(1)).listSupportAvailable();
    }

    @Test
    void getProduct_notFound() {
        when(productInfoMapper.selectById("nonexistent")).thenReturn(null);
        assertThatThrownBy(() -> productService.getProduct("nonexistent")).isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(PortalErrorCode.PRODUCT_NOT_FOUND.getCode()));
    }

    // ========== D.4 createProduct 补充测试 ==========

    @Test
    void createProductShouldInsertAndPublishEvent() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("DEPOSIT_001");
        req.setProductName("活期存款");
        req.setProductCategory("CAT_DEPOSIT");
        req.setSupportForSupportRequest(true);
        req.setProductDeptOrgCode("ORG_SZ_001");
        req.setResponsibleEmpIds(List.of("E10001", "E10002"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("DEPOSIT_001")).thenReturn(null);
        when(addrbookEmployeeMapper.countActiveByEmpIds(List.of("E10001", "E10002"))).thenReturn(2);
        when(addrbookEmployeeMapper.selectByEmpId("E10001")).thenReturn(buildEmployee("E10001", new ArrayList<>()));
        when(addrbookEmployeeMapper.selectByEmpId("E10002")).thenReturn(buildEmployee("E10002", new ArrayList<>()));
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(anyString(), any(), any(), anyString())).thenReturn(1);

        ProductInfo result = productService.createProduct(req);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotBlank();
        verify(productInfoMapper).insert(any());
        ArgumentCaptor<ProductResponsibleUpdatedEvent> captor = ArgumentCaptor.forClass(ProductResponsibleUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getAfterEmpIds()).containsExactly("E10001", "E10002");
        assertThat(captor.getValue().getBeforeEmpIds()).isEmpty();
    }

    @Test
    void createProductShouldThrowWhenDuplicateCodePreCheck() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("EXISTING_CODE");
        req.setProductName("活期存款");
        req.setProductCategory("CAT_DEPOSIT");
        req.setSupportForSupportRequest(true);
        req.setProductDeptOrgCode("ORG_SZ_001");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("EXISTING_CODE")).thenReturn(new ProductInfo());

        assertThatThrownBy(() -> productService.createProduct(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PORTAL-40901"));
    }

    @Test
    void createProductShouldNotPublishEventWhenNoResponsibleEmpIds() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("DEPOSIT_003");
        req.setProductName("定期存款");
        req.setProductCategory("CAT_DEPOSIT");
        req.setSupportForSupportRequest(false);
        req.setProductDeptOrgCode("ORG_SZ_001");
        // 不设置 responsibleEmpIds
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("DEPOSIT_003")).thenReturn(null);

        ProductInfo result = productService.createProduct(req);
        assertThat(result).isNotNull();
        verify(productInfoMapper).insert(any());
        verify(eventPublisher, never()).publishEvent(any(ProductResponsibleUpdatedEvent.class));
    }

    @Test
    void createProductShouldThrowWhenEmployeeResigned() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("DEPOSIT_004");
        req.setProductName("基金产品");
        req.setProductCategory("CAT_FUND");
        req.setSupportForSupportRequest(true);
        req.setProductDeptOrgCode("ORG_SZ_001");
        req.setResponsibleEmpIds(List.of("E001", "E002", "E003"));
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("DEPOSIT_004")).thenReturn(null);
        when(addrbookEmployeeMapper.countActiveByEmpIds(List.of("E001", "E002", "E003"))).thenReturn(2);

        assertThatThrownBy(() -> productService.createProduct(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PORTAL-40902"));
        verify(productInfoMapper, never()).insert(any());
    }

    @Test
    void createProductShouldSetStatusActiveByDefault() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("DEPOSIT_005");
        req.setProductName("理财产品");
        req.setProductCategory("CAT_WEALTH");
        req.setSupportForSupportRequest(false);
        req.setProductDeptOrgCode("ORG_BJ_001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("DEPOSIT_005")).thenReturn(null);

        ProductInfo result = productService.createProduct(req);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getDeleted()).isEqualTo(0);
        assertThat(result.getCreatedBy()).isEqualTo("E10001");
    }

    @Test
    void createProductShouldThrowWhenOrgCodeMismatch() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("PRD_ORG_TEST");
        req.setProductName("权限测试产品");
        req.setProductCategory("LOAN");
        req.setSupportForSupportRequest(false);
        req.setProductDeptOrgCode("ORG_OTHER");
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");

        assertThatThrownBy(() -> productService.createProduct(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PORTAL-40302"));
        verify(productInfoMapper, never()).insert(any());
    }

    // ========== D.5 updateProduct 补充测试 ==========

    @Test
    void updateProductShouldThrowWhenNotFound() {
        when(productInfoMapper.selectByIdForUpdate("P999")).thenReturn(null);
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("新名称");
        assertThatThrownBy(() -> productService.updateProduct("P999", req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PORTAL-40003"));
    }

    @Test
    void updateProductShouldUseForUpdateLock() {
        ProductInfo existing = buildProduct("P001", "DEPOSIT_001", List.of());
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("新名称");

        productService.updateProduct("P001", req);
        verify(productInfoMapper).selectByIdForUpdate("P001");
        verify(productInfoMapper).updateById(any());
    }

    @Test
    void updateProductShouldComputeDiffAndPublishEvent() {
        ProductInfo existing = buildProduct("P001", "DEPOSIT_001", Arrays.asList("E001", "E002"));
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(addrbookEmployeeMapper.countActiveByEmpIds(Arrays.asList("E002", "E003"))).thenReturn(2);
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(buildEmployee("E001", new ArrayList<>(Collections.singletonList("P001"))));
        when(addrbookEmployeeMapper.selectByEmpId("E003")).thenReturn(buildEmployee("E003", new ArrayList<>()));
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(anyString(), any(), any(), anyString())).thenReturn(1);
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setResponsibleEmpIds(Arrays.asList("E002", "E003"));

        productService.updateProduct("P001", req);

        ArgumentCaptor<ProductResponsibleUpdatedEvent> captor = ArgumentCaptor.forClass(ProductResponsibleUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProductResponsibleUpdatedEvent event = captor.getValue();
        assertThat(event.getBeforeEmpIds()).containsExactlyInAnyOrder("E001", "E002");
        assertThat(event.getAfterEmpIds()).containsExactlyInAnyOrder("E002", "E003");
    }

    @Test
    void updateProductShouldNotPublishEventWhenEmpIdsUnchanged() {
        ProductInfo existing = buildProduct("P001", "DEPOSIT_001", Arrays.asList("E001"));
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("新名称");
        // 不设置 responsibleEmpIds，不触发变更事件

        productService.updateProduct("P001", req);
        verify(eventPublisher, never()).publishEvent(any(ProductResponsibleUpdatedEvent.class));
    }

    @Test
    void updateProductShouldOnlyUpdateNonNullFields() {
        ProductInfo existing = buildProduct("P001", "DEPOSIT_001", List.of());
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("更新名称");
        // 其他字段为 null，不应被更新

        ProductInfo result = productService.updateProduct("P001", req);
        assertThat(result.getProductName()).isEqualTo("更新名称");
        verify(productInfoMapper).updateById(any(ProductInfo.class));
    }

    // ========== D.6 deleteProduct 补充测试 ==========

    @Test
    void deleteProductShouldThrowWhenNotFound() {
        when(productInfoMapper.selectByIdForUpdate("P999")).thenReturn(null);
        assertThatThrownBy(() -> productService.deleteProduct("P999"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PORTAL-40003"));
    }

    @Test
    void deleteProductShouldThrow40905WhenStillReferenced() {
        ProductInfo existing = buildProduct("P001", "DEPOSIT_001", List.of("E001"));
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(addrbookEmployeeMapper.countEmployeesReferringProduct("P001")).thenReturn(2);

        assertThatThrownBy(() -> productService.deleteProduct("P001"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PORTAL-40905"));
        verify(productInfoMapper, never()).softDeleteById(anyString(), anyString());
    }

    @Test
    void deleteProductShouldSoftDeleteAndPublishEvent() {
        ProductInfo existing = buildProduct("P001", "DEPOSIT_001", Arrays.asList("E001"));
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(addrbookEmployeeMapper.countEmployeesReferringProduct("P001")).thenReturn(0);
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(buildEmployee("E001", new ArrayList<>(Collections.singletonList("P001"))));
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(anyString(), any(), any(), anyString())).thenReturn(1);

        productService.deleteProduct("P001");

        verify(productInfoMapper).softDeleteById("P001", "OPERATOR01");
        ArgumentCaptor<ProductResponsibleUpdatedEvent> captor = ArgumentCaptor.forClass(ProductResponsibleUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getAfterEmpIds()).isEmpty();
        assertThat(captor.getValue().getBeforeEmpIds()).containsExactly("E001");
    }

    @Test
    void deleteProductShouldSucceedWhenNoResponsibleEmps() {
        ProductInfo existing = buildProduct("P001", "DEPOSIT_001", Collections.emptyList());
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(addrbookEmployeeMapper.countEmployeesReferringProduct("P001")).thenReturn(0);

        productService.deleteProduct("P001");

        verify(productInfoMapper).softDeleteById("P001", "OPERATOR01");
        // 无负责人时仍发布事件（beforeEmpIds 为空，afterEmpIds 为空）
        verify(eventPublisher).publishEvent(any(ProductResponsibleUpdatedEvent.class));
    }

    @Test
    void deleteProductShouldThrowWhenOrgCodeMismatch() {
        ProductInfo existing = buildProduct("P001", "DEPOSIT_001", Collections.emptyList());
        when(productInfoMapper.selectByIdForUpdate("P001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_OTHER");

        assertThatThrownBy(() -> productService.deleteProduct("P001"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PORTAL-40302"));
        verify(productInfoMapper, never()).softDeleteById(anyString(), anyString());
    }

    // ========== Cache-Aside 补充测试 ==========

    @Test
    @SuppressWarnings("unchecked")
    void listSupportAvailable_cacheMiss_queriesDbAndCaches() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("portal:product:support-available")).thenReturn(null);
        List<ProductInfo> dbResult = Arrays.asList(
                buildProduct("p1", "PRD001", Collections.emptyList()),
                buildProduct("p2", "PRD002", Collections.emptyList()));
        when(productInfoMapper.listSupportAvailable()).thenReturn(dbResult);

        List<ProductInfo> result = productService.listSupportAvailable();

        assertThat(result).hasSize(2);
        verify(productInfoMapper, times(1)).listSupportAvailable();
        verify(valueOps).set(eq("portal:product:support-available"), eq(dbResult), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSupportAvailable_cacheHit_skipsDb() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        List<ProductInfo> cachedResult = Collections.singletonList(
                buildProduct("p1", "PRD001", Collections.emptyList()));
        when(valueOps.get("portal:product:support-available")).thenReturn(cachedResult);

        List<ProductInfo> result = productService.listSupportAvailable();

        assertThat(result).hasSize(1);
        // 缓存命中时不应查询数据库
        verify(productInfoMapper, never()).listSupportAvailable();
    }

    @Test
    void createProduct_clearsCache() {
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("PRD_CACHE_TEST");
        req.setProductName("缓存测试产品");
        req.setProductCategory("LOAN");
        req.setSupportForSupportRequest(false);
        req.setProductDeptOrgCode("ORG001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(productInfoMapper.selectByProductCode("PRD_CACHE_TEST")).thenReturn(null);

        productService.createProduct(req);

        // 写操作完成后应清除产品支持缓存
        verify(redisTemplate).delete("portal:product:support-available");
    }

    private ProductInfo buildProduct(String id, String productCode, List<String> responsibleEmpIds) {
        ProductInfo p = new ProductInfo(); p.setId(id); p.setProductCode(productCode); p.setProductName("产品-" + productCode);
        p.setProductCategory("LOAN"); p.setStatus("ACTIVE"); p.setResponsibleEmpIds(responsibleEmpIds);
        p.setProductDeptOrgCode("ORG001"); p.setCreatedBy("CREATOR01");
        p.setCreatedTime(LocalDateTime.of(2026, 4, 1, 10, 0)); p.setUpdatedTime(LocalDateTime.of(2026, 4, 1, 10, 0)); p.setDeleted(0);
        return p;
    }

    private AddrbookEmployee buildEmployee(String empId, List<String> responsibleProductIds) {
        AddrbookEmployee e = new AddrbookEmployee(); e.setEmpId(empId); e.setEmpName("员工-" + empId);
        e.setStatus("ACTIVE"); e.setResponsibleProductIds(responsibleProductIds != null ? responsibleProductIds : new ArrayList<>());
        e.setUpdatedTime(LocalDateTime.of(2026, 4, 1, 10, 0)); e.setDeleted(0);
        return e;
    }
}
