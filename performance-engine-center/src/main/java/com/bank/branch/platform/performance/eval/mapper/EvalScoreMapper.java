package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalScore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 评价打分记录 Mapper.
 */
@Mapper
public interface EvalScoreMapper extends BaseMapper<EvalScore> {
    List<EvalScore> selectByTargetId(@Param("targetId") Long targetId);
    List<EvalScore> selectByTaskIdAndEvalUserId(@Param("taskId") Long taskId, @Param("evalUserId") String evalUserId);
    int countByTargetIdAndEvalUserId(@Param("targetId") Long targetId, @Param("evalUserId") String evalUserId);
}
