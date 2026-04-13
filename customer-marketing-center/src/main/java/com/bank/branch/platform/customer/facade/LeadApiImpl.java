package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.LeadApi;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 线索对外接口实现。
 * <p>
 * 实现 {@link LeadApi} 接口，直接委托 {@link CustLeadMapper} 完成只读查询。
 * 不调用 LeadService（避免引入不必要的业务校验和异常），直接返回 null 表示不存在。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadApiImpl implements LeadApi {

    private final CustLeadMapper leadMapper;

    /**
     * 按 ID 查询线索详情。
     *
     * @param id 线索ID
     * @return 线索实体，不存在时返回 null
     */
    @Override
    public CustLead getById(String id) {
        log.debug("[LeadApiImpl.getById] id={}", id);
        return leadMapper.selectById(id);
    }

    /**
     * 按线索编号查询线索详情。
     *
     * @param leadNo 线索编号
     * @return 线索实体，不存在时返回 null
     */
    @Override
    public CustLead getByLeadNo(String leadNo) {
        log.debug("[LeadApiImpl.getByLeadNo] leadNo={}", leadNo);
        return leadMapper.selectByLeadNo(leadNo);
    }
}
