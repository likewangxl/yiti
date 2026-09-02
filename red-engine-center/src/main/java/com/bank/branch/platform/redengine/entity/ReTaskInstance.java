package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bank.branch.platform.redengine.api.dto.ReTaskInstanceStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务的周期/临时实例。 */
@Data
@TableName("RE_TASK_INSTANCE")
public class ReTaskInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    /** 周期唯一键，例如 2024-02、2024-Q2。 */
    private String periodKey;
    /** 有效窗口开始时间，北京时间自然日的起点。 */
    private LocalDateTime windowStartAt;
    /** 有效窗口结束时间，北京时间自然日的终点。 */
    private LocalDateTime windowEndAt;
    private ReTaskInstanceStatus status;
    private LocalDateTime generatedAt;
    private LocalDateTime closedAt;
    @Version
    private Integer versionNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
