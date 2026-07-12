package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN_DATASOURCE 实体 —— 大屏数据源定义.
 *
 * <p>贫血模型，字段对齐 2026-07-12-screen-dashboard-ddl.sql。
 */
@Data
@TableName("RPT_SCREEN_DATASOURCE")
public class RptScreenDatasource {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据源编码（deleted=0 内应用层唯一） */
    private String dsCode;

    /** 数据源名称 */
    private String dsName;

    /** 能力标签：TIMESERIES 时序 / SINGLE 单值 */
    private String dsType;

    /** 来源：WIDE_TABLE / KPI_RESULT / CUSTOM_SQL */
    private String sourceKind;

    /** 类型化配置 JSON（三形态见 spec §5） */
    private String configJson;

    /** 允许的预设周期 JSON 数组 */
    private String timeParamJson;

    /** ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人工号 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 逻辑删除 0否1是 */
    @TableLogic
    private Integer deleted;
}
