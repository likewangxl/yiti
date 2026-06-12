package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.CustStatQueryApi;
import com.bank.branch.platform.performance.service.StatShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * {@link CustStatQueryApi} 实现：薄封装，仅做对外契约暴露；业务逻辑下沉在 {@link StatShowService}.
 *
 * <ul>
 *   <li>{@link #getCustNameFromMaster(String)} —— 委托 {@link StatShowService#getCustNameFromMaster}
 *       查客户主档 CUST_MASTER（当前口径，与 PC 管理端一致）</li>
 *   <li>{@link #getCustNameByCustId(String)} —— 委托 {@link StatShowService#getCustNameByCustId}
 *       查 XAN_M98_CUST_STAT_SHOW3（已弃用，保留兼容）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustStatQueryFacade implements CustStatQueryApi {

    private final StatShowService statShowService;

    @Override
    @Deprecated
    public Optional<String> getCustNameByCustId(String custId) {
        return statShowService.getCustNameByCustId(custId);
    }

    @Override
    public Optional<String> getCustNameFromMaster(String custNo) {
        return statShowService.getCustNameFromMaster(custNo);
    }
}
