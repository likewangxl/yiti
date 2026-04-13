package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.adapter.DataScopeAdapter;
import com.bank.branch.platform.portal.api.dto.ProductCreateReqDTO;
import com.bank.branch.platform.portal.config.PortalCacheConfig;
import com.bank.branch.platform.portal.controller.dto.product.ProductQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductUpdateReqDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductInfoMapper productInfoMapper;
    private final AddrbookEmployeeMapper addrbookEmployeeMapper;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** D.1 分页查询产品列表（含数据权限过滤） */
    public PageResult<ProductInfo> listProducts(ProductQueryReqDTO req) {
        try {
            com.bank.branch.platform.auth.api.dto.DataScopeContext authRecord =
                    bizScopeApi.buildScopeContext(currentUserApi.getCurrentEmpId(), BizType.PRODUCT, BizAction.LIST);
            DataScopeContext pojo = DataScopeAdapter.fromAuthRecord(authRecord);
            DataScopeContext.set(pojo);
            int pageNo = req.getPageNo() != null ? req.getPageNo() : 1;
            int pageSize = req.getPageSize() != null ? req.getPageSize() : 20;
            int offset = (pageNo - 1) * pageSize;
            long total = productInfoMapper.countPage(req.getKeyword(), req.getCategory(), req.getStatus());
            if (total == 0) { return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList()); }
            List<ProductInfo> records = productInfoMapper.selectPage(req.getKeyword(), req.getCategory(), req.getStatus(), offset, pageSize);
            return PageResult.of(pageNo, pageSize, total, records);
        } finally { DataScopeContext.clear(); }
    }

    /** D.2 按 ID 查询产品详情 */
    public ProductInfo getProduct(String id) {
        ProductInfo entity = productInfoMapper.selectById(id);
        if (entity == null) {
            throw new BizException(PortalErrorCode.PRODUCT_NOT_FOUND.getCode(), PortalErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }
        return entity;
    }

    /** D.3 查询所有支持中场支持的产品（Cache-Aside 模式） */
    @SuppressWarnings("unchecked")
    public List<ProductInfo> listSupportAvailable() {
        Object cached = redisTemplate.opsForValue().get(PortalCacheConfig.PRODUCT_SUPPORT_KEY);
        if (cached != null) { log.debug("[ProductService.listSupportAvailable] cache hit"); return (List<ProductInfo>) cached; }
        log.debug("[ProductService.listSupportAvailable] cache miss");
        List<ProductInfo> items = productInfoMapper.listSupportAvailable();
        redisTemplate.opsForValue().set(
                PortalCacheConfig.PRODUCT_SUPPORT_KEY,
                items,
                PortalCacheConfig.jitteredTtl(PortalCacheConfig.DEFAULT_TTL)
        );
        return items;
    }

    /** D.4 新增产品 */
    @Transactional(rollbackFor = Exception.class)
    public ProductInfo createProduct(ProductCreateReqDTO req) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        if (productInfoMapper.selectByProductCode(req.getProductCode()) != null) {
            throw new BizException(PortalErrorCode.PRODUCT_CODE_DUPLICATE.getCode(), PortalErrorCode.PRODUCT_CODE_DUPLICATE.getMessage());
        }
        List<String> empIds = req.getResponsibleEmpIds();
        if (empIds != null && !empIds.isEmpty()) {
            int activeCount = addrbookEmployeeMapper.countActiveByEmpIds(empIds);
            if (activeCount != empIds.size()) {
                throw new BizException(PortalErrorCode.EMPLOYEE_RESIGNED.getCode(), PortalErrorCode.EMPLOYEE_RESIGNED.getMessage());
            }
        }
        String productId = UUID.randomUUID().toString().replace("-", "");
        ProductInfo entity = new ProductInfo();
        entity.setId(productId);
        entity.setProductCode(req.getProductCode());
        entity.setProductName(req.getProductName());
        entity.setProductCategory(req.getProductCategory());
        entity.setDescription(req.getDescription());
        entity.setSupportForSupportRequest(req.getSupportForSupportRequest());
        entity.setProductDeptOrgCode(req.getProductDeptOrgCode());
        entity.setOwnerOrgId(req.getProductDeptOrgCode());
        entity.setFileObjectId(req.getFileObjectId());
        entity.setResponsibleEmpIds(empIds);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(currentEmpId);
        entity.setUpdatedBy(currentEmpId);
        entity.setDeleted(0);
        productInfoMapper.insert(entity);
        List<String> newEmpIds = empIds != null ? empIds : Collections.emptyList();
        if (!newEmpIds.isEmpty()) { syncResponsibleToAddrbook(productId, Collections.emptyList(), newEmpIds, currentEmpId); }
        clearSupportCache();
        if (!newEmpIds.isEmpty()) {
            eventPublisher.publishEvent(new ProductResponsibleUpdatedEvent(productId, req.getProductCode(), Collections.emptyList(), newEmpIds, "PRODUCT_SIDE", currentEmpId, LocalDateTime.now()));
        }
        return entity;
    }

    /** D.5 编辑产品 */
    @Transactional(rollbackFor = Exception.class)
    public ProductInfo updateProduct(String id, ProductUpdateReqDTO req) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        ProductInfo existing = productInfoMapper.selectByIdForUpdate(id);
        if (existing == null) {
            throw new BizException(PortalErrorCode.PRODUCT_NOT_FOUND.getCode(), PortalErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }
        List<String> oldEmpIds = existing.getResponsibleEmpIds() != null ? existing.getResponsibleEmpIds() : Collections.emptyList();
        List<String> newEmpIds = req.getResponsibleEmpIds();
        boolean empIdsChanged = newEmpIds != null && !new HashSet<>(oldEmpIds).equals(new HashSet<>(newEmpIds));
        if (empIdsChanged && !newEmpIds.isEmpty()) {
            int activeCount = addrbookEmployeeMapper.countActiveByEmpIds(newEmpIds);
            if (activeCount != newEmpIds.size()) {
                throw new BizException(PortalErrorCode.EMPLOYEE_RESIGNED.getCode(), PortalErrorCode.EMPLOYEE_RESIGNED.getMessage());
            }
        }
        ProductInfo patch = new ProductInfo();
        patch.setId(id);
        if (req.getProductName() != null) { patch.setProductName(req.getProductName()); existing.setProductName(req.getProductName()); }
        if (req.getProductCategory() != null) { patch.setProductCategory(req.getProductCategory()); existing.setProductCategory(req.getProductCategory()); }
        if (req.getDescription() != null) { patch.setDescription(req.getDescription()); existing.setDescription(req.getDescription()); }
        if (req.getSupportForSupportRequest() != null) { patch.setSupportForSupportRequest(req.getSupportForSupportRequest()); existing.setSupportForSupportRequest(req.getSupportForSupportRequest()); }
        if (req.getFileObjectId() != null) { patch.setFileObjectId(req.getFileObjectId()); existing.setFileObjectId(req.getFileObjectId()); }
        if (empIdsChanged) { patch.setResponsibleEmpIds(newEmpIds); existing.setResponsibleEmpIds(newEmpIds); }
        patch.setUpdatedBy(currentEmpId);
        productInfoMapper.updateById(patch);
        if (empIdsChanged) { syncResponsibleToAddrbook(id, oldEmpIds, newEmpIds != null ? newEmpIds : Collections.emptyList(), currentEmpId); }
        clearSupportCache();
        if (empIdsChanged) {
            eventPublisher.publishEvent(new ProductResponsibleUpdatedEvent(id, existing.getProductCode(), oldEmpIds, newEmpIds != null ? newEmpIds : Collections.emptyList(), "PRODUCT_SIDE", currentEmpId, LocalDateTime.now()));
        }
        return existing;
    }

    /** D.6 删除产品（含前置引用检查 + 高危审计） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(String id) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        ProductInfo existing = productInfoMapper.selectById(id);
        if (existing == null) {
            throw new BizException(PortalErrorCode.PRODUCT_NOT_FOUND.getCode(), PortalErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }
        // 前置引用检查：仍有员工引用该产品时，不可删除
        int refCount = addrbookEmployeeMapper.countEmployeesReferringProduct(id);
        if (refCount > 0) {
            throw new BizException(PortalErrorCode.PRODUCT_STILL_REFERRED.getCode(),
                    "仍有 " + refCount + " 名员工引用该产品，不可删除");
        }
        productInfoMapper.softDeleteById(id, currentEmpId);
        List<String> oldEmpIds = existing.getResponsibleEmpIds() != null ? existing.getResponsibleEmpIds() : Collections.emptyList();
        if (!oldEmpIds.isEmpty()) { syncResponsibleToAddrbook(id, oldEmpIds, Collections.emptyList(), currentEmpId); }
        clearSupportCache();
        eventPublisher.publishEvent(new ProductResponsibleUpdatedEvent(id, existing.getProductCode(), oldEmpIds, Collections.emptyList(), "PRODUCT_SIDE", currentEmpId, LocalDateTime.now()));
    }

    private void syncResponsibleToAddrbook(String productId, List<String> oldEmpIds, List<String> newEmpIds, String operatorEmpId) {
        Set<String> oldSet = new HashSet<>(oldEmpIds);
        Set<String> newSet = new HashSet<>(newEmpIds);
        Set<String> added = new HashSet<>(newSet); added.removeAll(oldSet);
        Set<String> removed = new HashSet<>(oldSet); removed.removeAll(newSet);
        List<String> allEmpIds = new ArrayList<>(); allEmpIds.addAll(added); allEmpIds.addAll(removed); Collections.sort(allEmpIds);
        for (String empId : allEmpIds) {
            AddrbookEmployee emp = addrbookEmployeeMapper.selectByEmpId(empId);
            if (emp == null) { log.warn("[syncResponsibleToAddrbook] skip missing empId={}", empId); continue; }
            List<String> productIds = emp.getResponsibleProductIds() != null ? new ArrayList<>(emp.getResponsibleProductIds()) : new ArrayList<>();
            if (added.contains(empId)) { if (!productIds.contains(productId)) { productIds.add(productId); } }
            else { productIds.remove(productId); }
            addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(empId, productIds, emp.getUpdatedTime(), operatorEmpId);
        }
    }

    /** 清除产品支持缓存（吞没异常，缓存删除失败不影响主流程） */
    private void clearSupportCache() {
        try { redisTemplate.delete(PortalCacheConfig.PRODUCT_SUPPORT_KEY); } catch (Exception e) { log.warn("clearSupportCache failed", e); }
    }
}
