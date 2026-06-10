package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfKpiCalcLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * KPI 方案级计算记录 Mapper（PERF_KPI_CALC_LOG）.
 *
 * <p>仅需 BaseMapper 的 insert：每个方案处理完 / 异常结束插一条。
 */
@Mapper
public interface PerfKpiCalcLogMapper extends BaseMapper<PerfKpiCalcLog> {

    /**
     * 查询计算记录中的最大数据日期（供考核计算页默认展示最新一日）.
     *
     * @return 最大 data_date；无记录时返回 null
     */
    @Select("SELECT MAX(data_date) FROM PERF_KPI_CALC_LOG")
    LocalDate selectMaxDataDate();
}
