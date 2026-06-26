package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/**
 * 公告 列表行 / 详情 视图（字段名与 SysNotice 实体一致，便于 BeanUtils 拷贝）。
 * 列表与详情共用；正文 content 列表也带回（数据量小）。
 */
@Data
public class NoticeVO {

    /** 编号. */
    private String noticId;
    /** 标题. */
    private String title;
    /** 通知正文. */
    private String content;
    /** 创建时间. */
    private String createTime;
    /** 是否公开：0,私有；1,公开. */
    private String isPublic;
    /** 序号. */
    private Long seqNo;
    /** 附件. */
    private String extend;
}
