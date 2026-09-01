package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.bizapp.enums.SupportScenario;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 中台支持申请场景路由器（无状态）。
 * <p>
 * 根据输入参数决定走场景A（产品直达）还是场景B（部门承接）。
 * 场景A：productIds 非空 且 otherDemand 为空。
 * 场景B：productIds 为空 或 otherDemand 非空，要求 supportDeptId 必填。
 * 两者皆空：抛出 BIZ-40903。
 * </p>
 */
@Slf4j
@Service
public class SupportScenarioRouter {

    /**
     * 根据输入参数决定场景。
     *
     * @param productIds    产品ID列表（可为 null 或空）
     * @param otherDemand   其他需求（可为 null 或空）
     * @param supportDeptId 承接部门ID（场景B必填）
     * @return SupportScenario.A 或 SupportScenario.B
     * @throws BizException BIZ-40903 if both empty, BIZ-40902 if B but no deptId
     */
    public SupportScenario route(List<String> productIds, String otherDemand, String supportDeptId) {
        boolean hasProducts = productIds != null && !productIds.isEmpty();
        boolean hasOtherDemand = StringUtils.hasText(otherDemand);

        // 两者皆为空 -> BIZ-40903
        if (!hasProducts && !hasOtherDemand) {
            throw new BizException(
                    BizAppErrorCode.EMPTY_PRODUCT_AND_DEMAND.getCode(),
                    BizAppErrorCode.EMPTY_PRODUCT_AND_DEMAND.getMessage()
            );
        }

        // 场景A：有产品 且 无其他需求
        if (hasProducts && !hasOtherDemand) {
            log.debug("[SupportScenarioRouter] 路由到场景A，productIds={}", productIds);
            return SupportScenario.A;
        }

        // 场景B：无产品 或 有其他需求 -> 需要 supportDeptId
        if (!StringUtils.hasText(supportDeptId)) {
            throw new BizException(
                    BizAppErrorCode.SCENARIO_B_MISSING_DEPT.getCode(),
                    BizAppErrorCode.SCENARIO_B_MISSING_DEPT.getMessage()
            );
        }

        log.debug("[SupportScenarioRouter] 路由到场景B，supportDeptId={}", supportDeptId);
        return SupportScenario.B;
    }
}
