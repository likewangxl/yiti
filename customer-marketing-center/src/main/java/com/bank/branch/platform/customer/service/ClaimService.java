package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.ReTouchReqDTO;
import com.bank.branch.platform.customer.dto.resp.ClaimedCustomerRespDTO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.event.ClaimCancelledEvent;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.governance.api.DictApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 客户认领业务服务。
 * <p>
 * 负责客户认领的创建（争抢式）、取消以及个人认领列表查询。
 * 认领依赖 uk_cust_claim_emp(cust_id, claimed_by) 唯一索引做数据库级别的并发安全保障，
 * 并发抢认领时通过 catch {@link DuplicateKeyException} 转为 CUSTOMER_ALREADY_CLAIMED 业务异常。
 * 认领只建立员工与客户的关系；首次触达由员工在已认领客户页手动发起。
 * 取消后发布 {@link ClaimCancelledEvent}。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final CustClaimMapper claimMapper;
    private final CustMasterMapper masterMapper;
    private final TouchTaskMapper touchTaskMapper;
    private final TouchTaskService touchTaskService;
    private final ApplicationEventPublisher eventPublisher;
    private final DictApi dictApi;
    private final OrgApi orgApi;
    /** 目标 MARKETING_* 认领服务；旧实体路径仅作为兼容回退。 */
    private final MarketingCustomerClaimService marketingClaimService;

    /**
     * 认领客户（争抢式）。
     * <p>
     * 先校验客户存在，然后 INSERT 认领记录。
     * 若唯一索引 uk_cust_claim_emp(cust_id, claimed_by) 冲突（同一员工并发 / 重复认领），
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
            // 唯一索引冲突 = 当前员工已认领此客户
            log.warn("[ClaimService.claim] duplicate claim detected, custId={}, empId={}", custId, empId);
            throw new BizException(CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getCode(),
                    CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getMessage());
        }

        log.info("[ClaimService.claim] claim created, claimId={}", entity.getId());
        return entity;
    }

    /**
     * 按目标营销线索认领。线索 ID 是唯一业务维度，避免把客户主档 ID 当成防重键。
     *
     * @return 目标认领记录 ID 的字符串形式，兼容现有 REST 响应契约
     */
    @Transactional
    public String claimMarketingLead(Long sourceLeadId, String orgId, String empId) {
        if (marketingClaimService == null) {
            throw new BizException(CustomerErrorCode.INTERNAL_ERROR.getCode(), "目标营销认领服务未配置");
        }
        return String.valueOf(marketingClaimService.claim(sourceLeadId, orgId, empId).getId());
    }

    /**
     * 对本人有效认领关系手动发起首次触达。
     */
    @Transactional
    public TouchTask startTouch(String claimId, String planFinishTime,
                                String operatorEmpId, String operatorOrgCode) {
        if (marketingClaimService != null && isNumericId(claimId)) {
            return marketingClaimService.startTouch(claimId, planFinishTime, operatorEmpId, operatorOrgCode);
        }
        CustClaim claim = requireOwnedActiveClaim(claimId, operatorEmpId, operatorOrgCode);
        List<TouchTask> active = touchTaskMapper.selectActiveByCustAndAssignee(
                claim.getCustId(), claim.getMaintainerEmpId());
        if (!active.isEmpty()) {
            throw new BizException(CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode(),
                    CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getMessage());
        }
        return touchTaskService.createFirstTouchTask(
                claim.getCustId(), claim.getOrgId(), claim.getMaintainerEmpId(), planFinishTime);
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
    public void cancelClaim(String claimId, String reason, String operatorEmpId, String operatorOrgCode) {
        log.info("[ClaimService.cancelClaim] claimId={}, operator={}, orgCode={}",
                claimId, operatorEmpId, operatorOrgCode);

        // 取消原因必填 — 先做前置校验，避免无效查询
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.CANCEL_REASON_REQUIRED.getCode(),
                    CustomerErrorCode.CANCEL_REASON_REQUIRED.getMessage());
        }

        if (marketingClaimService != null && isNumericId(claimId)) {
            marketingClaimService.cancel(claimId, reason, operatorEmpId, operatorOrgCode);
            return;
        }

        // 查询认领记录，不存在则抛 CLAIM_NOT_FOUND
        CustClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                    CustomerErrorCode.CLAIM_NOT_FOUND.getMessage());
        }

        // P1C 2026-04-29：跨机构越权校验（CUST-40305）— 操作员只能取消本机构的认领
        if (!java.util.Objects.equals(claim.getOrgId(), operatorOrgCode)) {
            log.warn("[ClaimService.cancelClaim] 越权拒绝 CUST-40305 claimId={}, claimOrg={}, operatorOrg={}",
                    claimId, claim.getOrgId(), operatorOrgCode);
            throw new BizException(CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getCode(),
                    CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getMessage());
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

    /** 查询本人已认领客户，附带最近触达任务，供页面直接展示。 */
    public PageResult<ClaimedCustomerRespDTO> listMyClaimedCustomers(String empId, int pageNo, int pageSize) {
        return listMyClaimedCustomers(empId, null, null, null, pageNo, pageSize);
    }

    /** 查询目标已认领池；无目标服务时回退到旧兼容查询。 */
    public PageResult<ClaimedCustomerRespDTO> listMyClaimedCustomers(String empId, String tab,
                                                                       String keyword, String sourceType,
                                                                       int pageNo, int pageSize) {
        if (marketingClaimService != null) {
            return marketingClaimService.listClaimed(empId, tab, keyword, sourceType, pageNo, pageSize);
        }
        int offset = (pageNo - 1) * pageSize;
        List<ClaimedCustomerRespDTO> records = claimMapper.selectMyClaimedCustomerPage(empId, offset, pageSize);
        fillDisplayNames(records);
        return PageResult.of(pageNo, pageSize, claimMapper.countMyClaimsPage(empId), records);
    }

    /** 回填已认领客户列表的字典名称与来源机构名称。 */
    private void fillDisplayNames(List<ClaimedCustomerRespDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> orgCodes = records.stream()
                .map(ClaimedCustomerRespDTO::getOwnerOrgId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, String> orgNames = new HashMap<>();
        if (!orgCodes.isEmpty()) {
            List<OrgDTO> orgs = orgApi.getOrgsByCodes(orgCodes);
            if (orgs != null) {
                orgs.stream()
                        .filter(Objects::nonNull)
                        .filter(org -> StringUtils.hasText(org.getOrgCode()))
                        .forEach(org -> orgNames.put(org.getOrgCode(), org.getOrgName()));
            }
        }
        Map<String, String> industryNames = new HashMap<>();
        Map<String, String> customerTypeNames = new HashMap<>();
        records.forEach(row -> {
            String industry = row.getIndustry();
            if (StringUtils.hasText(industry)
                    && !industryNames.containsKey(industry)) {
                industryNames.put(industry, translatedLabel("INDUSTRY", industry));
            }
            String customerType = row.getCustomerType();
            if (StringUtils.hasText(customerType)
                    && !customerTypeNames.containsKey(customerType)) {
                customerTypeNames.put(customerType, translatedLabel("CUSTOMER_TYPE", customerType));
            }
            row.setIndustryName(industryNames.get(industry));
            row.setCustomerTypeName(customerTypeNames.get(customerType));
            row.setOwnerOrgName(orgNames.get(row.getOwnerOrgId()));
        });
    }

    private String translatedLabel(String dictType, String code) {
        String label = dictApi.getDictLabel(dictType, code);
        return StringUtils.hasText(label) && !code.equals(label) ? label : null;
    }

    /**
     * 对已认领客户重新发起一次触达（FOLLOW_UP 类型）。
     * <p>
     * 业务规则：
     * <ul>
     *   <li>认领关系必须存在（CUST-40404）</li>
     *   <li>认领归属机构必须等于当前操作员所在机构（CUST-40305）</li>
     *   <li>客户当前不存在 PENDING/IN_PROGRESS 触达任务（CUST-40908）</li>
     * </ul>
     * 通过校验后委托 {@link TouchTaskService#createFollowUpTask} 创建新任务，
     * 执行人取自 claim.maintainerEmpId（维护人继续触达，与认领时设定一致）。
     * </p>
     *
     * @param claimId          认领关系 ID
     * @param req              请求 DTO（包含 reason 必填；planFinishTime 仅兼容旧客户端且服务端忽略）
     * @param operatorEmpId    操作人员工工号（仅记录到日志）
     * @param operatorOrgCode  操作人所在机构代码（用于校验跨机构）
     * @return 新创建的 FOLLOW_UP 触达任务实体
     */
    @Transactional
    public TouchTask reTouch(String claimId, ReTouchReqDTO req, String operatorEmpId, String operatorOrgCode) {
        log.info("[ClaimService.reTouch] claimId={}, operator={}, orgCode={}", claimId, operatorEmpId, operatorOrgCode);

        if (marketingClaimService != null && isNumericId(claimId)) {
            return marketingClaimService.reTouch(claimId, req, operatorEmpId, operatorOrgCode);
        }

        CustClaim claim = requireOwnedActiveClaim(claimId, operatorEmpId, operatorOrgCode);

        // 当前客户不允许有任何在途触达（PENDING/IN_PROGRESS），由 mapper 自身查询条件保证
        List<TouchTask> active = touchTaskMapper.selectActiveByCustAndAssignee(
                claim.getCustId(), claim.getMaintainerEmpId());
        if (!active.isEmpty()) {
            log.warn("[ClaimService.reTouch] running touch task exists, custId={}, count={}",
                    claim.getCustId(), active.size());
            throw new BizException(CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode(),
                    CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getMessage());
        }

        TouchTask created = touchTaskService.createFollowUpTask(
                claim.getCustId(), claim.getOrgId(), claim.getMaintainerEmpId(),
                req.getReason(), req.getPlanFinishTime());

        log.info("[ClaimService.reTouch] follow-up task created, claimId={}, newTaskId={}", claimId, created.getId());
        return created;
    }

    private CustClaim requireOwnedActiveClaim(String claimId, String operatorEmpId, String operatorOrgCode) {
        CustClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                    CustomerErrorCode.CLAIM_NOT_FOUND.getMessage());
        }
        if (!java.util.Objects.equals(claim.getOrgId(), operatorOrgCode)) {
            throw new BizException(CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getCode(),
                    CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getMessage());
        }
        if (!ClaimStatus.CLAIMED.getCode().equals(claim.getClaimStatus())
                || !java.util.Objects.equals(claim.getMaintainerEmpId(), operatorEmpId)) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode(),
                    CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getMessage());
        }
        return claim;
    }

    private boolean isNumericId(String claimId) {
        if (!StringUtils.hasText(claimId)) return false;
        try {
            Long.parseLong(claimId);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
