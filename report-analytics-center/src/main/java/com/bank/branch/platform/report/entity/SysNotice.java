package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通知公告 sys_notice 贫血实体（只读，历史数据查询 - 公告查询用）.
 *
 * <p>{@code NOTIC_ID} 为编号主键；{@code IS_PUBLIC} 0私有/1公开；{@code SEQ_NO} 序号。</p>
 */
@Data
@TableName("sys_notice")
public class SysNotice {

    /** 编号（主键）. */
    @TableId(value = "NOTIC_ID", type = IdType.INPUT)
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
