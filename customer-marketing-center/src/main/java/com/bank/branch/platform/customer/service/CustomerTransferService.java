package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.CustomerTransferRespDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTransferLog;
import com.bank.branch.platform.customer.entity.CustTransferTarget;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.CustomerRoleCode;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTransferLogMapper;
import com.bank.branch.platform.customer.mapper.CustTransferTargetMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 客户主办关系转交服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerTransferService {

    private final CustMasterMapper masterMapper;
    private final CustTransferLogMapper transferLogMapper;
    private final CustTransferTargetMapper transferTargetMapper;
    private final TouchTaskMapper touchTaskMapper;
    private final TouchTaskService touchTaskService;
    private final UserApi userApi;

    /**
     * 完成客户转交：首位接收人为新主办，关闭旧触达任务，保存完整快照并生成新任务。
     */
    @Transactional
    public String transfer(String custId, List<String> targetEmpIds, String reason, String operatorEmpId) {
        return transfer(custId, targetEmpIds, reason, operatorEmpId, null, true);
    }

    /** 带当前用户组织范围二次校验的客户转交入口。 */
    @Transactional
    public String transfer(String custId, List<String> targetEmpIds, String reason, String operatorEmpId,
                           String operatorOrgId, boolean systemAdmin) {
        CustMaster customer = masterMapper.selectById(custId);
        if (customer == null) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.TRANSFER_REASON_REQUIRED.getCode(),
                    CustomerErrorCode.TRANSFER_REASON_REQUIRED.getMessage());
        }
        if (!systemAdmin && !java.util.Objects.equals(operatorEmpId, customer.getMainManagerId())
                && !java.util.Objects.equals(operatorOrgId, customer.getMainOrgId())) {
            throw new BizException(CustomerErrorCode.TRANSFER_ACCESS_FORBIDDEN.getCode(),
                    CustomerErrorCode.TRANSFER_ACCESS_FORBIDDEN.getMessage());
        }
        List<String> targets = targetEmpIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(targetEmpIds.stream().filter(StringUtils::hasText).toList()));
        if (targets.isEmpty()) {
            throw new BizException(CustomerErrorCode.TRANSFER_TARGET_REQUIRED.getCode(),
                    CustomerErrorCode.TRANSFER_TARGET_REQUIRED.getMessage());
        }
        if (targets.contains(customer.getMainManagerId())) {
            throw new BizException(CustomerErrorCode.TRANSFER_TARGET_CURRENT_MANAGER.getCode(),
                    CustomerErrorCode.TRANSFER_TARGET_CURRENT_MANAGER.getMessage());
        }

        List<UserDTO> receivers = new ArrayList<>();
        for (String empId : targets) {
            Set<String> roles = userApi.getUserRoleCodes(empId);
            UserDTO user = userApi.getUserByEmpId(empId);
            if (!CustomerRoleCode.isCustomerManager(roles) || user == null || Boolean.FALSE.equals(user.getEnabled())
                    || !StringUtils.hasText(user.getMainOrgCode())) {
                throw new BizException(CustomerErrorCode.TRANSFER_ROLE_MISMATCH.getCode(),
                        CustomerErrorCode.TRANSFER_ROLE_MISMATCH.getMessage() + "：" + empId);
            }
            receivers.add(user);
        }

        LocalDateTime now = LocalDateTime.now();
        String transferId = UUID.randomUUID().toString().replace("-", "");
        UserDTO primary = receivers.get(0);
        CustTransferLog logEntity = new CustTransferLog();
        logEntity.setId(transferId);
        logEntity.setTransferNo("TRANSFER" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + transferId.substring(0, 4).toUpperCase());
        logEntity.setCustId(custId);
        logEntity.setFromManagerId(customer.getMainManagerId());
        logEntity.setFromOrgId(customer.getMainOrgId());
        logEntity.setPrimaryToManagerId(primary.getEmpId());
        logEntity.setPrimaryToOrgId(primary.getMainOrgCode());
        logEntity.setAccountOpenedSnapshot(customer.getIsAccountOpened() == null ? 0 : customer.getIsAccountOpened());
        logEntity.setReason(reason.trim());
        logEntity.setStatus("COMPLETED");
        logEntity.setOperatorEmpId(operatorEmpId);
        logEntity.setCompletedTime(now);
        logEntity.setCreatedTime(now);
        logEntity.setUpdatedTime(now);
        transferLogMapper.insert(logEntity);

        List<CustTransferTarget> targetEntities = new ArrayList<>();
        for (int i = 0; i < receivers.size(); i++) {
            UserDTO receiver = receivers.get(i);
            CustTransferTarget target = new CustTransferTarget();
            target.setId(UUID.randomUUID().toString().replace("-", ""));
            target.setTransferId(transferId);
            target.setTargetEmpId(receiver.getEmpId());
            target.setTargetOrgId(receiver.getMainOrgCode());
            target.setTargetRole(i == 0 ? "PRIMARY" : "CO_MANAGER");
            target.setSortNo(i);
            target.setCreatedTime(now);
            targetEntities.add(target);
        }
        transferTargetMapper.insertBatch(targetEntities);

        // 先关闭旧机构任务，再把客户主办快照切到新机构，保证日志权限按任务原机构保留。
        touchTaskMapper.cancelActiveByCust(custId, now, "客户转交", operatorEmpId);
        CustMaster update = new CustMaster();
        update.setId(custId);
        update.setMainManagerId(primary.getEmpId());
        update.setMainOrgId(primary.getMainOrgCode());
        update.setOwnershipStatus("ASSIGNED");
        update.setUpdatedTime(now);
        masterMapper.updateById(update);
        touchTaskService.createFirstTouchTask(custId, primary.getMainOrgCode(), primary.getEmpId(), null);
        log.info("[CustomerTransferService] 客户转交完成 custId={}, from={}, to={}, transferId={}",
                custId, customer.getMainManagerId(), primary.getEmpId(), transferId);
        return transferId;
    }

    /** 查询当前机构可见的转交记录，系统管理员可查看全量。 */
    public List<CustomerTransferRespDTO> list(String keyword, String orgId, boolean allData) {
        List<CustTransferLog> logs = transferLogMapper.selectVisible(keyword, orgId, allData);
        if (logs == null || logs.isEmpty()) return List.of();
        List<String> ids = logs.stream().map(CustTransferLog::getId).toList();
        List<CustTransferTarget> targets = transferTargetMapper.selectByTransferIds(ids);
        if (targets == null) targets = List.of();
        Map<String, List<CustTransferTarget>> grouped = targets.stream()
                .collect(Collectors.groupingBy(CustTransferTarget::getTransferId));

        Set<String> custIds = logs.stream().map(CustTransferLog::getCustId).collect(Collectors.toSet());
        Map<String, CustMaster> customers = masterMapper.selectByIds(new ArrayList<>(custIds)).stream()
                .collect(Collectors.toMap(CustMaster::getId, Function.identity()));
        Set<String> empIds = new LinkedHashSet<>();
        logs.forEach(item -> { empIds.add(item.getFromManagerId()); empIds.add(item.getOperatorEmpId()); });
        targets.forEach(item -> empIds.add(item.getTargetEmpId()));
        empIds.removeIf(id -> !StringUtils.hasText(id));
        List<UserDTO> userList = userApi.getUserByEmpIds(new ArrayList<>(empIds));
        if (userList == null) userList = List.of();
        Map<String, UserDTO> users = userList.stream()
                .collect(Collectors.toMap(UserDTO::getEmpId, Function.identity(), (a, b) -> a));

        return logs.stream().map(item -> toResp(item, grouped.getOrDefault(item.getId(), List.of()),
                customers.get(item.getCustId()), users)).toList();
    }

    /** 查询可作为接收人的启用客户经理。 */
    public List<UserDTO> candidates(String keyword) {
        PageResult<UserDTO> users = userApi.pageUsers(keyword, 1, 50);
        if (users == null || users.getRecords() == null) return List.of();
        return users.getRecords().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> {
                    Set<String> roles = userApi.getUserRoleCodes(user.getEmpId());
                    return CustomerRoleCode.isCustomerManager(roles);
                })
                .toList();
    }

    private CustomerTransferRespDTO toResp(CustTransferLog item, List<CustTransferTarget> targets,
                                           CustMaster customer, Map<String, UserDTO> users) {
        CustomerTransferRespDTO dto = new CustomerTransferRespDTO();
        org.springframework.beans.BeanUtils.copyProperties(item, dto);
        dto.setCustName(customer == null ? item.getCustId() : customer.getCustName());
        UserDTO from = users.get(item.getFromManagerId());
        dto.setFromManagerName(from == null ? item.getFromManagerId() : from.getDisplayName());
        dto.setFromOrgName(from == null ? item.getFromOrgId() : from.getMainOrgName());
        UserDTO operator = users.get(item.getOperatorEmpId());
        dto.setOperatorName(operator == null ? item.getOperatorEmpId() : operator.getDisplayName());
        dto.setTargets(targets.stream().map(target -> {
            CustomerTransferRespDTO.Target result = new CustomerTransferRespDTO.Target();
            UserDTO user = users.get(target.getTargetEmpId());
            result.setEmpId(target.getTargetEmpId());
            result.setEmpName(user == null ? target.getTargetEmpId() : user.getDisplayName());
            result.setOrgId(target.getTargetOrgId());
            result.setOrgName(user == null ? target.getTargetOrgId() : user.getMainOrgName());
            result.setRole(target.getTargetRole());
            result.setSortNo(target.getSortNo());
            return result;
        }).toList());
        return dto;
    }
}
