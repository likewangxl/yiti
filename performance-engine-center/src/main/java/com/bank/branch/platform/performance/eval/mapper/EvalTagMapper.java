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
    /**
     * 按名称和类型查询（唯一性校验用）.
     */
    EvalTag selectByNameAndType(@Param("tagName") String tagName, @Param("tagType") Integer tagType);

    /**
     * 分页条件查询.
     */
    List<EvalTag> selectByCondition(@Param("tagType") Integer tagType, @Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 条件计数.
     */
    long countByCondition(@Param("tagType") Integer tagType, @Param("keyword") String keyword);
}
