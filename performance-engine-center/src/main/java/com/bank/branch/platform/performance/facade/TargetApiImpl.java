package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.TargetApi;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.service.TargetPlanService;
import com.bank.branch.platform.performance.service.TargetValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 目标查询对外 API 实现 (V1.0, 4 方法全实现).
 *
 * <p>Step 1 (RED) 骨架: 4 方法抛 UOE, 由 UT 暴露缺口。
 * Step 2 (GREEN) 将改为真实委托 Service + Assembler 装配 DTO。
 */
@Service
@RequiredArgsConstructor
public class TargetApiImpl implements TargetApi {

    private final TargetPlanService targetPlanService;
    private final TargetValueService targetValueService;

    @Override
    public Optional<TargetPlanDTO> getTargetPlan(String planCode) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<TargetPlanDTO> getTargetPlanById(String planId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<BigDecimal> getTargetValue(String planId, String subjectType, String subjectId,
                                               String cycleKey, String metricCode) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<TargetValueDTO> listTargetValues(String planId, String subjectType, String subjectId,
                                                 String cycleKey) {
        throw new UnsupportedOperationException("not implemented");
    }
}
