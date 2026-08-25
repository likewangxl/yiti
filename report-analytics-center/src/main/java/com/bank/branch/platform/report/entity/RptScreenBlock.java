package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN_BLOCK 实体 —— 大屏区块（布局 + 组件 + 绑定 + 钻取）.
 */
@Data
@TableName("RPT_SCREEN_BLOCK")
public class RptScreenBlock {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属大屏 RPT_SCREEN.id */
    private Long screenId;

    /** 区域：LEFT / MAIN / RIGHT */
    private String region;

    /** 区域内行号（从 1 起） */
    private Integer rowNo;

    /** 行内列号（从 1 起） */
    private Integer colNo;

    /** 行内宽度百分比 1~100 */
    private Integer widthPct;

    /** 区域内行高百分比 1~100（同行取首块值） */
    private Integer heightPct;

    /** 大屏图表 innerType（当前 19 种；SPARKLINE_CARD 仅允许 TIMESERIES 绑定） */
    private String componentType;

    /** 数据绑定 JSON */
    private String bindJson;

    /** 样式 JSON */
    private String styleJson;

    /** 钻取/跳转 JSON */
    private String drillJson;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
