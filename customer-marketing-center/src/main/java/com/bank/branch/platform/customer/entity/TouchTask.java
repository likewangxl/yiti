package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 正式触达任务实体，对应 MARKETING_TOUCH_TASK。 */
@Data
@TableName("MARKETING_TOUCH_TASK")
public class TouchTask {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long custId;
    private String sourceType;
    private Long sourceBizId;
    private String orgId;
    private String assigneeEmpId;
    private String taskType;
    private String taskStatus;
    private LocalDateTime planFinishTime;
    private LocalDateTime warningTime;
    private String slaStatus;
    private LocalDateTime successTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer lockVersion;

    /** 页面展示字段，不落库：有效营销客户名称。 */
    @TableField(exist = false)
    private String custName;

    /** 页面展示字段，不落库：兼容演示页面使用的 customerName 命名。 */
    @TableField(exist = false)
    private String customerName;

    /** 页面展示字段，不落库：任务下所有有效日志登记的参与人工号。 */
    @TableField(exist = false)
    private List<String> participantEmpIds;

    /** 页面展示字段，不落库：任务下有效工作日志数量。 */
    @TableField(exist = false)
    private Long logCount;

    /** 页面查询内部字段，不落库：参与人工号由 SQL 聚合后以逗号分隔传递。 */
    @TableField(exist = false)
    @JsonIgnore
    private String participantEmpIdsText;

    /** 页面查询内部字段，不落库：由主执行人登记、可补录日志的协同人工号。 */
    @TableField(exist = false)
    @JsonIgnore
    private String eligibleCollaboratorEmpIdsText;

    /** 当前查看人是否可追加触达日志，由服务端权限口径计算。 */
    @TableField(exist = false)
    private Boolean canWriteLog;

    /** 当前查看人是否可完成或取消任务，由服务端权限口径计算。 */
    @TableField(exist = false)
    private Boolean canOperateTask;
}
