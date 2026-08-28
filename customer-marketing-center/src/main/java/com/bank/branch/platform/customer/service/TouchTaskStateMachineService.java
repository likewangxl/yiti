package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 触达任务状态机服务。
 * <p>
 * 封装触达任务 4 个状态 (PENDING/IN_PROGRESS/SUCCESS/CANCELLED) 之间的合法转移规则。
 * 依据《功能规格》§7.3bis 定义：
 * <ul>
 *   <li>PENDING → IN_PROGRESS / CANCELLED</li>
 *   <li>IN_PROGRESS → SUCCESS / CANCELLED</li>
 *   <li>SUCCESS / CANCELLED 为终态，不允许再转移</li>
 * </ul>
 * </p>
 */
@Service
public class TouchTaskStateMachineService {

    /**
     * 合法状态转移规则表。
     * key 为起始态，value 为该起始态允许流转到的目标态集合。
     * 终态（SUCCESS/CANCELLED）对应空集合，任何转移均非法。
     */
    private static final Map<TouchTaskStatus, Set<TouchTaskStatus>> ALLOWED = Map.of(
            TouchTaskStatus.PENDING,     Set.of(TouchTaskStatus.IN_PROGRESS,
                                                TouchTaskStatus.CANCELLED),
            TouchTaskStatus.IN_PROGRESS, Set.of(TouchTaskStatus.SUCCESS,
                                                TouchTaskStatus.CANCELLED),
            TouchTaskStatus.SUCCESS,     Set.of(),
            TouchTaskStatus.CANCELLED,   Set.of()
    );

    /**
     * 校验触达任务状态转移合法性。
     * <p>
     * 若转移非法（包含 null 入参），抛出 {@code BizException} (CUST-40010)。
     * </p>
     *
     * @param from 当前状态，不得为 null
     * @param to   目标状态，不得为 null
     * @throws BizException 非法转移时抛 CUST-40010
     */
    public void assertTransition(TouchTaskStatus from, TouchTaskStatus to) {
        if (from == null || to == null) {
            throw new BizException(
                    CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getCode(),
                    CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getMessage());
        }
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new BizException(
                    CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getCode(),
                    CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getMessage());
        }
    }

    /**
     * 检查状态转移是否合法（不抛异常版本）。
     * <p>
     * null 入参直接返回 {@code false}。
     * </p>
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true 表示合法，false 表示非法
     */
    public boolean isValidTransition(TouchTaskStatus from, TouchTaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
