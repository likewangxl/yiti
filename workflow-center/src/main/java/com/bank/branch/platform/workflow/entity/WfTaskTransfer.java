package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务转交待认领生命周期实体，对应 WF_TASK_TRANSFER 表。
 * <p>记录一次任务转交从发起到被接收人接受/拒绝/发起人撤销的完整状态流转。</p>
 */
@Data
@TableName("WF_TASK_TRANSFER")
public class WfTaskTransfer {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String processInstanceId;
    private String taskId;
    private String businessKey;
    private String bizType;
    private String nodeKey;
    private String nodeName;
    private String fromEmpId;
    private String initiatorEmpId;
    private String toEmpId;
    private String orgCode;
    private String status;
    private String transferReason;
    private String rejectReason;
    private LocalDateTime initiatedTime;
    private LocalDateTime decidedTime;
}
