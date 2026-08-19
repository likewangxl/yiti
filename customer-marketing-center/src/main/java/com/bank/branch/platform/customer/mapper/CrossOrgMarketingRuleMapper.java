package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.CrossOrgMarketingRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 跨机构营销校验规则 Mapper。 */
@Mapper
public interface CrossOrgMarketingRuleMapper extends BaseMapper<CrossOrgMarketingRule> {
    /** 查询启用规则，按展示顺序返回。 */
    @Select("SELECT * FROM CROSS_ORG_MARKETING_RULE WHERE enabled=1 ORDER BY sort_no, rule_code")
    List<CrossOrgMarketingRule> selectEnabledRules();
}
