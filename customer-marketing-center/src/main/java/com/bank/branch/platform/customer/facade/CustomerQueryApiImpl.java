package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 客户主档对外查询接口实现。
 * <p>
 * 实现 {@link CustomerQueryApi} 接口，直接委托 {@link CustMasterMapper} 和 {@link CustClaimMapper} 完成只读查询。
 * 这是被多个模块依赖的核心 API，不包含写操作。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerQueryApiImpl implements CustomerQueryApi {

    private final CustMasterMapper custMasterMapper;
    private final CustClaimMapper custClaimMapper;

    /**
     * 按 ID 查询客户主档。
     *
     * @param id 客户ID
     * @return 客户主档实体，不存在时返回 null
     */
    @Override
    public CustMaster getCustomer(String id) {
        log.debug("[CustomerQueryApiImpl.getCustomer] id={}", id);
        return custMasterMapper.selectById(id);
    }

    /**
     * 按客户编号查询客户主档。
     *
     * @param custNo 客户编号
     * @return 客户主档实体，不存在时返回 null
     */
    @Override
    public CustMaster getCustomerByCustNo(String custNo) {
        log.debug("[CustomerQueryApiImpl.getCustomerByCustNo] custNo={}", custNo);
        return custMasterMapper.selectByCustNo(custNo);
    }

    /**
     * 校验客户是否有效（存在且未删除）。
     *
     * @param custId 客户ID
     * @return true 表示客户存在，false 表示不存在或已删除
     */
    @Override
    public boolean isValidCustomer(String custId) {
        log.debug("[CustomerQueryApiImpl.isValidCustomer] custId={}", custId);
        return custMasterMapper.selectById(custId) != null;
    }

    /**
     * 校验指定机构是否已认领该客户。
     *
     * @param custId 客户ID
     * @param orgId  机构代码
     * @return true 表示该机构存在认领记录，false 表示未认领
     */
    @Override
    public boolean isClaimedByOrg(String custId, String orgId) {
        log.debug("[CustomerQueryApiImpl.isClaimedByOrg] custId={}, orgId={}", custId, orgId);
        return custClaimMapper.selectByCustIdAndOrgId(custId, orgId) != null;
    }

    /**
     * 查询客户的所有认领记录（含历史取消记录）。
     *
     * @param custId 客户ID
     * @return 认领记录列表，不存在时返回空列表
     */
    @Override
    public List<CustClaim> getCustomerClaims(String custId) {
        log.debug("[CustomerQueryApiImpl.getCustomerClaims] custId={}", custId);
        List<CustClaim> claims = custClaimMapper.selectByCustId(custId);
        return claims != null ? claims : Collections.emptyList();
    }
}
