package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 人员标签关联 Mapper.
 */
@Mapper
public interface EvalUserTagMapper extends BaseMapper<EvalUserTag> {
    List<EvalUserTag> selectByUserId(@Param("userId") Long userId);
    List<Long> selectUserIdsByTagId(@Param("tagId") Long tagId);
    List<Long> selectTagIdsByUserIdAndType(@Param("userId") Long userId, @Param("tagType") Integer tagType);
    int batchInsert(@Param("list") List<EvalUserTag> list);
    int batchDelete(@Param("userId") Long userId, @Param("tagIds") List<Long> tagIds);
}
