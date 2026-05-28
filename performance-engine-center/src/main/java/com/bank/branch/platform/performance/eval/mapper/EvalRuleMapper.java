package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 评价关系规则 Mapper.
 */
@Mapper
public interface EvalRuleMapper extends BaseMapper<EvalRule> {
    EvalRule selectByBeEvalTagId(@Param("beEvalTagId") Long beEvalTagId);
    List<EvalRule> selectByCondition(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    long countByCondition(@Param("keyword") String keyword);
}
