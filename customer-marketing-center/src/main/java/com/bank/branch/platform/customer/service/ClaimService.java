package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.event.ClaimCancelledEvent;
import com.bank.branch.platform.customer.event.ClaimCreatedEvent;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 客户认领业务服务。
 * <p>
 * 负责客户认领的创建（争抢式）、取消以及个人认领列表查询。
 * 认领依赖 uk(cust_id, org_id) 唯一索引做数据库级别的并发安全保障，
 * 并发抢认领时通过 catch {@link DuplicateKeyException} 转为 CUSTOMER_ALREADY_CLAIMED 业务异常。
 * 认领成功后发布 {@link ClaimCreatedEvent}，取消后发布 {@link ClaimCancelledEvent}，
 * 由下游服务（如触达任务）监听处理。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final CustClaimMapper claimMapper;
    private final CustMasterMapper masterMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 认领客户（争抢式）。
     * <p>
     * 先校验客户存在，然后 INSERT 认领记录。
     * 若唯一索引 uk(cust_id, org_id) 冲突（并发认领 / 重复认领），
     * 捕获 {@link DuplicateKeyException} 并抛出 CUSTOMER_ALREADY_CLAIMED 业务异常。
     * 认领时 maintainerEmpId 默认等于 claimedBy。
     * </p>
     *
     * @param custId 客户ID
     * @param orgId  认领机构代码
     * @param empId  认领人员工工号
     * @return 认领成功后的认领记录实体
     */
    @Transactional
    public CustClaim claim(String custId, String orgId, String empId) {
        log.info("[ClaimService.claim] custId={}, orgId={}, empId={}", custId, orgId, empId);

        // 先检查客户存在，不存在则抛出 CUSTOMER_NOT_FOUND
        CustMaster customer = masterMapper.selectById(custId);
        if (customer == null) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        CustClaim entity = new CustClaim();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setCustId(custId);
        entity.setOrgId(orgId);
        entity.setClaimedBy(empId);
        // 认领时 maintainerEmpId 默认等于 claimedBy
        entity.setMaintainerEmpId(empId);
        entity.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        entity.setClaimTime(now);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);

        try {
            claimMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突 = 该机构已认领此客户
            log.warn("[ClaimService.claim] duplicate claim detected, custId={}, orgId={}", custId, orgId);
            throw new BizException(CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getCode(),
                    CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getMessage());
        }

        // 发布认领成功事件，通知下游（如触达任务）
        eventPublisher.publishEvent(new ClaimCreatedEvent(
                entity.getId(), custId, orgId, empId, empId));

        log.info("[ClaimService.claim] claim created, claimId={}", entity.getId());
        return entity;
    }

    /**
     * 取消认领（高危操作）。
     * <p>
     * 取消原因 reason 为必填，缺失时抛出 CANCEL_REASON_REQUIRED。
     * 认领记录不存在时抛出 CLAIM_NOT_FOUND。
     * 更新 claimStatus=CANCELLED，同时设置 cancelTime 和 cancelReason。
     * 取消后发布 {@link ClaimCancelledEvent} 事件。
     * </p>
     *
     * @param claimId       认领记录 ID
     * @param reason        取消原因（必填）
     * @param operatorEmpId 操作人员工工号
     */
    @Transactional
    public void cancelClaim(String claimId, String reason, String operatorEmpId) {
        log.info("[ClaimService.cancelClaim] claimId={}, operator={}", claimId, operatorEmpId);

        // 取消原因必填 — 先做前置校验，避免无效查询
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.CANCEL_REASON_REQUIRED.getCode(),
                    CustomerErrorCode.CANCEL_REASON_REQUIRED.getMessage());
        }

        // 查询认领记录，不存在则抛 CLAIM_NOT_FOUND
        CustClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                    CustomerErrorCode.CLAIM_NOT_FOUND.getMessage());
        }

        // 更新认领状态为已取消，同时设置取消时间和原因
        LocalDateTime now = LocalDateTime.now();
        CustClaim updateEntity = new CustClaim();
        updateEntity.setId(claimId);
        updateEntity.setClaimStatus(ClaimStatus.CANCELLED.getCode());
        updateEntity.setCancelTime(now);
        updateEntity.setCancelReason(reason);
        updateEntity.setUpdatedTime(now);

        claimMapper.updateById(updateEntity);

        // 发布取消事件，通知下游（如清理相关触达任务）
        eventPublisher.publishEvent(new ClaimCancelledEvent(
                claimId, claim.getCustId(), claim.getOrgId(), reason, operatorEmpId));

        log.info("[ClaimService.cancelClaim] claim cancelled, claimId={}", claimId);
    }

    /**
     * 分页查询我的认领列表。
     * <p>
     * 查询当前员工有效认领的客户（claimStatus=CLAIMED）。
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param empId    员工工号
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页的认领记录列表
     */
    public PageResult<CustClaim> listMyClaims(String empId, int pageNo, int pageSize) {
        log.info("[ClaimService.listMyClaims] empId={}, pageNo={}, pageSize={}", empId, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<CustClaim> records = claimMapper.selectMyClaimsPage(empId, offset, pageSize);
        long total = claimMapper.countMyClaimsPage(empId);

        log.info("[ClaimService.listMyClaims] empId={}, total={}", empId, total);
        return PageResult.of(pageNo, pageSize, total, records);
    }
}
