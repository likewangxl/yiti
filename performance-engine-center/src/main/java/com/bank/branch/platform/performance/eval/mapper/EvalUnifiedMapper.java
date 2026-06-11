package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.dto.UnifiedEvalTaskRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 统一评价任务查询 —— UNION ALL 合并 EVAL_TASK 与 EVAL_ASSIGN_BATCH。
 */
@Mapper
public interface EvalUnifiedMapper {

    List<UnifiedEvalTaskRow> selectUnified(@Param("status") Integer status,
                                           @Param("keyword") String keyword,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    long countUnified(@Param("status") Integer status,
                      @Param("keyword") String keyword);
}
