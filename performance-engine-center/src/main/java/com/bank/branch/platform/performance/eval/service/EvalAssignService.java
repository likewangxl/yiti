package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalPendingGroupDTO;
import com.bank.branch.platform.performance.eval.dto.EvalPendingItemDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 待处理任务（导入式评价任务）用户端服务。
 *
 * <p>职责：当前登录人作为打分人，查询自己未提交明细的部门汇总、某部门明细，并逐人提交打分。</p>
 */
@Slf4j
@Service
public class EvalAssignService {

    /** 任务类型字典。 */
    private static final String DICT_IMPORT_TYPE = "EVAL_IMPORT_TYPE";
    /** 等级打分允许的固定分值：非常满意100/比较满意95/满意85/一般75/不满意59. */
    private static final Set<Integer> GRADE_SCORES = Set.of(100, 95, 85, 75, 59);

    private final EvalAssignItemMapper itemMapper;
    private final EvalAssignBatchMapper batchMapper;
    private final DictApi dictApi;
    private final UserApi userApi;

    @Autowired
    public EvalAssignService(EvalAssignItemMapper itemMapper,
                             EvalAssignBatchMapper batchMapper,
                             DictApi dictApi,
                             UserApi userApi) {
        this.itemMapper = itemMapper;
        this.batchMapper = batchMapper;
        this.dictApi = dictApi;
        this.userApi = userApi;
    }

    /**
     * 查询我的待处理任务汇总（按被打分人部门聚合未提交明细）。
     *
     * @param evalUserId 当前登录人工号（打分人）
     * @return 汇总行，taskTypeLabel 经字典翻译
     */
    public List<EvalPendingGroupDTO> listMyPendingGroups(String evalUserId) {
        List<EvalPendingGroupDTO> groups = itemMapper.selectPendingGroups(evalUserId);
        Map<String, String> labelMap = new HashMap<>();
        for (DictItemDTO d : dictApi.getDictItems(DICT_IMPORT_TYPE)) {
            labelMap.put(d.getDictCode(), d.getDictLabel());
        }
        for (EvalPendingGroupDTO g : groups) {
            g.setTaskTypeLabel(labelMap.getOrDefault(g.getTaskType(), g.getTaskType()));
        }
        return groups;
    }

    /**
     * 查询我在指定批次+部门下的全部明细（含已提交）。
     *
     * @param evalUserId 当前登录人工号（打分人）
     * @param batchId    批次ID
     * @param dept       被打分人部门
     * @return 明细 DTO 列表
     */
    public List<EvalPendingItemDTO> listMyPendingItems(String evalUserId, Long batchId, String dept) {
        List<EvalAssignItem> items = itemMapper.selectByScorerBatchDept(evalUserId, batchId, dept == null ? "" : dept);
        // 明细里 be_eval_user_id 存的是 USER_ID，前端工号列要展示登录名(PT_USER.username)。
        // 跨模块禁止直连 pt_user，收集去重 USER_ID 后单次走 auth UserApi 批量反查（分片 IN），
        // 避免逐行 N+1；查不到的 USER_ID（已删除用户等）回退为原 USER_ID 兜底展示。
        Set<String> beUserIds = items.stream()
                .map(EvalAssignItem::getBeEvalUserId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> idToUsername = beUserIds.isEmpty()
                ? Collections.emptyMap()
                : userApi.mapEmpIdsToUsername(new ArrayList<>(beUserIds));
        return items.stream().map(i -> toItemDTO(i, idToUsername)).collect(Collectors.toList());
    }

    /**
     * 提交某条明细的打分。
     *
     * <p>校验顺序：明细存在 → 归属当前人 → 未提交 → 批次截止未过 → 分数符合评价类型 → 写库。</p>
     *
     * @param evalUserId 当前登录人工号（打分人）
     * @param itemId     明细ID
     * @param score      分数
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(String evalUserId, Long itemId, int score) {
        EvalAssignItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, itemId);
        }
        // 归属校验：只能提交分配给自己的明细
        if (!evalUserId.equals(item.getEvalUserId())) {
            throw new PerfException(PerfErrorCode.EVAL_NO_PERMISSION, evalUserId);
        }
        // 唯一性：已提交不可重复
        if (item.getSubmitted() != null && item.getSubmitted() == 1) {
            throw new PerfException(PerfErrorCode.EVAL_SCORE_DUPLICATE, evalUserId);
        }
        // 截止时间：批次未截止才可提交
        EvalAssignBatch batch = batchMapper.selectById(item.getBatchId());
        if (batch == null || batch.getDeadline() == null || !batch.getDeadline().isAfter(LocalDateTime.now())) {
            throw new PerfException(PerfErrorCode.EVAL_TASK_CLOSED, item.getBatchId());
        }
        // 批次状态：仅 ACTIVE(0) 可打分，DRAFT(2) 或 CLOSED(1) 拒绝
        if (batch.getStatus() == null || batch.getStatus() != 0) {
            throw new PerfException(PerfErrorCode.EVAL_BATCH_NOT_ACTIVE);
        }
        // 分数校验：按评价类型分支
        if ("GRADE".equals(item.getScoreType())) {
            if (!GRADE_SCORES.contains(score)) {
                throw new PerfException(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE, score);
            }
        } else {
            if (score < 10 || score > 100) {
                throw new PerfException(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE, score);
            }
        }
        itemMapper.markSubmitted(itemId, score, LocalDateTime.now());
        log.info("[EvalAssignService.submitScore] 提交成功 itemId={} evalUserId={} score={} scoreType={}",
                itemId, evalUserId, score, item.getScoreType());
    }

    /**
     * 批量提交某部门下多人打分（一个事务 all-or-none）。
     *
     * <p>逐条复用 {@link #submitScore(String, Long, int)} 的全部校验（归属/未提交/截止/状态/分值）；
     * 因同处一个 {@code @Transactional}，任一条失败则整批回滚，不会出现"部分人已提交"的脏状态。
     * 这样前端可在录完整部门的打分后"一键提交"。</p>
     *
     * @param evalUserId 当前登录人工号（打分人）
     * @param entries    待提交明细分数列表（itemId + score）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitScoreBatch(String evalUserId, List<ScoreEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (ScoreEntry e : entries) {
            submitScore(evalUserId, e.itemId(), e.score());
        }
        log.info("[EvalAssignService.submitScoreBatch] 批量提交成功 evalUserId={} count={}",
                evalUserId, entries.size());
    }

    /** 批量提交单条：明细ID + 分数。 */
    public record ScoreEntry(Long itemId, int score) {
    }

    /** 实体 → 明细 DTO（idToUsername：USER_ID→登录名，工号列展示用，查不到回退原 USER_ID）。 */
    private EvalPendingItemDTO toItemDTO(EvalAssignItem i, Map<String, String> idToUsername) {
        EvalPendingItemDTO d = new EvalPendingItemDTO();
        d.setItemId(i.getItemId());
        d.setBeEvalUserId(i.getBeEvalUserId());
        d.setBeEvalUserUsername(idToUsername.getOrDefault(i.getBeEvalUserId(), i.getBeEvalUserId()));
        d.setBeEvalUserName(i.getBeEvalUserName());
        d.setBeEvalDept(i.getBeEvalDept());
        d.setBeEvalTag(i.getBeEvalTag());
        d.setWeightTag(i.getWeightTag());
        d.setScoreType(i.getScoreType());
        d.setScore(i.getScore());
        d.setSubmitted(i.getSubmitted());
        return d;
    }
}
