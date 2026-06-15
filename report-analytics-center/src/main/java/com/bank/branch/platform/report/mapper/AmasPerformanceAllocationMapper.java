package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.AmasPerformanceAllocation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业绩调整分配信息 Mapper：按 PERF_ADJUST_NO 取一组分配明细（详情页用）.
 */
@Mapper
public interface AmasPerformanceAllocationMapper extends BaseMapper<AmasPerformanceAllocation> {
}
