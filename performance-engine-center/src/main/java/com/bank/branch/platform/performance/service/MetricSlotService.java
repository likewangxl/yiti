package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Set;

/**
 * 指标槽位分配服务.
 */
@Service
@RequiredArgsConstructor
public class MetricSlotService {

    private final PerfMetricDefMapper mapper;

    /**
     * 按维度和层级分配槽位.
     *
     * @param baseDim       基础维度
     * @param metricLevel   指标层级
     * @param preferredSlot 指定槽位
     * @return 分配到的槽位
     */
    @Transactional(readOnly = true)
    public int allocSlot(String baseDim, Integer metricLevel, Integer preferredSlot) {
        int[] range = rangeOf(metricLevel);
        Set<Integer> occupied = listOccupied(baseDim);

        if (preferredSlot != null) {
            if (preferredSlot < range[0] || preferredSlot > range[1]) {
                throw new PerfException(
                        PerfErrorCode.PARAM_INVALID,
                        "preferredSlot=" + preferredSlot + " 越出 L" + metricLevel + " 槽位区间");
            }
            if (occupied.contains(preferredSlot)) {
                throw new PerfException(PerfErrorCode.METRIC_SLOT_CONFLICT, baseDim, preferredSlot);
            }
            return preferredSlot;
        }

        for (int slot = range[0]; slot <= range[1]; slot++) {
            if (!occupied.contains(slot)) {
                return slot;
            }
        }
        throw new PerfException(PerfErrorCode.INVALID_STATE, "L" + metricLevel + " 槽位区间已耗尽");
    }

    /**
     * 查询某维度已占用槽位.
     *
     * @param baseDim 基础维度
     * @return 已占用槽位集合
     */
    @Transactional(readOnly = true)
    public Set<Integer> listOccupied(String baseDim) {
        Set<Integer> occupied = mapper.selectOccupiedSlots(baseDim);
        return occupied == null ? Collections.emptySet() : occupied;
    }

    /**
     * 释放指定指标的槽位.
     *
     * @param id       指标主键
     * @param operator 操作人
     * @param reason   释放原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseSlot(String id, String operator, String reason) {
        PerfMetricDef def = mapper.selectById(id);
        if (def == null) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, id);
        }
        if (!"DISABLED".equals(def.getStatus())) {
            throw new PerfException(PerfErrorCode.INVALID_STATE, "仅 DISABLED 状态可释放槽位, 当前=" + def.getStatus());
        }
        mapper.releaseSlotById(id, operator);
    }

    private int[] rangeOf(Integer metricLevel) {
        if (metricLevel == null) {
            throw new PerfException(PerfErrorCode.PARAM_INVALID, "metricLevel 不能为空");
        }
        return switch (metricLevel) {
            case 1 -> new int[]{1, 100};
            case 2 -> new int[]{101, 150};
            case 3 -> new int[]{151, 200};
            default -> throw new PerfException(PerfErrorCode.PARAM_INVALID, "不支持的 metricLevel=" + metricLevel);
        };
    }
}
