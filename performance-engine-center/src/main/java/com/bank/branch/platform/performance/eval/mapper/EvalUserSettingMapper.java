package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalUserSetting;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 人员评价设置 Mapper。
 */
@Mapper
public interface EvalUserSettingMapper extends BaseMapper<EvalUserSetting> {

    /**
     * 取全部"不参与评价"的工号（出现在 EVAL_USER_SETTING 即视为不参与；
     * 用于"否"过滤的名单驱动分页）。
     */
    List<String> selectExcludedUserIds();

    /**
     * 在给定工号集合内，取"不参与评价"的子集（出现在名单即不参与；用于列表 overlay）。
     * 调用方须保证 userIds 非空。
     *
     * @param userIds 工号集合
     * @return 其中不参与评价（在名单内）的工号
     */
    List<String> selectExcludedUserIdsIn(@Param("userIds") List<String> userIds);

    /**
     * 标记某工号"不参与评价"：写入排除名单（幂等：已存在则刷新 updated_time）。
     *
     * @param userId 工号
     * @return 受影响行数
     */
    int markExcluded(@Param("userId") String userId);

    /**
     * 清除某工号的"不参与"标记：从排除名单移出（恢复参与，回到默认）。
     *
     * @param userId 工号
     * @return 受影响行数
     */
    int clearExcluded(@Param("userId") String userId);
}
