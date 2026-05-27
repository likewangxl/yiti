package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalRuleGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 评价人组明细 Mapper.
 */
@Mapper
public interface EvalRuleGroupMapper extends BaseMapper<EvalRuleGroup> {
    List<EvalRuleGroup> selectByRuleId(@Param("ruleId") Long ruleId);
    int deleteByRuleId(@Param("ruleId") Long ruleId);
    int batchInsert(@Param("list") List<EvalRuleGroup> list);
}
