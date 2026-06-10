package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待处理任务批次 Mapper（insert / selectById 由 MyBatis-Plus BaseMapper 提供）。
 */
@Mapper
public interface EvalAssignBatchMapper extends BaseMapper<EvalAssignBatch> {
}
