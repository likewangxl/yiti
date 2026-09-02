package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bank.branch.platform.redengine.api.dto.ReTaskStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 红色引擎任务定义。 */
@Data
@TableName("RE_TASK")
public class ReTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 任务业务编号。 */
    private String taskNo;
    /** 任务标题。 */
    private String title;
    /** 任务说明。 */
    private String description;
    /** 定时或临时，数据库值为 SCHEDULED/TEMPORARY。 */
    private String nature;
    /** 任务类型，FOUR_DIMENSION 表示四大维度材料上报。 */
    private String typeCode;
    /** ALL_BRANCHES/SPECIFIED_BRANCHES/SPECIFIED_EMPLOYEES。 */
    private String audienceType;
    /** 定时任务周期；临时任务为空。 */
    private String cycle;
    /** 周期窗口持续天数。 */
    private Integer durationDays;
    /** 临时任务开始时间。 */
    private LocalDateTime startAt;
    /** 临时任务结束时间。 */
    private LocalDateTime endAt;
    /** 定时任务生效起始日期。 */
    private LocalDate effectiveFrom;
    /** 定时任务生效结束日期。 */
    private LocalDate effectiveTo;
    /** 是否要求上传附件，0 否、1 是。 */
    private Integer requiresFile;
    /** 任务定义状态。 */
    private ReTaskStatus status;
    /** 发布时间。 */
    private LocalDateTime publishedAt;
    /** 发布人平台用户 ID。 */
    private String publishedBy;
    /** 创建人平台用户 ID。 */
    private String createdBy;
    /** 更新人平台用户 ID。 */
    private String updatedBy;
    /** 乐观锁版本。 */
    @Version
    private Integer versionNo;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
