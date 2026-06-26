package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.NoticeQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.NoticeVO;
import com.bank.branch.platform.report.entity.SysNotice;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.SysNoticeMapper;
import com.bank.branch.platform.report.service.NoticeQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 公告查询实现：只读，MyBatis-Plus 条件查询 + 分页，对外只返回 VO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeQueryServiceImpl implements NoticeQueryService {

    private final SysNoticeMapper noticeMapper;

    @Override
    public PageResult<NoticeVO> pageList(NoticeQueryReqDTO req, PageRequest page) {
        NoticeQueryReqDTO q = req == null ? new NoticeQueryReqDTO() : req;
        LambdaQueryWrapper<SysNotice> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getTitle())) {
            w.like(SysNotice::getTitle, q.getTitle().trim());
        }
        if (StringUtils.hasText(q.getIsPublic())) {
            w.eq(SysNotice::getIsPublic, q.getIsPublic().trim());
        }
        if (StringUtils.hasText(q.getCreateTimeStart())) {
            w.ge(SysNotice::getCreateTime, q.getCreateTimeStart().trim());
        }
        if (StringUtils.hasText(q.getCreateTimeEnd())) {
            w.le(SysNotice::getCreateTime, q.getCreateTimeEnd().trim());
        }
        w.orderByDesc(SysNotice::getSeqNo);

        Page<SysNotice> p = new Page<>(page.getPageNo(), page.getPageSize());
        noticeMapper.selectPage(p, w);
        List<NoticeVO> rows = p.getRecords().stream().map(e -> {
            NoticeVO vo = new NoticeVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(page.getPageNo(), page.getPageSize(), p.getTotal(), rows);
    }

    @Override
    public NoticeVO detail(String noticId) {
        SysNotice e = noticeMapper.selectById(noticId);
        if (e == null) {
            throw new RptException(RptErrorCode.NOTICE_NOT_FOUND);
        }
        NoticeVO vo = new NoticeVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
