package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.CustStatQueryApi;
import com.bank.branch.platform.performance.service.StatShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * {@link CustStatQueryApi} 实现：委托 {@link StatShowService#getCustNameByCustId} 查 XAN_M98_CUST_STAT_SHOW3.
 *
 * <p>薄封装，仅做对外契约暴露；业务逻辑（空值处理、LIMIT 1 取一条）下沉在 service 层。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustStatQueryFacade implements CustStatQueryApi {

    private final StatShowService statShowService;

    @Override
    public Optional<String> getCustNameByCustId(String custId) {
        return statShowService.getCustNameByCustId(custId);
    }
}
