package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bank.branch.platform.customer.entity.TouchLimitRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collections;
import java.util.List;

/** 客户标签触达周期规则 Mapper。 */
@Mapper
public interface TouchLimitRuleMapper extends BaseMapper<TouchLimitRule> {

    /** 按标签 ID 查询规则；唯一键保证最多一行。 */
    default TouchLimitRule selectByTagId(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<TouchLimitRule> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(TouchLimitRule::getTagId, tagId);
        return selectList(wrapper).stream().findFirst().orElse(null);
    }

    /**
     * 按当前标签页一次性查询规则，避免分页标签逐行查规则形成 N+1。
     */
    default List<TouchLimitRule> selectByTagIds(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<TouchLimitRule> wrapper = Wrappers.lambdaQuery();
        wrapper.in(TouchLimitRule::getTagId, tagIds);
        return selectList(wrapper);
    }
}
