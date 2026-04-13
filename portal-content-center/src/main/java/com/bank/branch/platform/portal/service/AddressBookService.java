package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeUpdateReqDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.event.AddrbookUpdatedEvent;
import com.bank.branch.platform.portal.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 通讯录域核心 Service
 *
 * <p>提供通讯录员工的分页列表、详情查询、编辑、模糊搜索、按机构查询等能力。
 * 编辑操作包含权限校验、产品数量限制、双向同步产品负责人、领域事件发布。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressBookService {

    private final AddrbookEmployeeMapper addrbookEmployeeMapper;
    private final ProductInfoMapper productInfoMapper;
    private final CurrentUserApi currentUserApi;
    private final ApplicationEventPublisher eventPublisher;

    /** 单员工负责产品数量上限 */
    private static final int MAX_RESPONSIBLE_PRODUCTS = 20;

    /**
     * C.1 分页查询通讯录员工列表
     *
     * @param req 查询请求（含关键词、机构、状态、分页参数）
     * @return 分页结果
     */
    public PageResult<AddrbookEmployee> listEmployees(EmployeeQueryReqDTO req) {
        int pageNo = req.getPageNo() != null ? req.getPageNo() : 1;
        int pageSize = req.getPageSize() != null ? req.getPageSize() : 20;
        int offset = (pageNo - 1) * pageSize;

        long total = addrbookEmployeeMapper.countPage(req.getKeyword(), req.getOrgCode(), req.getStatus());
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        List<AddrbookEmployee> records = addrbookEmployeeMapper.selectPage(
                req.getKeyword(), req.getOrgCode(), req.getStatus(), offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * C.2 查询员工详情
     *
     * @param empId 员工工号
     * @return 员工实体
     * @throws BizException EMPLOYEE_NOT_FOUND 员工不存在
     */
    public AddrbookEmployee getEmployee(String empId) {
        AddrbookEmployee entity = addrbookEmployeeMapper.selectByEmpId(empId);
        if (entity == null) {
            throw new BizException(
                    PortalErrorCode.EMPLOYEE_NOT_FOUND.getCode(),
                    PortalErrorCode.EMPLOYEE_NOT_FOUND.getMessage());
        }
        return entity;
    }

    /**
     * C.3 编辑员工通讯录信息
     *
     * <p>业务规则：
     * <ul>
     *   <li>目标员工必须存在且 ACTIVE</li>
     *   <li>仅本人或同机构负责人可编辑（V1 简化：同机构即可）</li>
     *   <li>负责产品数量不超过 20</li>
     *   <li>负责产品必须 ACTIVE 且 support_for_support_request=true</li>
     *   <li>产品变更时双向同步 product_info.responsible_emp_ids</li>
     *   <li>发布 AddrbookUpdatedEvent + ProductResponsibleUpdatedEvent</li>
     * </ul></p>
     *
     * @param targetEmpId 目标员工工号
     * @param req         更新请求
     * @throws BizException EMPLOYEE_NOT_FOUND / EMPLOYEE_RESIGNED / NO_RIGHT_TO_EDIT_OTHER / PRODUCT_RESPONSIBLE_LIMIT
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEmployee(String targetEmpId, EmployeeUpdateReqDTO req) {
        String operatorEmpId = currentUserApi.getCurrentEmpId();

        // 1. 查询目标员工
        AddrbookEmployee target = addrbookEmployeeMapper.selectByEmpId(targetEmpId);
        if (target == null) {
            throw new BizException(
                    PortalErrorCode.EMPLOYEE_NOT_FOUND.getCode(),
                    PortalErrorCode.EMPLOYEE_NOT_FOUND.getMessage());
        }

        // 2. 检查员工状态
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new BizException(
                    PortalErrorCode.EMPLOYEE_RESIGNED.getCode(),
                    PortalErrorCode.EMPLOYEE_RESIGNED.getMessage());
        }

        // 3. 权限校验：本人可编辑，否则需同机构（V1 简化）
        if (!operatorEmpId.equals(targetEmpId)) {
            String operatorOrgCode = currentUserApi.getCurrentOrgCode();
            if (!Objects.equals(operatorOrgCode, target.getOrgCode())) {
                throw new BizException(
                        PortalErrorCode.NO_RIGHT_TO_EDIT_OTHER.getCode(),
                        PortalErrorCode.NO_RIGHT_TO_EDIT_OTHER.getMessage());
            }
        }

        // 4. 负责产品校验
        List<String> newProductIds = req.getResponsibleProductIds();
        boolean productChanged = false;
        List<String> oldProductIds = target.getResponsibleProductIds() != null
                ? target.getResponsibleProductIds() : Collections.emptyList();

        if (newProductIds != null) {
            // 4a. 数量上限
            if (newProductIds.size() > MAX_RESPONSIBLE_PRODUCTS) {
                throw new BizException(
                        PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode(),
                        PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getMessage());
            }
            // 4b. 产品有效性
            if (!newProductIds.isEmpty()) {
                List<ProductInfo> products = productInfoMapper.listByIds(newProductIds);
                if (products.size() != newProductIds.size()) {
                    throw new BizException(
                            PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode(),
                            "部分产品不存在");
                }
                for (ProductInfo p : products) {
                    if (!"ACTIVE".equals(p.getStatus()) || !Boolean.TRUE.equals(p.getSupportForSupportRequest())) {
                        throw new BizException(
                                PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode(),
                                "产品 " + p.getProductCode() + " 不可选（非 ACTIVE 或不支持中场支持）");
                    }
                }
            }
            // 4c. 检查是否真正变更
            productChanged = !new HashSet<>(oldProductIds).equals(new HashSet<>(newProductIds));
        }

        // 5. 构建更新实体
        AddrbookEmployee patch = new AddrbookEmployee();
        patch.setEmpId(targetEmpId);
        List<String> changedFields = new ArrayList<>();

        if (req.getMobile() != null) {
            patch.setMobile(req.getMobile());
            changedFields.add("mobile");
        }
        if (req.getEmail() != null) {
            patch.setEmail(req.getEmail());
            changedFields.add("email");
        }
        if (req.getPosition() != null) {
            patch.setPosition(req.getPosition());
            changedFields.add("position");
        }
        if (req.getSelfDesc() != null) {
            patch.setSelfDesc(req.getSelfDesc());
            changedFields.add("selfDesc");
        }
        if (newProductIds != null) {
            patch.setResponsibleProductIds(newProductIds);
            changedFields.add("responsibleProductIds");
        }
        patch.setMaintainerEmpId(operatorEmpId);

        // 6. 执行更新
        addrbookEmployeeMapper.updateFields(patch);

        // 7. 双向同步产品负责人
        if (productChanged) {
            syncProductResponsible(targetEmpId, oldProductIds, newProductIds, operatorEmpId);
        }

        // 8. 发布通讯录更新事件
        eventPublisher.publishEvent(new AddrbookUpdatedEvent(
                targetEmpId, changedFields, operatorEmpId, LocalDateTime.now()));

        log.info("[AddressBookService.updateEmployee] empId={}, changedFields={}, operator={}",
                targetEmpId, changedFields, operatorEmpId);
    }

    /**
     * C.4 模糊搜索员工（员工选择器）
     *
     * @param keyword 搜索关键词
     * @param limit   最大返回数量
     * @return 匹配的员工列表
     */
    public List<AddrbookEmployee> searchEmployees(String keyword, int limit) {
        return addrbookEmployeeMapper.searchByKeyword(keyword, limit);
    }

    /**
     * 按机构查询员工列表
     *
     * @param orgCode 机构代码
     * @return 该机构下的员工列表
     */
    public List<AddrbookEmployee> listByOrg(String orgCode) {
        return addrbookEmployeeMapper.selectByOrgCode(orgCode);
    }

    /**
     * 双向同步产品负责人：将员工从旧产品中移除、加入新产品
     *
     * @param empId         员工工号
     * @param oldProductIds 旧负责产品列表
     * @param newProductIds 新负责产品列表
     * @param operatorEmpId 操作人工号
     */
    private void syncProductResponsible(String empId, List<String> oldProductIds,
                                        List<String> newProductIds, String operatorEmpId) {
        Set<String> oldSet = new HashSet<>(oldProductIds);
        Set<String> newSet = new HashSet<>(newProductIds);

        // 需要移除 empId 的产品（旧有但新没有）
        Set<String> removed = new HashSet<>(oldSet);
        removed.removeAll(newSet);

        // 需要新增 empId 的产品（新有但旧没有）
        Set<String> added = new HashSet<>(newSet);
        added.removeAll(oldSet);

        // 对所有变更的产品进行同步
        Set<String> allChanged = new HashSet<>();
        allChanged.addAll(removed);
        allChanged.addAll(added);

        for (String productId : allChanged) {
            ProductInfo product = productInfoMapper.selectById(productId);
            if (product == null) {
                log.warn("[syncProductResponsible] skip missing productId={}", productId);
                continue;
            }
            List<String> empIds = product.getResponsibleEmpIds() != null
                    ? new ArrayList<>(product.getResponsibleEmpIds()) : new ArrayList<>();
            List<String> beforeEmpIds = new ArrayList<>(empIds);

            if (added.contains(productId)) {
                // 新增 empId 到产品
                if (!empIds.contains(empId)) {
                    empIds.add(empId);
                }
            } else {
                // 从产品移除 empId
                empIds.remove(empId);
            }

            // 更新产品
            ProductInfo patch = new ProductInfo();
            patch.setId(productId);
            patch.setResponsibleEmpIds(empIds);
            patch.setUpdatedBy(operatorEmpId);
            productInfoMapper.updateById(patch);

            // 发布产品负责人变更事件
            eventPublisher.publishEvent(new ProductResponsibleUpdatedEvent(
                    productId, product.getProductCode(),
                    beforeEmpIds, empIds,
                    "ADDRBOOK_SIDE", operatorEmpId, LocalDateTime.now()));
        }
    }
}
