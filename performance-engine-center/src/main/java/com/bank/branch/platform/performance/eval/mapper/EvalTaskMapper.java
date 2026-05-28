package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 评价任务 Mapper.
 */
@Mapper
public interface EvalTaskMapper extends BaseMapper<EvalTask> {
    List<EvalTask> selectByCondition(@Param("status") Integer status, @Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    long countByCondition(@Param("status") Integer status, @Param("keyword") String keyword);
    List<EvalTask> selectExpiredActive();
    int closeTask(@Param("taskId") Long taskId);
}
