package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfKpiCalcLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * KPI 方案级计算记录 Mapper（PERF_KPI_CALC_LOG）.
 *
 * <p>仅需 BaseMapper 的 insert：每个方案处理完 / 异常结束插一条。
 */
@Mapper
public interface PerfKpiCalcLogMapper extends BaseMapper<PerfKpiCalcLog> {
}
