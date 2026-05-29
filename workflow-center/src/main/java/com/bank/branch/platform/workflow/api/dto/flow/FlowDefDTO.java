package com.bank.branch.platform.workflow.api.dto.flow;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流程定义列表/详情 DTO。
 * <p>
 * 用于流程定义列表查询与详情展示，不包含节点/边图形数据。
 * 图形数据通过 {@link FlowGraphDTO} 单独获取。
 * </p>
 */
@Data
public class FlowDefDTO {

    /** 流程定义ID（UUID主键） */
    private String id;

    /** 流程唯一标识键 */
    private String flowKey;

    /** 关联业务类型（如 TARGET_ADJUST / ALLOC_ADJUST） */
    private String bizType;

    /** 流程名称 */
    private String name;

    /** 状态：DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** 版本号，每次发布自增 */
    private Integer version;

    /** 已部署的 Flowable 流程定义 KEY */
    private String deployedProcDefKey;

    /** 是否为只读导入：0-否，1-是 */
    private Integer isReadonlyImport;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;
}
