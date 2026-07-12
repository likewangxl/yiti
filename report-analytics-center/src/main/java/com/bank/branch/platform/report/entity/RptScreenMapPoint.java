package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RPT_SCREEN_MAP_POINT 实体 —— 大屏地图支行点位.
 */
@Data
@TableName("RPT_SCREEN_MAP_POINT")
public class RptScreenMapPoint {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 支行机构号（唯一） */
    private String orgCode;

    /** 支行名称 */
    private String orgName;

    /** 经度 */
    private BigDecimal lng;

    /** 纬度 */
    private BigDecimal lat;

    /** 点击跳转目标屏编码 */
    private String targetScreenCode;

    /** ACTIVE / DISABLED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
