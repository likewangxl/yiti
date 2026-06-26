package com.bank.branch.platform.report.dto.req;

import lombok.Data;

/**
 * 公告查询列表条件（页面上部查询项，全部可选）。列表按 SEQ_NO 倒序。
 */
@Data
public class NoticeQueryReqDTO {

    /** 标题（模糊匹配）. */
    private String title;

    /** 是否公开：0,私有；1,公开（精确匹配）. */
    private String isPublic;

    /** 创建时间起（含）. */
    private String createTimeStart;

    /** 创建时间止（含）. */
    private String createTimeEnd;
}
