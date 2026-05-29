package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.portal.api.AddressBookApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 人员标签关联服务.
 */
@Slf4j
@Service
public class EvalUserTagService {

    private final EvalUserTagMapper evalUserTagMapper;
    private final EvalTagMapper evalTagMapper;
    private final UserApi userApi;
    private final AddressBookApi addressBookApi;

    @Autowired
    public EvalUserTagService(EvalUserTagMapper evalUserTagMapper,
                              EvalTagMapper evalTagMapper,
                              UserApi userApi,
                              AddressBookApi addressBookApi) {
        this.evalUserTagMapper = evalUserTagMapper;
        this.evalTagMapper = evalTagMapper;
        this.userApi = userApi;
        this.addressBookApi = addressBookApi;
    }

    /** 查询人员的标签关联. */
    public List<EvalUserTag> getByUserId(Long userId) {
        return evalUserTagMapper.selectByUserId(userId);
    }

    /** 查询标签关联的所有用户ID. */
    public List<Long> getUserIdsByTagId(Long tagId) {
        return evalUserTagMapper.selectUserIdsByTagId(tagId);
    }

    /** 批量绑定人员标签. */
    @Transactional(rollbackFor = Exception.class)
    public void batchBind(Long userId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        List<EvalUserTag> list = tagIds.stream().map(tagId -> {
            EvalUserTag ut = new EvalUserTag();
            ut.setUserId(userId);
            ut.setTagId(tagId);
            return ut;
        }).collect(Collectors.toList());
        evalUserTagMapper.batchInsert(list);
    }

    /** 批量解绑人员标签. */
    @Transactional(rollbackFor = Exception.class)
    public void batchUnbind(Long userId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        evalUserTagMapper.batchDelete(userId, tagIds);
    }

    /**
     * 覆盖式保存人员的评价角色：删除该用户全部旧关联，再写入新的被评价人标签（至多一个）+ 评价人标签（多个）。
     * <p>被评价人单选由参数结构（单个 beEvalTagId）天然保证；类型校验防止前端传错类型。</p>
     *
     * @param userId      人员ID
     * @param beEvalTagId 被评价人标签ID（必须 tagType=1；null 表示清空被评价人角色）
     * @param evalTagIds  评价人标签ID列表（必须都是 tagType=2；null/空 表示清空评价人角色）
     * @throws PerfException EVAL_RULE_NOT_FOUND（标签不存在）/ EVAL_TAG_TYPE_MISMATCH（类型不符）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(Long userId, Long beEvalTagId, List<Long> evalTagIds) {
        // 1. 校验被评价人标签必须 tagType=1
        if (beEvalTagId != null) {
            EvalTag t = evalTagMapper.selectById(beEvalTagId);
            if (t == null) throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, beEvalTagId);
            if (!Integer.valueOf(1).equals(t.getTagType())) {
                throw new PerfException(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH, beEvalTagId);
            }
        }
        // 2. 校验评价人标签必须都是 tagType=2
        List<Long> evalIds = (evalTagIds == null) ? List.of() : evalTagIds;
        for (Long tid : evalIds) {
            EvalTag t = evalTagMapper.selectById(tid);
            if (t == null) throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, tid);
            if (!Integer.valueOf(2).equals(t.getTagType())) {
                throw new PerfException(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH, tid);
            }
        }
        // 3. 覆盖：删除该用户全部旧标签关联
        List<EvalUserTag> existing = evalUserTagMapper.selectByUserId(userId);
        if (!existing.isEmpty()) {
            List<Long> oldTagIds = existing.stream().map(EvalUserTag::getTagId).collect(Collectors.toList());
            evalUserTagMapper.batchDelete(userId, oldTagIds);
        }
        // 4. 写入新组合（被评价 1 个 + 评价人 N 个）
        List<EvalUserTag> toInsert = new ArrayList<>();
        if (beEvalTagId != null) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(beEvalTagId);
            toInsert.add(u);
        }
        for (Long tid : evalIds) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(tid);
            toInsert.add(u);
        }
        if (!toInsert.isEmpty()) {
            evalUserTagMapper.batchInsert(toInsert);
        }
        log.info("[EvalUserTagService.saveUserRoles] userId={} beEvalTagId={} evalTagIds={}", userId, beEvalTagId, evalIds);
    }
}
