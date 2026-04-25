package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerFilterDTO;
import com.bank.branch.platform.report.dto.req.CustPoolSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.CustPoolSummaryVO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.CustPoolSummaryService;
import com.bank.branch.platform.report.service.ExportTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link CustPoolSummaryService} 实现（Task M3.3.1，Green）.
 *
 * <p>装配步骤：
 * <ol>
 *   <li>未传 orgId 时按当前用户 orgCode 兜底</li>
 *   <li>构造 4 次 CustomerFilterDTO（total / VIP / NORMAL / POTENTIAL），调 countCustomers 累计</li>
 *   <li>上游异常包装 RPT-50001（R4 fail-close）</li>
 *   <li>装配 CustPoolSummaryVO，单行返回</li>
 * </ol>
 *
 * <p>缓存：{@code rpt:summary:cust}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustPoolSummaryServiceImpl implements CustPoolSummaryService {

    private static final String CUSTOMER_TYPE_VIP = "VIP";

    private static final String CUSTOMER_TYPE_NORMAL = "NORMAL";

    private static final String CUSTOMER_TYPE_POTENTIAL = "POTENTIAL";

    private final CustomerQueryApi customerQueryApi;

    private final CurrentUserApi currentUserApi;

    private final ExportTaskService exportTaskService;

    @Override
    @Cacheable(value = "rpt:summary:cust",
            key = "T(java.lang.String).format('%s', #req.orgId)",
            unless = "#result == null")
    public PageResult<CustPoolSummaryVO> getCustPoolSummary(CustPoolSummaryReqDTO req, PageRequest page) {
        // 1) 兜底 orgCode
        String orgCode = StringUtils.hasText(req.getOrgId())
                ? req.getOrgId()
                : currentUserApi.getCurrentOrgCode();

        // 2) 4 次 countCustomers 累计
        try {
            long total = customerQueryApi.countCustomers(buildFilter(orgCode, null));
            long vip = customerQueryApi.countCustomers(buildFilter(orgCode, CUSTOMER_TYPE_VIP));
            long normal = customerQueryApi.countCustomers(buildFilter(orgCode, CUSTOMER_TYPE_NORMAL));
            long potential = customerQueryApi.countCustomers(buildFilter(orgCode, CUSTOMER_TYPE_POTENTIAL));

            CustPoolSummaryVO vo = CustPoolSummaryVO.builder()
                    .orgCode(orgCode)
                    .totalCount(total)
                    .vipCount(vip)
                    .normalCount(normal)
                    .potentialCount(potential)
                    .build();

            return PageResult.of(page.getPageNo(), page.getPageSize(), 1L, List.of(vo));
        } catch (RuntimeException ex) {
            log.warn("[CustPoolSummary] CustomerQueryApi.countCustomers 失败 orgCode={} cause={}",
                    orgCode, ex.getMessage());
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED, ex);
        }
    }

    @Override
    public ExportTaskRespDTO submitCustPoolSummaryExport(CustPoolSummaryReqDTO req) {
        return exportTaskService.submitCustPoolSummaryExport(req);
    }

    /**
     * 构造客户过滤条件：按 orgCode 限定数据范围 + 可选 customerType.
     */
    private CustomerFilterDTO buildFilter(String orgCode, String customerType) {
        CustomerFilterDTO filter = new CustomerFilterDTO();
        filter.setOrgIds(List.of(orgCode));
        if (StringUtils.hasText(customerType)) {
            filter.setCustomerTypes(List.of(customerType));
        }
        return filter;
    }
}
