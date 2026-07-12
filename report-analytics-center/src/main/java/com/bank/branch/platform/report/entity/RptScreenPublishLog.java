package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN_PUBLISH_LOG 实体 —— 大屏发布归档(按屏滚动保留最近 10 次).
 *
 * <p>避坑:DataEase 双态互相覆盖后历史彻底丢失;本表在每次发布时留一份渲染包快照,支撑回滚。
 */
@Data
@TableName("RPT_SCREEN_PUBLISH_LOG")
public class RptScreenPublishLog {

    /** 主键(自增) */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属大屏 RPT_SCREEN.id */
    private Long screenId;

    /** 发布时的渲染包(CANVAS_PUBLISHED_JSON 全量) */
    private String snapshotJson;

    /** 发布人工号 */
    private String publishedBy;

    /** 发布时间 */
    private LocalDateTime publishedAt;
}
