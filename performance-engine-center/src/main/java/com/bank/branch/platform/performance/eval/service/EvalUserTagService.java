package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 人员标签关联服务.
 */
@Slf4j
@Service
public class EvalUserTagService {

    private final EvalUserTagMapper evalUserTagMapper;

    @Autowired
    public EvalUserTagService(EvalUserTagMapper evalUserTagMapper) {
        this.evalUserTagMapper = evalUserTagMapper;
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
}
