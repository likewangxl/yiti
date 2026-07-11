package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待处理任务批次 Mapper（insert / selectById 由 MyBatis-Plus BaseMapper 提供）。
 */
@Mapper
public interface EvalAssignBatchMapper extends BaseMapper<EvalAssignBatch> {

    /**
     * 关闭已过截止时间的进行中批次（status 0→1），覆盖评价导入(EVAL)与奖励分配(REWARD)。
     * <p>带条件的批更新，落 XML：{@code UPDATE ... SET status=1 WHERE status=0 AND deadline<=NOW()}。</p>
     *
     * @return 本次关闭的批次数
     */
    int closeExpiredBatches();
}
