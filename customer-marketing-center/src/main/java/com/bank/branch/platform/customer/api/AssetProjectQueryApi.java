package com.bank.branch.platform.customer.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 跨模块资产立项只读统计契约，禁止调用方直连 MARKETING_ASSET_PROJECT_APPLY。 */
public interface AssetProjectQueryApi {
    long countRunningByCustomer(Long custId);
    long countByApplicant(String empId);
    long countCompletedByApplicant(String empId, LocalDateTime startTime, LocalDateTime endTime);
    BigDecimal sumCompletedCreditByApplicant(String empId, LocalDateTime startTime, LocalDateTime endTime);
}
