package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN 实体 —— 大屏定义.
 */
@Data
@TableName("RPT_SCREEN")
public class RptScreen {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 大屏编码（deleted=0 内应用层唯一） */
    private String screenCode;

    /** 大屏名称 */
    private String screenName;

    /** 视角：PROVINCE / BRANCH / PERSON */
    private String viewLevel;

    /** 主题变量覆盖 JSON（一期留空） */
    private String themeJson;

    /** ACTIVE / DISABLED */
    private String status;

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
