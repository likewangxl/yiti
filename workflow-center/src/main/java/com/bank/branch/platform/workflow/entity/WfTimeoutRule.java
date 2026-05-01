package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程超时规则实体，对应 wf_timeout_rule 表。
 * <p>
 * 配置每个流程节点的超时阈值，用于红绿灯状态计算：
 * 已耗工时 < warning_hours -> 绿灯（正常）；
 * warning_hours <= 已耗工时 < timeout_hours -> 黄灯（预警）；
 * 已耗工时 >= timeout_hours -> 红灯（超时）。
 * </p>
 */
@Data
@TableName("WF_TIMEOUT_RULE")
public class WfTimeoutRule {

    /** 规则ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 流程定义KEY，对应 process_definition_key */
    private String processDefinitionKey;

    /** 节点KEY，对应 node_key */
    private String nodeKey;

    /** 超时小时数（红灯阈值），对应 timeout_hours */
    private Integer timeoutHours;

    /** 预警小时数（黄灯阈值），对应 warning_hours */
    private Integer warningHours;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
