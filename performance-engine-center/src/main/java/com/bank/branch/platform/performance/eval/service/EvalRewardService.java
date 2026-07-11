package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingItemDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 奖励分配（REWARD）用户端服务。
 *
 * <p>当前登录人作为分配人：按部门查看待分配汇总、查看某部门明细、一次性提交整组分配。</p>
 */
@Slf4j
@Service
public class EvalRewardService {

    /** 任务类型字典。 */
    private static final String DICT_IMPORT_TYPE = "EVAL_IMPORT_TYPE";

    private final EvalRewardItemMapper itemMapper;
    private final EvalAssignBatchMapper batchMapper;
    private final DictApi dictApi;

    @Autowired
    public EvalRewardService(EvalRewardItemMapper itemMapper,
                             EvalAssignBatchMapper batchMapper,
                             DictApi dictApi) {
        this.itemMapper = itemMapper;
        this.batchMapper = batchMapper;
        this.dictApi = dictApi;
    }

    /**
     * 我的奖励分配待处理汇总（按部门聚合未提交明细）。
     *
     * @param assignUserId 当前登录人工号（分配人）
     * @return 汇总行，taskTypeLabel 经字典翻译
     */
    public List<EvalRewardPendingGroupDTO> listMyRewardPendingGroups(String assignUserId) {
        List<EvalRewardPendingGroupDTO> groups = itemMapper.selectRewardPendingGroups(assignUserId);
        Map<String, String> labelMap = new HashMap<>();
        for (DictItemDTO d : dictApi.getDictItems(DICT_IMPORT_TYPE)) {
            labelMap.put(d.getDictCode(), d.getDictLabel());
        }
        for (EvalRewardPendingGroupDTO g : groups) {
            g.setTaskTypeLabel(labelMap.getOrDefault(g.getTaskType(), g.getTaskType()));
        }
        return groups;
    }

    /**
     * 查询我在指定批次+部门下的全部明细（含已提交）。
     *
     * @param assignUserId 当前登录人工号（分配人）
     * @param batchId      批次ID
     * @param dept         部门名称
     * @return 明细 DTO 列表
     */
    public List<EvalRewardPendingItemDTO> listMyRewardPendingItems(String assignUserId, Long batchId, String dept) {
        List<EvalRewardItem> items = itemMapper.selectByAssignerBatchDept(
                assignUserId, batchId, dept == null ? "" : dept);
        return items.stream().map(this::toItemDTO).collect(Collectors.toList());
    }

    /**
     * 一次性提交某(分配人+批次+部门)组的分配。
     *
     * <p>校验：批次 ACTIVE 且未过期 → entries 覆盖该组全部未提交明细且归属当前人 →
     * 每人分配值>0 → 求和严格等于该组分配合计 → 同一事务逐条写库(all-or-none)。</p>
     *
     * @param assignUserId 当前登录人工号（分配人）
     * @param batchId      批次ID
     * @param dept         部门名称
     * @param entries      分配明细（itemId + assignValue）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitRewardBatch(String assignUserId, Long batchId, String dept, List<RewardEntry> entries) {
        // 截止时间：批次未截止才可提交
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null || batch.getDeadline() == null || !batch.getDeadline().isAfter(LocalDateTime.now())) {
            throw new PerfException(PerfErrorCode.EVAL_TASK_CLOSED, batchId);
        }
        // 批次状态：仅 ACTIVE(0) 可分配
        if (batch.getStatus() == null || batch.getStatus() != 0) {
            throw new PerfException(PerfErrorCode.EVAL_BATCH_NOT_ACTIVE);
        }
        List<EvalRewardItem> groupItems = itemMapper.selectByAssignerBatchDept(assignUserId, batchId, dept);
        // 该组未提交明细（提交对象）
        Map<Long, EvalRewardItem> unsubmitted = groupItems.stream()
                .filter(i -> i.getSubmitted() != null && i.getSubmitted() == 0)
                .collect(Collectors.toMap(EvalRewardItem::getItemId, i -> i));
        if (entries == null || entries.isEmpty() || unsubmitted.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, batchId);
        }
        // 必须覆盖该组全部未提交明细（给每个人都分配）
        if (entries.size() != unsubmitted.size()) {
            throw new PerfException(PerfErrorCode.EVAL_REWARD_SUM_MISMATCH);
        }
        // 该组分配合计（组内一致，取任一条）
        BigDecimal groupTotal = unsubmitted.values().iterator().next().getAssignTotal();

        BigDecimal sum = BigDecimal.ZERO;
        for (RewardEntry e : entries) {
            EvalRewardItem it = unsubmitted.get(e.itemId());
            if (it == null) {
                // 明细不属于该组未提交集合（非归属 / 已提交 / 不存在）
                throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, e.itemId());
            }
            if (e.assignValue() == null || e.assignValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PerfException(PerfErrorCode.EVAL_REWARD_ASSIGN_NOT_POSITIVE);
            }
            sum = sum.add(e.assignValue());
        }
        // 求和严格等于分配合计
        if (groupTotal == null || sum.compareTo(groupTotal) != 0) {
            throw new PerfException(PerfErrorCode.EVAL_REWARD_SUM_MISMATCH);
        }
        LocalDateTime now = LocalDateTime.now();
        for (RewardEntry e : entries) {
            itemMapper.markAssigned(e.itemId(), e.assignValue(), now);
        }
        log.info("[EvalRewardService.submitRewardBatch] 提交成功 assignUserId={} batchId={} dept={} count={}",
                assignUserId, batchId, dept, entries.size());
    }

    /** 批量提交单条:明细ID + 分配值. */
    public record RewardEntry(Long itemId, BigDecimal assignValue) {
    }

    /** 实体 → 明细 DTO。 */
    private EvalRewardPendingItemDTO toItemDTO(EvalRewardItem i) {
        EvalRewardPendingItemDTO d = new EvalRewardPendingItemDTO();
        d.setItemId(i.getItemId());
        d.setBeAssignedUserId(i.getBeAssignedUserId());
        d.setBeAssignedUserName(i.getBeAssignedUserName());
        d.setDeptName(i.getDeptName());
        d.setOriginalValue(i.getOriginalValue());
        d.setCashValue(i.getCashValue());
        d.setAssignTotal(i.getAssignTotal());
        d.setAssignValue(i.getAssignValue());
        d.setSubmitted(i.getSubmitted());
        return d;
    }
}
