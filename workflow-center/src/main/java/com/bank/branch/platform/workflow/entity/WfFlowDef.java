package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流程设计器-流程定义实体，对应 WF_FLOW_DEF 表。
 * <p>
 * 每条记录代表一套可视化设计的审批流程定义（草稿/已发布/已停用）。
 * 发布时由 FlowableDeployService 把节点/连线编译为 BPMN 并部署到 Flowable，
 * 部署成功后回填 deployed_proc_def_key / deployed_proc_def_id。
 * </p>
 */
@Data
@TableName("WF_FLOW_DEF")
public class WfFlowDef {

    /** 流程定义ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 流程唯一标识键（业务侧命名，全局唯一），对应 flow_key */
    private String flowKey;

    /** 关联业务类型（如 TARGET_ADJUST / ALLOC_ADJUST），对应 biz_type */
    private String bizType;

    /** 流程名称，对应 name */
    private String name;

    /** 流程描述，对应 description */
    private String description;

    /** 状态：DRAFT/PUBLISHED/DISABLED，对应 status */
    private String status;

    /** 版本号，每次发布自增，对应 version */
    private Integer version;

    /** 已部署的 Flowable 流程定义 KEY，对应 deployed_proc_def_key */
    private String deployedProcDefKey;

    /** 已部署的 Flowable 流程定义 ID（含版本后缀），对应 deployed_proc_def_id */
    private String deployedProcDefId;

    /** 来源 Flowable procDef KEY（导入场景），对应 source_proc_def_key */
    private String sourceProcDefKey;

    /** 是否为只读导入：0-否，1-是，对应 is_readonly_import */
    private Integer isReadonlyImport;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 创建人工号，对应 created_by */
    private String createdBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 更新人工号，对应 updated_by */
    private String updatedBy;
}
