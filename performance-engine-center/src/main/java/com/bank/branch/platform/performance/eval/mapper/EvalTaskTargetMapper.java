package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalTaskTarget;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;

/**
 * 任务被评价人明细 Mapper.
 */
@Mapper
public interface EvalTaskTargetMapper extends BaseMapper<EvalTaskTarget> {
    List<EvalTaskTarget> selectByTaskId(@Param("taskId") Long taskId);
    int batchInsert(@Param("list") List<EvalTaskTarget> list);
    int updateFinalScore(@Param("targetId") Long targetId, @Param("finalScore") BigDecimal finalScore);
    int deleteByTaskId(@Param("taskId") Long taskId);
}
