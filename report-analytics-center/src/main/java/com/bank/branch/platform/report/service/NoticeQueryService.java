package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.NoticeQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.NoticeVO;

/**
 * 公告查询服务（只读，历史数据查询 - 公告查询）。
 */
public interface NoticeQueryService {

    /** 分页查询公告列表（SEQ_NO 倒序，顶部查询项过滤）. */
    PageResult<NoticeVO> pageList(NoticeQueryReqDTO req, PageRequest page);

    /** 查询公告详情（含正文）. */
    NoticeVO detail(String noticId);
}
