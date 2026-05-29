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

    /** 取全部"启用=是"的工号（用于"是"过滤的 eval 侧驱动分页）。 */
    List<String> selectEnabledUserIds();

    /**
     * 在给定工号集合内，取"启用=是"的子集（用于列表 overlay）。
     * 调用方须保证 userIds 非空。
     *
     * @param userIds 工号集合
     * @return 其中启用的工号
     */
    List<String> selectEnabledUserIdsIn(@Param("userIds") List<String> userIds);

    /**
     * upsert 启用位：存在则更新，不存在则插入。
     *
     * @param userId      工号
     * @param evalEnabled 1=是 0=否
     * @return 受影响行数
     */
    int upsert(@Param("userId") String userId, @Param("evalEnabled") int evalEnabled);
}
