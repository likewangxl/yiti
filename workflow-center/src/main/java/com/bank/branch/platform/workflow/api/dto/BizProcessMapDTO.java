package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务流程映射 DTO
 * <p>
 * 用于跨模块传输 biz_process_map 信息，隔离内部实体。
 * </p>
 */
@Data
public class BizProcessMapDTO {

    /** 映射ID */
    private String id;

    /** 业务键 */
    private String businessKey;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private String bizId;

    /** 流程定义KEY */
    private String processDefinitionKey;

    /** 流程实例ID */
    private String processInstanceId;

    /** 流程状态：RUNNING / COMPLETED / CANCELLED */
    private String processStatus;

    /** 流程标题 */
    private String title;

    /** 发起人工号 */
    private String startUser;

    /** 发起人机构代码 */
    private String startOrgId;

    /** 当前处理人工号 */
    private String currentAssignee;

    /** 候选组列表 */
    private List<String> candidateGroups;

    /** 发起时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
