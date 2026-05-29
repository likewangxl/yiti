package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;
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
    List<EvalUserTag> selectByUserId(@Param("userId") String userId);
    List<String> selectUserIdsByTagId(@Param("tagId") Long tagId);
    List<Long> selectTagIdsByUserIdAndType(@Param("userId") String userId, @Param("roleType") Integer roleType);
    int batchInsert(@Param("list") List<EvalUserTag> list);
    int batchDelete(@Param("userId") String userId, @Param("tagIds") List<Long> tagIds);

    /**
     * 批量查询多个用户的标签（JOIN EVAL_TAG 带出 tagName；roleType 取自 EVAL_USER_TAG.role_type），供列表聚合用。
     * 调用方须保证 userIds 非空。
     *
     * @param userIds 人员工号列表（String）
     * @return 投影行列表
     */
    List<EvalUserTagRow> selectUserTagsByUserIds(@Param("userIds") List<String> userIds);
}
