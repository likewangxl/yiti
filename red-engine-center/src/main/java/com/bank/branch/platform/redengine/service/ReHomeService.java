package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReHomeBranchRankingDTO;
import com.bank.branch.platform.redengine.api.dto.ReHomeSummaryDTO;
import com.bank.branch.platform.redengine.api.dto.ReOverduePageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionActionRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionExecuteReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskOverdueItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReWarningPoolDTO;

import java.util.List;

/** 红色引擎首页、季度排名、预警池和任务逾期扣分服务。 */
public interface ReHomeService {

    /**
     * 按当前登录人的角色返回首页汇总。
     *
     * @param operatorId 当前登录人平台用户 ID
     * @return 组织视角或所在支部视角首页数据
     */
    ReHomeSummaryDTO getSummary(String operatorId);

    /**
     * 查询当前自然季度支部密集排名。
     *
     * @return 按得分降序、组织 ID 升序的排名
     */
    List<ReHomeBranchRankingDTO> getCurrentQuarterRanking();

    /**
     * 按当前登录角色查询季度排名；组织角色返回全量，支部角色仅返回所在支部。
     *
     * @param operatorId 当前登录人平台用户 ID
     * @return 应用当前用户数据范围后的排名
     */
    List<ReHomeBranchRankingDTO> getCurrentQuarterRanking(String operatorId);

    /**
     * 查询连续当前季度与上一已完成季度的红黄牌预警。
     *
     * @return 红牌优先且不重复的预警池
     */
    ReWarningPoolDTO getWarningPool();

    /**
     * 分页查询已到截止时间、尚未执行扣分的任务分配。
     *
     * @param query      查询条件
     * @param operatorId 当前登录人平台用户 ID
     * @return 逾期待执行扣分页
     */
    PageResult<ReTaskOverdueItemDTO> pageOverdue(ReOverduePageQueryDTO query, String operatorId);

    /**
     * 执行一条任务分配的逾期扣分，按 assignment 唯一幂等。
     *
     * @param request    扣分请求
     * @param operatorId 当前登录人平台用户 ID
     * @return 扣分结果
     */
    ReTaskDeductionActionRespDTO executeOverdue(ReTaskDeductionExecuteReqDTO request, String operatorId);
}
