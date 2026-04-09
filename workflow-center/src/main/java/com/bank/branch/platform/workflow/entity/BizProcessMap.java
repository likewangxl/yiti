package com.bank.branch.platform.workflow.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务流程映射实体，对应 biz_process_map 表。
 * <p>
 * 桥接业务实体与 Flowable 流程实例，实现"一个业务实体最多对应一个运行中流程"的约束。
 * business_key 格式为 {BIZ_TYPE}:{biz_id}，如 LOAN:LA202603060001。
 * process_status 枚举值：RUNNING-运行中, COMPLETED-已完成, CANCELLED-已取消。
 * </p>
 */
@Data
public class BizProcessMap {

    /** 映射ID（UUID主键），对应 id */
    private String id;

    /** 业务键（格式：BIZ_TYPE:{id}），对应 business_key */
    private String businessKey;

    /** 业务类型，对应 biz_type */
    private String bizType;

    /** 业务ID，对应 biz_id */
    private String bizId;

    /** 流程定义KEY，对应 process_definition_key */
    private String processDefinitionKey;

    /** Flowable流程实例ID，对应 process_instance_id */
    private String processInstanceId;

    /** 发起人工号，对应 start_user */
    private String startUser;

    /** 当前处理人工号，对应 current_assignee */
    private String currentAssignee;

    /** 候选组列表（JSON数组），对应 candidate_groups */
    private String candidateGroups;

    /** 流程状态：RUNNING/COMPLETED/CANCELLED，对应 process_status */
    private String processStatus;

    /** 流程标题，对应 title */
    private String title;

    /** 发起时间，对应 start_time */
    private LocalDateTime startTime;

    /** 结束时间，对应 end_time */
    private LocalDateTime endTime;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
