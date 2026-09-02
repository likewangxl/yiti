package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 新任务实例与现有 RE_SUBMIT 四维材料上报的关联。 */
@Data
@TableName("RE_TASK_RE_SUBMIT_REL")
public class ReTaskReSubmitRel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long taskInstanceId;
    private Long assignmentId;
    private Long reSubmitId;
    private Long taskSubmissionId;
    private String dimensionCode;
    private String itemCode;
    private String createdBy;
    private LocalDateTime createTime;
}
