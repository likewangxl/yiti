package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.ClaimApi;
import com.bank.branch.platform.customer.api.converter.CustClaimDTOConverter;
import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 认领查询 API 实现。
 * <p>
 * 委托 {@link CustClaimMapper} 完成只读查询，所有方法只返回 CLAIMED 状态的记录。
 * 不包含认领/取消认领业务逻辑，仅提供跨模块查询能力。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimApiImpl implements ClaimApi {

    /** 有效认领状态字符串常量，与 ClaimStatus.CLAIMED.getCode() 一致 */
    private static final String CLAIMED = "CLAIMED";

    private final CustClaimMapper custClaimMapper;

    /**
     * 获取客户在指定机构的认领关系（仅 CLAIMED 状态）。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return CLAIMED 状态的认领 DTO；不存在或已取消时返回 empty
     */
    @Override
    public Optional<CustClaimDTO> getClaim(String custId, String orgCode) {
        log.debug("[ClaimApiImpl.getClaim] custId={}, orgCode={}", custId, orgCode);
        CustClaim c = custClaimMapper.selectByCustIdAndOrgId(custId, orgCode);
        if (c == null || !CLAIMED.equals(c.getClaimStatus())) {
            return Optional.empty();
        }
        return Optional.ofNullable(CustClaimDTOConverter.toDTO(c));
    }

    /**
     * 员工已认领的客户列表（maintainer_emp_id 且 claim_status=CLAIMED）。
     *
     * @param empId 员工工号
     * @return 该员工维护的有效认领记录列表，不存在时返回空列表
     */
    @Override
    public List<CustClaimDTO> getEmpClaims(String empId) {
        log.debug("[ClaimApiImpl.getEmpClaims] empId={}", empId);
        List<CustClaim> list = custClaimMapper.selectActiveByEmp(empId);
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(CustClaimDTOConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 机构已认领的客户列表（claim_status=CLAIMED）。
     *
     * @param orgCode 机构代码
     * @return 该机构下有效认领记录列表，不存在时返回空列表
     */
    @Override
    public List<CustClaimDTO> getOrgClaims(String orgCode) {
        log.debug("[ClaimApiImpl.getOrgClaims] orgCode={}", orgCode);
        List<CustClaim> list = custClaimMapper.selectActiveByOrg(orgCode);
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(CustClaimDTOConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 校验认领关系是否有效（CLAIMED）。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return true 表示存在 CLAIMED 状态的认领关系
     */
    @Override
    public boolean isClaimActive(String custId, String orgCode) {
        log.debug("[ClaimApiImpl.isClaimActive] custId={}, orgCode={}", custId, orgCode);
        CustClaim c = custClaimMapper.selectByCustIdAndOrgId(custId, orgCode);
        return c != null && CLAIMED.equals(c.getClaimStatus());
    }

    /**
     * 统计员工认领客户数量（仅 CLAIMED 状态）。
     *
     * @param empId 员工工号
     * @return 有效认领数量
     */
    @Override
    public long countEmpClaims(String empId) {
        log.debug("[ClaimApiImpl.countEmpClaims] empId={}", empId);
        Long v = custClaimMapper.countActiveByEmp(empId);
        return v == null ? 0L : v;
    }

    /**
     * 统计机构认领客户数量（仅 CLAIMED 状态）。
     *
     * @param orgCode 机构代码
     * @return 有效认领数量
     */
    @Override
    public long countOrgClaims(String orgCode) {
        log.debug("[ClaimApiImpl.countOrgClaims] orgCode={}", orgCode);
        Long v = custClaimMapper.countActiveByOrg(orgCode);
        return v == null ? 0L : v;
    }
}
