package com.bank.branch.platform.customer.service.marketing;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerOwnershipRestoreRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerTransferRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTransferLog;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTransferTarget;
import com.bank.branch.platform.customer.enums.CustomerRoleCode;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTransferLogMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTransferTargetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 营销客户主办权转交服务。
 *
 * <p>TRANSFER 只能指定一名有效客户经理；UNASSIGN 只能由管理员执行。
 * 变更和转交审计在同一事务中完成，主档更新使用 lock_version CAS。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOwnershipService {

    private static final String INVALID_REQUEST = "CUST-40000";
    private static final String ACCESS_FORBIDDEN = "CUST-40310";
    private static final String CONFLICT = "CUST-40902";
    private static final String SNAPSHOT_UNAVAILABLE = "CUST-50301";

    private final MarketingCustomerInfoMapper customerMapper;
    private final MarketingCustomerTransferLogMapper transferLogMapper;
    private final MarketingCustomerTransferTargetMapper transferTargetMapper;
    private final UserApi userApi;

    /**
     * 执行客户主办权指定、转交或取消。
     *
     * @param customerId 客户主档 ID
     * @param request 转交请求
     * @param operatorEmpId 当前员工工号
     * @param operatorOrgId 当前员工主机构编码
     * @param operatorAdmin 是否具备营销管理员能力
     */
    @Transactional
    @AuditLog(action = "TRANSFER", resourceType = "MARKETING_CUSTOMER", reasonRequired = true)
    public void transfer(Long customerId, MarketingCustomerTransferRequest request,
                         String operatorEmpId, String operatorOrgId, boolean operatorAdmin) {
        MarketingCustomerInfo customer = requireActive(customerId);
        validateRequest(request);
        if (!sameVersion(request.getLockVersion(), customer.getLockVersion())) {
            throw new BizException(CONFLICT, "客户主办权已变化，请刷新后重新操作");
        }

        String action = request.getTransferAction().trim().toUpperCase();
        if (!operatorAdmin && !StringUtils.hasText(customer.getMainManagerId())) {
            throw new BizException(ACCESS_FORBIDDEN, "仅当前主办客户经理或营销管理员可以转交");
        }
        if (!operatorAdmin && !operatorEmpId.equals(customer.getMainManagerId())) {
            throw new BizException(ACCESS_FORBIDDEN, "仅当前主办客户经理可以转交");
        }
        if ("UNASSIGN".equals(action)) {
            if (!operatorAdmin) {
                throw new BizException(ACCESS_FORBIDDEN, "只有营销管理员可以取消主办");
            }
            if (StringUtils.hasText(request.getTargetManagerId())) {
                throw new BizException(INVALID_REQUEST, "取消主办时不能填写接收客户经理");
            }
            persistOwnershipChange(customer, request, operatorEmpId, null, null, "UNASSIGNED", action);
            return;
        }
        if (!"ASSIGN".equals(action) && !"TRANSFER".equals(action)) {
            throw new BizException(INVALID_REQUEST, "主办操作仅支持 ASSIGN、TRANSFER 或 UNASSIGN");
        }
        if (!StringUtils.hasText(request.getTargetManagerId())) {
            throw new BizException(INVALID_REQUEST, "请选择接收客户经理");
        }
        if (!operatorAdmin && "ASSIGN".equals(action)) {
            throw new BizException(ACCESS_FORBIDDEN, "客户经理只能转交本人主办客户");
        }
        if (request.getTargetManagerId().equals(customer.getMainManagerId())) {
            throw new BizException("CUST-40904", "接收客户经理不能包含当前主办客户经理");
        }
        UserDTO target = requireCustomerManager(request.getTargetManagerId());
        persistOwnershipChange(customer, request, operatorEmpId, target.getEmpId(),
                target.getMainOrgCode(), "ASSIGNED", action);
    }

    /**
     * 恢复 AUTO 主办同步的入口。
     * 当前工程尚未接入外部每日客户经理全量快照，因此显式返回不可用错误，
     * 防止误将 MANUAL 客户切回 AUTO 后没有真实快照可刷新。
     */
    @Transactional(readOnly = true)
    @AuditLog(action = "RESTORE_OWNER_AUTO", resourceType = "MARKETING_CUSTOMER", reasonRequired = true)
    public void restoreAuto(Long customerId, MarketingCustomerOwnershipRestoreRequest request,
                            String operatorEmpId, String operatorOrgId, boolean operatorAdmin) {
        if (!operatorAdmin) {
            throw new BizException(ACCESS_FORBIDDEN, "只有营销管理员可以恢复自动同步");
        }
        MarketingCustomerInfo customer = requireActive(customerId);
        if (request == null || !StringUtils.hasText(request.getReason()) || request.getLockVersion() == null) {
            throw new BizException(INVALID_REQUEST, "锁版本和恢复原因不能为空");
        }
        if (!sameVersion(request.getLockVersion(), customer.getLockVersion())) {
            throw new BizException(CONFLICT, "客户主办权已变化，请刷新后重新操作");
        }
        throw new BizException(SNAPSHOT_UNAVAILABLE,
                "外部客户经理关系全量快照适配尚未配置，暂不能恢复自动同步");
    }

    private MarketingCustomerInfo requireActive(Long customerId) {
        MarketingCustomerInfo customer = customerMapper.selectActiveById(customerId);
        if (customer == null) {
            throw new BizException("CUST-40403", "客户不存在");
        }
        return customer;
    }

    private void validateRequest(MarketingCustomerTransferRequest request) {
        if (request == null || !StringUtils.hasText(request.getTransferAction())
                || !StringUtils.hasText(request.getReason()) || request.getLockVersion() == null) {
            throw new BizException(INVALID_REQUEST, "主办操作、锁版本和转交原因不能为空");
        }
    }

    private UserDTO requireCustomerManager(String empId) {
        if (!StringUtils.hasText(empId)) {
            throw new BizException("CUST-40013", "至少选择一名接收客户经理");
        }
        if (!CustomerRoleCode.isCustomerManager(userApi.getUserRoleCodes(empId))) {
            throw new BizException("CUST-40306", "转交接收人角色不符");
        }
        UserDTO user = userApi.getUserByEmpId(empId);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())
                || !StringUtils.hasText(user.getMainOrgCode())) {
            throw new BizException("CUST-40904", "转交接收人不存在或已停用");
        }
        return user;
    }

    private void persistOwnershipChange(MarketingCustomerInfo customer,
                                        MarketingCustomerTransferRequest request,
                                        String operatorEmpId,
                                        String targetManagerId,
                                        String targetOrgId,
                                        String ownershipStatus,
                                        String action) {
        LocalDateTime now = LocalDateTime.now();
        int affected = customerMapper.updateOwnershipByLockVersion(customer.getId(), request.getLockVersion(),
                targetManagerId, targetOrgId, ownershipStatus, "MANUAL", operatorEmpId, now,
                request.getReason().trim());
        if (affected != 1) {
            throw new BizException(CONFLICT, "客户主办权已变化，请刷新后重新操作");
        }

        MarketingCustomerTransferLog transferLog = new MarketingCustomerTransferLog();
        transferLog.setTransferNo("MTR-" + UUID.randomUUID().toString().replace("-", ""));
        transferLog.setCustId(customer.getId());
        transferLog.setTransferAction(action);
        transferLog.setFromManagerId(customer.getMainManagerId());
        transferLog.setFromOrgId(customer.getMainOrgId());
        transferLog.setPrimaryToManagerId(targetManagerId);
        transferLog.setPrimaryToOrgId(targetOrgId);
        transferLog.setAccountOpenedSnapshot(customer.getIsAccountOpened());
        transferLog.setTransferSource("ADMIN");
        transferLog.setReason(request.getReason().trim());
        transferLog.setStatus("COMPLETED");
        transferLog.setOperatorEmpId(operatorEmpId);
        transferLog.setCompletedTime(now);
        transferLog.setCreatedTime(now);
        transferLog.setUpdatedTime(now);
        if (transferLogMapper.insert(transferLog) != 1) {
            throw new BizException("CUST-50001", "保存主办变更记录失败");
        }

        if (!"UNASSIGN".equals(action)) {
            MarketingCustomerTransferTarget target = new MarketingCustomerTransferTarget();
            target.setTransferId(transferLog.getId());
            target.setTargetEmpId(targetManagerId);
            target.setTargetOrgId(targetOrgId);
            target.setTargetRole("PRIMARY");
            target.setSortNo(0);
            target.setCreatedBy(operatorEmpId);
            target.setCreatedTime(now);
            if (transferTargetMapper.insert(target) != 1) {
                throw new BizException("CUST-50001", "保存主办接收人记录失败");
            }
        }
        log.info("[CustomerOwnershipService] 主办权变更完成 custId={}, action={}, operator={}",
                customer.getId(), action, operatorEmpId);
    }

    private boolean sameVersion(Integer expected, Integer actual) {
        return (expected == null ? 0 : expected) == (actual == null ? 0 : actual);
    }
}
