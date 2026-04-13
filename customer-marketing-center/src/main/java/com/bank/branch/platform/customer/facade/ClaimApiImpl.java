package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.ClaimApi;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 客户认领记录对外查询接口实现。
 * <p>
 * 实现 {@link ClaimApi} 接口，直接委托 {@link CustClaimMapper} 完成只读查询。
 * 不包含认领/取消认领业务逻辑，仅提供查询能力。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimApiImpl implements ClaimApi {

    private final CustClaimMapper custClaimMapper;

    /**
     * 查询指定客户的所有认领记录（含历史取消记录）。
     *
     * @param custId 客户ID
     * @return 认领记录列表，不存在时返回空列表
     */
    @Override
    public List<CustClaim> getClaimsByCustomer(String custId) {
        log.debug("[ClaimApiImpl.getClaimsByCustomer] custId={}", custId);
        List<CustClaim> claims = custClaimMapper.selectByCustId(custId);
        return claims != null ? claims : Collections.emptyList();
    }

    /**
     * 按客户ID和机构代码查询认领记录。
     *
     * @param custId 客户ID
     * @param orgId  机构代码
     * @return 认领记录实体，不存在时返回 null
     */
    @Override
    public CustClaim getClaimByCustIdAndOrgId(String custId, String orgId) {
        log.debug("[ClaimApiImpl.getClaimByCustIdAndOrgId] custId={}, orgId={}", custId, orgId);
        return custClaimMapper.selectByCustIdAndOrgId(custId, orgId);
    }
}
