package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.portal.adapter.DataScopeAdapter;
import com.bank.branch.platform.portal.api.dto.ProductCreateReqDTO;
import com.bank.branch.platform.portal.config.PortalCacheConfig;
import com.bank.branch.platform.portal.controller.dto.product.ProductQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductUpdateReqDTO;
import com.bank.branch.platform.portal.service.dto.ProductListQuery;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.governance.config.MemoryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductInfoMapper productInfoMapper;
    private final AddrbookQueryService addrbookQueryService;
    private final UserProductRelationService userProductRelationService;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;
    private final AuditApi auditApi;
    private final MemoryCacheService memoryCacheService;

    /** D.1 分页查询产品列表（含 DATA_SCOPE 数据权限过滤） */
    public PageResult<ProductInfo> listProducts(ProductQueryReqDTO req) {
        com.bank.branch.platform.auth.api.dto.DataScopeContext authRecord =
                bizScopeApi.buildScopeContext(currentUserApi.getCurrentEmpId(), BizType.PRODUCT, BizAction.LIST);
        DataScopeContext pojo = DataScopeAdapter.fromAuthRecord(authRecord);
        int pageNo = req.getPageNo() != null ? req.getPageNo() : 1;
        int pageSize = req.getPageSize() != null ? req.getPageSize() : 20;
        int offset = (pageNo - 1) * pageSize;
        // 使用带 DATA_SCOPE 的 listProducts / countProducts 方法
        ProductListQuery query = ProductListQuery.builder()
                .keyword(req.getKeyword())
                .category(req.getCategory())
                .status(req.getStatus())
                .productDeptOrgCode(req.getProductDeptOrgCode())
                .supportForSupportRequest(req.getSupportForSupportRequest())
                .offset(offset)
                .limit(pageSize)
                .dataScope(pojo)
                .build();
        long total = productInfoMapper.countProducts(query);
        if (total == 0) { return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList()); }
        List<ProductInfo> records = productInfoMapper.listProducts(query);
        return PageResult.of(pageNo, pageSize, total, records);
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
        Object cached = memoryCacheService.get(PortalCacheConfig.PRODUCT_SUPPORT_KEY);
        if (cached != null) { log.debug("[ProductService.listSupportAvailable] cache hit"); return (List<ProductInfo>) cached; }
        log.debug("[ProductService.listSupportAvailable] cache miss");
        List<ProductInfo> items = productInfoMapper.listSupportAvailable();
        memoryCacheService.put(
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
        // 机构权限校验：当前用户 orgCode 须与产品部门 orgCode 匹配（系统管理员豁免）
        if (!currentUserApi.isSystemAdmin()) {
            String currentOrgCode = currentUserApi.getCurrentOrgCode();
            if (currentOrgCode == null || !currentOrgCode.equals(req.getProductDeptOrgCode())) {
                throw new BizException(
                        PortalErrorCode.NO_RIGHT_TO_PRODUCT_DEPT.getCode(),
                        PortalErrorCode.NO_RIGHT_TO_PRODUCT_DEPT.getMessage());
            }
        }
        if (productInfoMapper.selectByProductCode(req.getProductCode()) != null) {
            throw new BizException(PortalErrorCode.PRODUCT_CODE_DUPLICATE.getCode(), PortalErrorCode.PRODUCT_CODE_DUPLICATE.getMessage());
        }
        List<String> empIds = normalizeIds(req.getResponsibleEmpIds());
        validateResponsibleEmpIds(empIds);
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
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(currentEmpId);
        entity.setUpdatedBy(currentEmpId);
        entity.setDeleted(0);
        productInfoMapper.insert(entity);
        userProductRelationService.replaceUsersForProduct(productId, empIds, currentEmpId);
        clearSupportCache();
        auditCreate(entity, currentEmpId);
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
        List<String> newEmpIds = req.getResponsibleEmpIds() == null
                ? null : normalizeIds(req.getResponsibleEmpIds());
        if (newEmpIds != null) {
            validateResponsibleEmpIds(newEmpIds);
        }
        ProductInfo patch = new ProductInfo();
        patch.setId(id);
        if (req.getProductName() != null) { patch.setProductName(req.getProductName()); existing.setProductName(req.getProductName()); }
        if (req.getProductCategory() != null) { patch.setProductCategory(req.getProductCategory()); existing.setProductCategory(req.getProductCategory()); }
        // 产品部门可改：同步 owner_org_id，与 createProduct 中两者绑定的语义保持一致
        if (req.getProductDeptOrgCode() != null) {
            patch.setProductDeptOrgCode(req.getProductDeptOrgCode()); existing.setProductDeptOrgCode(req.getProductDeptOrgCode());
            patch.setOwnerOrgId(req.getProductDeptOrgCode()); existing.setOwnerOrgId(req.getProductDeptOrgCode());
        }
        if (req.getDescription() != null) { patch.setDescription(req.getDescription()); existing.setDescription(req.getDescription()); }
        if (req.getSupportForSupportRequest() != null) { patch.setSupportForSupportRequest(req.getSupportForSupportRequest()); existing.setSupportForSupportRequest(req.getSupportForSupportRequest()); }
        if (req.getFileObjectId() != null) { patch.setFileObjectId(req.getFileObjectId()); existing.setFileObjectId(req.getFileObjectId()); }
        if (req.getStatus() != null) { patch.setStatus(req.getStatus()); existing.setStatus(req.getStatus()); }
        patch.setUpdatedBy(currentEmpId);
        productInfoMapper.updateById(patch);
        if (newEmpIds != null) {
            userProductRelationService.replaceUsersForProduct(id, newEmpIds, currentEmpId);
        }
        clearSupportCache();
        auditUpdate(existing, req, currentEmpId);
        return existing;
    }

    /** D.6 删除产品（含前置引用检查 + 高危审计） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(String id) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        // 使用 FOR UPDATE 行锁防竞态（与 updateProduct 一致）
        ProductInfo existing = productInfoMapper.selectByIdForUpdate(id);
        if (existing == null) {
            throw new BizException(PortalErrorCode.PRODUCT_NOT_FOUND.getCode(), PortalErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }
        // 机构权限校验：当前用户 orgCode 须与产品部门 orgCode 匹配（系统管理员豁免）
        if (!currentUserApi.isSystemAdmin()) {
            String currentOrgCode = currentUserApi.getCurrentOrgCode();
            if (currentOrgCode == null || !currentOrgCode.equals(existing.getProductDeptOrgCode())) {
                throw new BizException(
                        PortalErrorCode.NO_RIGHT_TO_PRODUCT_DEPT.getCode(),
                        PortalErrorCode.NO_RIGHT_TO_PRODUCT_DEPT.getMessage());
            }
        }
        // 前置引用检查：关系表仍有负责人引用时，不可删除
        int refCount = userProductRelationService.countUsersByProductId(id);
        if (refCount > 0) {
            throw new BizException(PortalErrorCode.PRODUCT_STILL_REFERRED.getCode(),
                    "仍有 " + refCount + " 名员工引用该产品，不可删除");
        }
        productInfoMapper.softDeleteById(id, currentEmpId);
        clearSupportCache();
        auditDelete(existing, currentEmpId);
    }

    /** 校验负责人均为通讯录中的在职用户。 */
    private void validateResponsibleEmpIds(List<String> empIds) {
        if (empIds.isEmpty()) {
            return;
        }
        List<String> invalidEmpIds = addrbookQueryService.findInvalidEmpIds(empIds);
        if (invalidEmpIds != null && !invalidEmpIds.isEmpty()) {
            throw new BizException(PortalErrorCode.EMPLOYEE_RESIGNED.getCode(),
                    PortalErrorCode.EMPLOYEE_RESIGNED.getMessage());
        }
    }

    /** 统一规范化负责人 ID，保证关系表复合键不会因空白或重复值冲突。 */
    private static List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                normalized.add(id.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    /** 清除产品支持缓存（吞没异常，缓存删除失败不影响主流程） */
    private void clearSupportCache() {
        try { memoryCacheService.evict(PortalCacheConfig.PRODUCT_SUPPORT_KEY); } catch (Exception e) { log.warn("clearSupportCache failed", e); }
    }

    private void auditCreate(ProductInfo entity, String operatorEmpId) {
        safeAuditLog(AuditLogCmd.builder()
                .traceId(MdcUtils.getTraceId())
                .empId(operatorEmpId)
                .bizType("PRODUCT")
                .bizAction("CREATE")
                .resourceUrl("/api/products")
                .requestMethod("POST")
                .requestParams("id=" + entity.getId() + "&productCode=" + entity.getProductCode() + "&productName=" + entity.getProductName())
                .responseStatus(200)
                .build());
    }

    private void auditUpdate(ProductInfo existing, ProductUpdateReqDTO req, String operatorEmpId) {
        safeAuditLog(AuditLogCmd.builder()
                .traceId(MdcUtils.getTraceId())
                .empId(operatorEmpId)
                .bizType("PRODUCT")
                .bizAction("EDIT")
                .resourceUrl("/api/products/" + existing.getId())
                .requestMethod("PUT")
                .requestParams("id=" + existing.getId() + "&productCode=" + existing.getProductCode()
                        + "&productName=" + (req.getProductName() != null ? req.getProductName() : existing.getProductName()))
                .responseStatus(200)
                .build());
    }

    private void auditDelete(ProductInfo existing, String operatorEmpId) {
        safeAuditLog(AuditLogCmd.builder()
                .traceId(MdcUtils.getTraceId())
                .empId(operatorEmpId)
                .bizType("PRODUCT")
                .bizAction("DELETE")
                .resourceUrl("/api/products/" + existing.getId())
                .requestMethod("DELETE")
                .requestParams("id=" + existing.getId() + "&productCode=" + existing.getProductCode()
                        + "&fileObjectId=" + existing.getFileObjectId())
                .responseStatus(200)
                .build());
    }

    private void safeAuditLog(AuditLogCmd cmd) {
        try {
            auditApi.log(cmd);
        } catch (Exception ex) {
            log.warn("[ProductService] audit log failed, action={}", cmd.getBizAction(), ex);
        }
    }
}
