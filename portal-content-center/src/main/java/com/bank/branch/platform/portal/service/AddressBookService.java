package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserDirectoryApi;
import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeSelfUpdateReqDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通讯录域核心服务。
 *
 * <p>员工主数据统一由 auth 的 {@link UserDirectoryApi} 提供，负责产品关系由
 * {@link UserProductRelationService} 提供。portal 不再读取或写入 ADDRBOOK_EMPLOYEE，
 * 也不再维护产品负责人 JSON 反向字段。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressBookService {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RESPONSIBLE_PRODUCTS = 20;

    private final UserDirectoryApi userDirectoryApi;
    private final UserProductRelationService userProductRelationService;
    private final ProductInfoMapper productInfoMapper;
    private final CurrentUserApi currentUserApi;
    private final AuditApi auditApi;

    /**
     * 分页查询在职通讯录。
     *
     * <p>status、position 在新主数据模型中没有对应的 portal 私有来源；查询统一使用
     * UserDirectoryApi 的在职口径，保留请求字段只是为了兼容现有前端报文。</p>
     */
    public PageResult<UserDirectoryDTO> listEmployees(EmployeeQueryReqDTO req) {
        EmployeeQueryReqDTO query = req == null ? new EmployeeQueryReqDTO() : req;
        int pageNo = positiveOrDefault(query.getPageNo(), DEFAULT_PAGE_NO);
        int pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        return userDirectoryApi.pageActiveUsers(query.getKeyword(), query.getOrgCode(), pageNo, pageSize);
    }

    /** 按 USER_ID 查询通讯录详情；不存在时沿用 portal 的业务错误码。 */
    public UserDirectoryDTO getEmployee(String empId) {
        UserDirectoryDTO employee = userDirectoryApi.getEmployee(empId);
        if (employee == null) {
            throw employeeNotFound();
        }
        return employee;
    }

    /** 批量按 USER_ID 查询通讯录，顺序由 UserDirectoryApi 保证。 */
    public List<UserDirectoryDTO> getEmployees(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDirectoryDTO> employees = userDirectoryApi.getEmployeesByIds(empIds);
        return employees == null ? Collections.emptyList() : employees;
    }

    /** 搜索在职通讯录员工。 */
    public List<UserDirectoryDTO> searchEmployees(String keyword, int limit) {
        List<UserDirectoryDTO> employees = userDirectoryApi.searchEmployees(keyword, Math.min(Math.max(limit, 1), 50));
        return employees == null ? Collections.emptyList() : employees;
    }

    /**
     * 按机构查询在职员工。
     *
     * <p>UserDirectoryApi 以分页形式提供查询，这里按总数继续翻页，避免为兼容旧的
     * AddressBookApi 而退回 portal 私表。</p>
     */
    public List<UserDirectoryDTO> listByOrg(String orgCode) {
        if (orgCode == null || orgCode.isBlank()) {
            return Collections.emptyList();
        }
        List<UserDirectoryDTO> result = new ArrayList<>();
        int pageNo = 1;
        long total = Long.MAX_VALUE;
        while (result.size() < total) {
            PageResult<UserDirectoryDTO> page = userDirectoryApi.pageActiveUsers(null, orgCode.trim(), pageNo, MAX_PAGE_SIZE);
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                break;
            }
            result.addAll(page.getRecords());
            total = page.getTotal();
            if (result.size() >= total || page.getRecords().size() < MAX_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        return result;
    }

    /** 按用户查询负责产品 ID；用于 REST 详情和列表回填。 */
    public List<String> listProductIdsByUserId(String userId) {
        List<String> productIds = userProductRelationService.listProductIdsByUserId(userId);
        return productIds == null ? Collections.emptyList() : productIds;
    }

    /** 批量查询用户负责产品 ID，供列表一次性回填，避免逐用户 N+1。 */
    public Map<String, List<String>> mapProductIdsByUserIds(Collection<String> userIds) {
        Map<String, List<String>> productIds = userProductRelationService.mapProductIdsByUserIds(userIds);
        return productIds == null ? Collections.emptyMap() : productIds;
    }

    /** 按产品 ID 批量查询产品简要信息。 */
    public List<ProductInfo> listProductsByIds(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProductInfo> products = productInfoMapper.listByIds(productIds);
        return products == null ? Collections.emptyList() : products;
    }

    /**
     * 当前用户自助维护电话、邮箱和负责产品。
     *
     * <p>不接收目标用户 ID，用户身份由 UserDirectoryApi 的认证上下文确定。产品关系
     * 变更前校验产品存在、启用且支持中场支持，随后使用关系服务整组替换。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDirectoryDTO updateCurrentUser(EmployeeSelfUpdateReqDTO req) {
        if (req == null) {
            throw new IllegalArgumentException("通讯录更新请求不能为空");
        }
        String operator = currentUserApi.getCurrentEmpId();
        requireCurrentUser(operator);
        UserDirectoryDTO current = userDirectoryApi.getEmployee(operator);
        if (current == null) {
            throw employeeNotFound();
        }
        List<String> productIds = req.getResponsibleProductIds() == null
                ? null : normalizeIds(req.getResponsibleProductIds());
        if (productIds != null) {
            validateResponsibleProducts(productIds);
        }

        // DTO 为部分更新语义：省略联系方式时保留现值，空字符串才表示清空。
        String mobile = req.getMobile() == null ? current.getMobile() : req.getMobile();
        String email = req.getEmail() == null ? current.getEmail() : req.getEmail();
        UserDirectoryDTO updated = userDirectoryApi.updateCurrentUserContact(mobile, email);
        if (productIds != null) {
            userProductRelationService.replaceProductsForUser(operator, productIds, operator);
        }

        safeAuditLog(AuditLogCmd.builder()
                .traceId(MdcUtils.getTraceId())
                .empId(operator)
                .bizType("EMPLOYEE")
                .bizAction("EDIT_SELF")
                .resourceUrl("/api/employees/me")
                .requestMethod("PUT")
                .requestParams("changedFields=[mobile,email,responsibleProductIds]")
                .responseStatus(200)
                .build());
        log.info("[AddressBookService.updateCurrentUser] operator={}, productCount={}",
                operator, productIds == null ? "unchanged" : productIds.size());
        return updated;
    }

    /** 兼容 service 层旧命名，仍然只能更新当前登录用户。 */
    public UserDirectoryDTO updateMyself(EmployeeSelfUpdateReqDTO req) {
        return updateCurrentUser(req);
    }

    private void validateResponsibleProducts(List<String> productIds) {
        if (productIds.size() > MAX_RESPONSIBLE_PRODUCTS) {
            throw new BizException(PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode(),
                    PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getMessage());
        }
        if (productIds.isEmpty()) {
            return;
        }
        List<ProductInfo> products = productInfoMapper.listByIds(productIds);
        if (products == null || products.size() != productIds.size()) {
            throw new BizException(PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode(), "部分产品不存在");
        }
        Set<String> requested = new LinkedHashSet<>(productIds);
        Set<String> found = new LinkedHashSet<>();
        for (ProductInfo product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            found.add(product.getId());
            if (!"ACTIVE".equals(product.getStatus())
                    || !Boolean.TRUE.equals(product.getSupportForSupportRequest())) {
                throw new BizException(PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode(),
                        "产品 " + product.getProductCode() + " 不可选（非 ACTIVE 或不支持中场支持）");
            }
        }
        if (!found.equals(requested)) {
            throw new BizException(PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode(), "部分产品不存在");
        }
    }

    private static List<String> normalizeIds(Collection<String> ids) {
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

    private void requireCurrentUser(String operator) {
        if (operator == null || operator.isBlank()) {
            throw employeeNotFound();
        }
    }

    private static BizException employeeNotFound() {
        return new BizException(PortalErrorCode.EMPLOYEE_NOT_FOUND.getCode(),
                PortalErrorCode.EMPLOYEE_NOT_FOUND.getMessage());
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private void safeAuditLog(AuditLogCmd cmd) {
        try {
            auditApi.log(cmd);
        } catch (Exception ex) {
            log.warn("[AddressBookService] audit log failed, action={}", cmd.getBizAction(), ex);
        }
    }
}
