package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.UnifiedEvalTaskRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalScoreMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskTargetMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUnifiedMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一评价任务查询与删除 —— 合并规则任务(EVAL_TASK)与导入批次(EVAL_ASSIGN_BATCH)。
 */
@Slf4j
@Service
public class EvalUnifiedService {

    private final EvalUnifiedMapper unifiedMapper;
    private final EvalTaskMapper evalTaskMapper;
    private final EvalTaskTargetMapper evalTaskTargetMapper;
    private final EvalScoreMapper evalScoreMapper;
    private final EvalAssignBatchMapper evalAssignBatchMapper;
    private final EvalAssignItemMapper evalAssignItemMapper;

    @Autowired
    public EvalUnifiedService(EvalUnifiedMapper unifiedMapper,
                              EvalTaskMapper evalTaskMapper,
                              EvalTaskTargetMapper evalTaskTargetMapper,
                              EvalScoreMapper evalScoreMapper,
                              EvalAssignBatchMapper evalAssignBatchMapper,
                              EvalAssignItemMapper evalAssignItemMapper) {
        this.unifiedMapper = unifiedMapper;
        this.evalTaskMapper = evalTaskMapper;
        this.evalTaskTargetMapper = evalTaskTargetMapper;
        this.evalScoreMapper = evalScoreMapper;
        this.evalAssignBatchMapper = evalAssignBatchMapper;
        this.evalAssignItemMapper = evalAssignItemMapper;
    }

    /**
     * 分页查询统一评价任务列表。
     *
     * @param status   任务状态（null=全部, 0=进行中, 1=已结束, 2=草稿）
     * @param keyword  关键词（匹配任务名称/批次名称/创建人）
     * @param page     页码，1-based
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<UnifiedEvalTaskRow> listUnified(Integer status, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<UnifiedEvalTaskRow> rows = unifiedMapper.selectUnified(status, keyword, offset, pageSize);
        long total = unifiedMapper.countUnified(status, keyword);
        return PageResult.of(page, pageSize, total, rows);
    }

    /**
     * 删除评价任务（硬删除）。
     *
     * <p>校验截止时间：截止时间未到则拒绝删除。<b>例外</b>：草稿(未发布, status=2)的导入批次随时可删，
     * 因其尚未发布给打分人、不存在已采集数据，不受截止时间约束。
     * <p>规则任务(AUTO)：级联删除 EVAL_SCORE → EVAL_TASK_TARGET → EVAL_TASK。
     * <p>导入批次(IMPORT)：级联删除 EVAL_ASSIGN_ITEM → EVAL_ASSIGN_BATCH。
     *
     * @param sourceType AUTO=规则任务, 其他=导入批次
     * @param sourceId   任务ID 或 批次ID
     * @throws PerfException 截止时间未到且非草稿时抛 EVAL_TASK_DELETE_BEFORE_DEADLINE
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUnified(String sourceType, Long sourceId) {
        if ("AUTO".equals(sourceType)) {
            EvalTask task = evalTaskMapper.selectById(sourceId);
            if (task == null) {
                throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, sourceId);
            }
            if (task.getEndTime() != null && task.getEndTime().isAfter(LocalDateTime.now())) {
                throw new PerfException(PerfErrorCode.EVAL_TASK_DELETE_BEFORE_DEADLINE,
                        "截止时间 " + task.getEndTime() + "，当前不可删除");
            }
            evalScoreMapper.deleteByTaskId(sourceId);
            evalTaskTargetMapper.deleteByTaskId(sourceId);
            evalTaskMapper.deleteById(sourceId);
            log.info("[EvalUnifiedService.deleteUnified] 规则任务已删除 taskId={}", sourceId);
        } else {
            EvalAssignBatch batch = evalAssignBatchMapper.selectById(sourceId);
            if (batch == null) {
                throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, sourceId);
            }
            // 草稿(未发布, status=2)随时可删；已发布(ACTIVE/CLOSED)才需截止时间已过
            boolean draft = batch.getStatus() != null && batch.getStatus() == 2;
            if (!draft && batch.getDeadline() != null && batch.getDeadline().isAfter(LocalDateTime.now())) {
                throw new PerfException(PerfErrorCode.EVAL_TASK_DELETE_BEFORE_DEADLINE,
                        "截止时间 " + batch.getDeadline() + "，当前不可删除");
            }
            evalAssignItemMapper.deleteByBatchId(sourceId);
            evalAssignBatchMapper.deleteById(sourceId);
            log.info("[EvalUnifiedService.deleteUnified] 导入批次已删除 batchId={}", sourceId);
        }
    }
}
