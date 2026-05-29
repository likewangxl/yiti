package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 评价标签字典 Mapper.
 */
@Mapper
public interface EvalTagMapper extends BaseMapper<EvalTag> {
    /** 按名称查询（唯一性校验用）. */
    EvalTag selectByName(@Param("tagName") String tagName);

    /** 分页条件查询（仅按名称关键词）. */
    List<EvalTag> selectByCondition(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    /** 条件计数. */
    long countByCondition(@Param("keyword") String keyword);

    /**
     * 查询全部标签（不分页，供下拉选项用）.
     * @param status 状态筛选（可选，null 表示不过滤）
     * @return 标签列表，按 tag_id ASC 排序
     */
    List<EvalTag> selectAll(@Param("status") Integer status);
}
