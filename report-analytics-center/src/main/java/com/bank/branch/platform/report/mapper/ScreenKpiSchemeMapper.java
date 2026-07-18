package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.PerfKpiScheme;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * KPI 方案 Mapper（只读：大屏 KPI_DETAIL 数据源保存校验 + 方案下拉，全走 MyBatis-Plus 条件查询）.
 */
@Mapper
public interface ScreenKpiSchemeMapper extends BaseMapper<PerfKpiScheme> {
}
