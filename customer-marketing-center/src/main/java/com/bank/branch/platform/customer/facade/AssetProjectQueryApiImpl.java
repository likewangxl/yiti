package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.AssetProjectQueryApi;
import com.bank.branch.platform.customer.mapper.AssetProjectApplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AssetProjectQueryApiImpl implements AssetProjectQueryApi {
    private final AssetProjectApplyMapper mapper;

    @Override public long countRunningByCustomer(Long custId) { return mapper.countRunningByCustomer(custId); }
    @Override public long countByApplicant(String empId) { return mapper.countByApplicant(empId); }
    @Override public long countCompletedByApplicant(String empId, LocalDateTime start, LocalDateTime end) {
        return mapper.countCompletedByApplicant(empId, start, end);
    }
    @Override public BigDecimal sumCompletedCreditByApplicant(String empId, LocalDateTime start, LocalDateTime end) {
        BigDecimal amount = mapper.sumCompletedCreditByApplicant(empId, start, end);
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
