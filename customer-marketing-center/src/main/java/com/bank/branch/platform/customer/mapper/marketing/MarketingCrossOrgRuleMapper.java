package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingCrossOrgRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** MARKETING_CROSS_ORG_RULE 数据访问。 */
@Mapper
public interface MarketingCrossOrgRuleMapper extends BaseMapper<MarketingCrossOrgRule> {

    /** 查询启用规则，按 sort_no、rule_code 稳定排序。 */
    List<MarketingCrossOrgRule> selectEnabledRules();
}
