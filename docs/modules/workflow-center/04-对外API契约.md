# 工作流中心 -- 对外 API 契约

> 版本：V1.0 | 最后更新：2026-04-02
> 本文档定义 workflow-center 暴露给其他模块的 Java API 接口契约，所有跨模块调用必须通过这些接口。

---

## 1. WorkflowApi（流程启动与控制）

### 1.1 接口定义

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;

/**
 * 工作流核心 API -- 流程启动与控制
 *
 * <p>所有业务模块通过此接口发起和控制流程实例。
 * 调用方必须在自身事务内调用（嵌入式 Flowable 共享事务）。</p>
 *
 * <p>调用顺序：业务模块先完成 RBAC + DATA_SCOPE + 状态守卫 + 表单校验 + 业务主表保存，
 * 再调用 startProcess()。</p>
 */
public interface WorkflowApi {

    /**
     * 启动流程实例
     *
     * <p>执行逻辑：</p>
     * <ol>
     *   <li>校验 processDefinitionKey 存在且已激活（否则抛 WF-40401）</li>
     *   <li>校验 businessKey 在 biz_process_map 中不存在 RUNNING 记录（否则抛 WF-40901）</li>
     *   <li>调用 IdentityService.setAuthenticatedUserId(startUser)</li>
     *   <li>使用 RuntimeService.createProcessInstanceBuilder() 启动流程</li>
     *   <li>写入 biz_process_map 记录</li>
     * </ol>
     *
     * @param cmd 流程启动命令，不可为 null
     * @return 流程启动响应，包含 processInstanceId、businessKey、firstTaskId
     * @throws BizException WF-40401 流程定义不存在或未激活
     * @throws BizException WF-40901 业务键已存在进行中的流程
     * @throws BizException WF-50001 Flowable 引擎异常
     */
    WorkflowLaunchResp startProcess(StartProcessCmd cmd);

    /**
     * 取消流程实例（仅系统管理员 / 异常兜底使用）
     *
     * <p>执行逻辑：</p>
     * <ol>
     *   <li>校验流程实例存在且为 RUNNING 状态</li>
     *   <li>调用 RuntimeService.deleteProcessInstance(processInstanceId, reason)</li>
     *   <li>更新 biz_process_map: status=CANCELLED, end_time=now</li>
     *   <li>事务提交后发布 workflow.process.completed.v1 事件（result=CANCELLED）</li>
     * </ol>
     *
     * @param processInstanceId 流程实例ID，不可为 null
     * @param reason 取消原因，不可为空
     * @throws BizException WF-40902 流程实例不存在或已结束
     */
    void cancelProcess(String processInstanceId, String reason);
}
```

### 1.2 StartProcessCmd

```java
package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 流程启动命令
 *
 * <p>由业务模块构建，传递给 WorkflowApi.startProcess()。
 * 业务模块在调用前必须已完成所有业务校验和主表保存。</p>
 */
public class StartProcessCmd {

    /**
     * 业务类型
     * 枚举值：LEAD / LOAN / SUPPORT / TOUCH / TARGET_ADJUST / ALLOC_ADJUST
     */
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    /**
     * 业务对象ID
     * 如线索ID、资产投放申请ID、中场支持详情ID等
     */
    @NotBlank(message = "业务ID不能为空")
    private String bizId;

    /**
     * 业务键
     * 格式：BIZ_TYPE:{id}，如 LEAD:12345、LOAN:LA202603060001
     * 在 biz_process_map 中必须唯一
     */
    @NotBlank(message = "业务键不能为空")
    @Size(max = 100, message = "业务键长度不能超过100")
    private String businessKey;

    /**
     * 流程定义 KEY
     * 必须是 Flowable 中已部署且已激活的流程定义
     * V1 可选值：lead_approve_v1 / lead_import_approve_v1 / loan_approve_v1 /
     *           touch_process_v1 / support_simple_v1 / support_complex_v1 /
     *           alloc_adjust_approve_v1 / target_adjust_approve_v1 / lead_delete_approve_v1
     */
    @NotBlank(message = "流程定义KEY不能为空")
    private String processDefinitionKey;

    /**
     * 发起人工号
     * 必须是有效的系统用户工号
     */
    @NotBlank(message = "发起人不能为空")
    private String startUser;

    /**
     * 发起人机构代码
     * 用于候选组解析中的组织边界判定
     */
    @NotBlank(message = "发起人机构不能为空")
    private String startOrgId;

    /**
     * 流程标题
     * 展示在待办列表中，如 "资产投放申请 - XX科技有限公司"
     */
    @NotBlank(message = "流程标题不能为空")
    @Size(max = 200, message = "流程标题长度不能超过200")
    private String title;

    /**
     * 额外流程变量
     * 可选。业务模块可传入流程中需要使用的变量，
     * 如客户类型（用于排他网关路由）、产品ID等。
     * workflow-center 会自动注入 bizType/bizId/businessKey/startUser/startOrgId/title。
     */
    private Map<String, Object> variables;

    // getter / setter 省略
}
```

### 1.3 WorkflowLaunchResp

```java
package com.bank.branch.platform.workflow.api.dto;

/**
 * 流程启动响应
 */
public class WorkflowLaunchResp {

    /**
     * Flowable 流程实例ID
     * 用于后续流程查询、进度图、审批日志等
     */
    private String processInstanceId;

    /**
     * 业务键
     * 与 StartProcessCmd.businessKey 一致，便于调用方记录
     */
    private String businessKey;

    /**
     * 首个任务ID
     * 如果流程启动后立即生成了第一个 userTask，则返回其 taskId。
     * 某些流程可能第一个节点是自动任务（无 userTask），此时为 null。
     */
    private String firstTaskId;

    // getter / setter 省略
}
```

---

## 2. WorkflowQueryApi（流程查询）

### 2.1 接口定义

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.common.model.PageRequest;
import com.bank.branch.platform.common.model.PageResult;
import com.bank.branch.platform.workflow.api.dto.*;

import java.util.List;
import java.util.Optional;

/**
 * 工作流查询 API -- 待办/已办/流程映射/审批日志
 *
 * <p>供业务模块和门户模块查询流程相关信息。
 * 高频接口，建议调用方做适当缓存。</p>
 */
public interface WorkflowQueryApi {

    /**
     * 查询用户待办列表（供门户工作台聚合）
     *
     * <p>查询逻辑：</p>
     * <ol>
     *   <li>解析用户候选组集合（基于本系统权限模型）</li>
     *   <li>查询 assignee=empId OR candidateGroup IN (候选组集合) 的任务</li>
     *   <li>关联 biz_process_map 补充业务信息</li>
     *   <li>计算 SLA 红绿灯状态</li>
     * </ol>
     *
     * @param empId 用户工号
     * @param bizTypeFilter 业务类型过滤（可选，null 表示全部）
     * @param page 分页参数
     * @return 分页待办列表
     */
    PageResult<TaskDTO> queryTasks(String empId, String bizTypeFilter, PageRequest page);

    /**
     * 查询用户已办列表
     *
     * @param empId 用户工号
     * @param bizTypeFilter 业务类型过滤（可选）
     * @param page 分页参数
     * @return 分页已办列表
     */
    PageResult<DoneTaskDTO> queryDoneTasks(String empId, String bizTypeFilter, PageRequest page);

    /**
     * 按业务键查询流程映射
     *
     * @param businessKey 业务键，如 LEAD:12345
     * @return 流程映射信息，未找到时返回 Optional.empty()
     */
    Optional<ProcessMapDTO> getProcessMap(String businessKey);

    /**
     * 按业务类型+ID查询流程映射
     *
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @return 流程映射信息，未找到时返回 Optional.empty()
     */
    Optional<ProcessMapDTO> getProcessMapByBiz(String bizType, String bizId);

    /**
     * 查询流程实例的审批日志
     *
     * @param processInstanceId 流程实例ID
     * @return 审批日志列表，按时间正序排列
     */
    List<ApprovalLogDTO> getApprovalLogs(String processInstanceId);

    /**
     * 按 taskId 获取流程元信息
     *
     * <p>用于 BizType 动态解析场景：
     * 统一办理入口 /api/workflow/tasks/{taskId}/* 需要先获取
     * business_key 来判断 BizType 进行权限校验。</p>
     *
     * @param taskId Flowable 任务ID
     * @return 流程映射信息
     * @throws BizException WF-40902 任务不存在或已完成
     */
    ProcessMapDTO requireProcessMetaByTaskId(String taskId);

    /**
     * 查询待办数量（用于工作台卡片）
     *
     * @param empId 用户工号
     * @return 待办数量
     */
    int countTasks(String empId);

    /**
     * 查询节点表单配置
     *
     * @param processDefinitionKey 流程定义 Key
     * @param nodeKey 节点 Key
     * @return 节点表单配置
     */
    NodeFormConfDTO getNodeFormConf(String processDefinitionKey, String nodeKey);
}
```

---

## 3. WorkflowConfigApi（配置查询）

### 3.1 接口定义

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.NodeCandidateConfDTO;
import com.bank.branch.platform.workflow.api.dto.TimeoutRuleDTO;

import java.util.List;

/**
 * 工作流配置查询 API
 *
 * <p>供管理页面和内部逻辑查询工作流配置。
 * 配置变更频率低，建议使用 Redis 缓存，TTL 30 分钟。</p>
 */
public interface WorkflowConfigApi {

    /**
     * 查询超时规则列表
     *
     * @param processDefinitionKey 流程定义 Key
     * @return 超时规则列表
     */
    List<TimeoutRuleDTO> listTimeoutRules(String processDefinitionKey);

    /**
     * 查询节点候选人配置列表
     *
     * @param processDefinitionKey 流程定义 Key
     * @return 节点候选人配置列表
     */
    List<NodeCandidateConfDTO> listNodeCandidates(String processDefinitionKey);
}
```

---

## 4. WorkflowParticipantService（流程参与者判定）

### 4.1 接口定义

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.RuntimeAccessDTO;

import java.util.Set;

/**
 * 工作流参与者判定服务
 *
 * <p>统一的流程参与者判定入口，不允许业务模块自行实现"谁算参与者"。</p>
 *
 * <p>数据来源边界：</p>
 * <ul>
 *   <li>biz_process_map：当前流程态、业务键映射、当前 assignee、候选组快照</li>
 *   <li>Flowable runtime：当前 assignee/candidate/claim 判定</li>
 *   <li>Flowable history：历史办理人与 identity links 判定</li>
 *   <li>auth-permission-center：当前用户角色/岗位/组织边界与候选组集合</li>
 * </ul>
 *
 * <p>禁止事项：</p>
 * <ul>
 *   <li>业务模块直接联查 ACT_RU_* / ACT_HI_*</li>
 *   <li>业务 SQL 直接硬凑 is_participant</li>
 *   <li>用业务表中的 assigned_emp_id 替代 runtime task 办理权</li>
 * </ul>
 */
public interface WorkflowParticipantService {

    /**
     * 判断是否属于流程参与者可读范围
     *
     * <p>参与者定义：</p>
     * <ul>
     *   <li>流程发起人（start_user）</li>
     *   <li>历史处理人（ACT_HI_TASKINST.ASSIGNEE_）</li>
     *   <li>当前候选人（ACT_RU_IDENTITYLINK）</li>
     * </ul>
     *
     * @param empId 用户工号
     * @param businessKey 业务键
     * @param viewBizType 当前视图的 BizType（用于区分发起侧/承接侧）
     * @return true=是参与者可读，false=不可读
     */
    boolean isParticipant(String empId, String businessKey, String viewBizType);

    /**
     * 判断当前是否可签收/办理/转交
     *
     * <p>返回详细的操作权限判定结果，包含：</p>
     * <ul>
     *   <li>canClaim：是否可签收（用户在候选组中 && 任务未签收）</li>
     *   <li>canApprove：是否可审批（用户是当前 assignee）</li>
     *   <li>canReject：是否可驳回（用户是当前 assignee）</li>
     *   <li>canTransfer：是否可转交（用户是当前 assignee 或任务管理员）</li>
     *   <li>isAssignee：是否当前处理人</li>
     *   <li>isCandidate：是否候选人</li>
     * </ul>
     *
     * @param taskId Flowable 任务ID
     * @param empId 用户工号
     * @return 运行时权限判定结果
     * @throws BizException WF-40902 任务不存在或已完成
     */
    RuntimeAccessDTO resolveRuntimeAccess(String taskId, String empId);

    /**
     * 解析当前用户候选组集合
     *
     * <p>调用 auth-permission-center 获取用户角色/组织信息后，
     * 根据 wf_node_candidate_conf 配置计算用户属于哪些候选组。</p>
     *
     * <p>候选组交集按"当前用户现有组身份"计算，不做历史组身份回放。</p>
     *
     * @param empId 用户工号
     * @return 用户所属候选组集合，如 {"ROLE_CORP_REVIEWER", "ORG_001_MANAGER"}
     */
    Set<String> resolveCandidateGroups(String empId);

    /**
     * 提供参与者可见的业务键集合（用于只读列表/历史查询）
     *
     * <p>当 DATA_SCOPE = WORKFLOW_PARTICIPANT 时，
     * 业务模块调用此接口获取当前用户可见的 businessKey 集合，
     * 用于 SQL IN 条件拼接。</p>
     *
     * @param empId 用户工号
     * @param bizType 业务类型（可选过滤）
     * @return 可见的 businessKey 集合
     */
    Set<String> listReadableBusinessKeys(String empId, String bizType);
}
```

### 4.2 RuntimeAccessDTO

```java
package com.bank.branch.platform.workflow.api.dto;

/**
 * 运行时权限判定结果
 *
 * <p>由 WorkflowParticipantService.resolveRuntimeAccess() 返回，
 * 表示当前用户对指定任务的操作权限。</p>
 */
public class RuntimeAccessDTO {

    /** 是否可签收（用户在候选组中 && 任务未被签收） */
    private boolean canClaim;

    /** 是否可审批（用户是当前 assignee） */
    private boolean canApprove;

    /** 是否可驳回（用户是当前 assignee） */
    private boolean canReject;

    /** 是否可转交（用户是当前 assignee 或任务管理员/系统管理员） */
    private boolean canTransfer;

    /** 是否当前处理人 */
    private boolean isAssignee;

    /** 是否候选人（在候选组中但未签收） */
    private boolean isCandidate;

    // getter / setter 省略
}
```

---

## 5. DTO 完整定义

### 5.1 TaskDTO（待办任务 DTO）

```java
package com.bank.branch.platform.workflow.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办任务 DTO
 * 用于待办列表展示和工作台聚合
 */
public class TaskDTO {

    /** Flowable 任务ID */
    private String taskId;

    /** 流程实例ID */
    private String processInstanceId;

    /** 业务键 */
    private String businessKey;

    /** 业务类型：LEAD / LOAN / SUPPORT / TOUCH 等 */
    private String bizType;

    /** 业务对象ID */
    private String bizId;

    /** 流程标题 */
    private String title;

    /** 发起人工号 */
    private String startUser;

    /** 发起人姓名 */
    private String startUserName;

    /** 发起人机构名称 */
    private String startOrgName;

    /** 流程发起时间 */
    private LocalDateTime startTime;

    /** 节点名称（如"公司部审核"） */
    private String taskName;

    /** 到达节点时间 */
    private LocalDateTime taskCreateTime;

    /** 当前处理人工号（未签收时为 null） */
    private String assignee;

    /** 候选组列表（已签收后为空列表） */
    private List<String> candidateGroups;

    /** SLA 红绿灯状态：GREEN / YELLOW / RED */
    private String slaStatus;

    /** 黄灯预警时间点 */
    private LocalDateTime warningTime;

    /** 红灯超时时间点 */
    private LocalDateTime timeoutTime;

    /** 是否可领取（true = 用户在候选组中且任务未签收） */
    private boolean claimable;

    // getter / setter 省略
}
```

### 5.2 DoneTaskDTO（已办任务 DTO）

```java
package com.bank.branch.platform.workflow.api.dto;

import java.time.LocalDateTime;

/**
 * 已办任务 DTO
 * 用于已办列表展示
 */
public class DoneTaskDTO {

    /** Flowable 任务ID */
    private String taskId;

    /** 流程实例ID */
    private String processInstanceId;

    /** 业务键 */
    private String businessKey;

    /** 业务类型 */
    private String bizType;

    /** 业务对象ID */
    private String bizId;

    /** 流程标题 */
    private String title;

    /** 发起人工号 */
    private String startUser;

    /** 发起人姓名 */
    private String startUserName;

    /** 发起人机构名称 */
    private String startOrgName;

    /** 流程发起时间 */
    private LocalDateTime startTime;

    /** 节点名称 */
    private String taskName;

    /** 到达节点时间 */
    private LocalDateTime taskCreateTime;

    /** 办理完成时间 */
    private LocalDateTime completeTime;

    /** 审批结果：APPROVE / REJECT */
    private String approvalResult;

    /** 办理意见 */
    private String opinion;

    // getter / setter 省略
}
```

### 5.3 ProcessMapDTO（流程映射 DTO）

```java
package com.bank.branch.platform.workflow.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程映射 DTO
 * 对应 biz_process_map 表，提供业务键与流程实例的映射信息
 */
public class ProcessMapDTO {

    /** 映射记录ID */
    private String id;

    /** 业务键 */
    private String businessKey;

    /** 业务类型 */
    private String bizType;

    /** 业务对象ID */
    private String bizId;

    /** 流程定义 Key */
    private String processDefinitionKey;

    /** 流程实例ID */
    private String processInstanceId;

    /** 当前任务ID（运行中才有） */
    private String currentTaskId;

    /** 当前节点 BPMN Node ID */
    private String currentNodeId;

    /** 当前处理人工号 */
    private String currentAssignee;

    /** 当前节点候选组 */
    private List<String> candidateGroups;

    /** 发起人工号 */
    private String startUser;

    /** 流程发起时间 */
    private LocalDateTime startTime;

    /** 流程结束时间 */
    private LocalDateTime endTime;

    /** 流程状态：RUNNING / COMPLETED / CANCELLED */
    private String processStatus;

    // getter / setter 省略
}
```

### 5.4 ApprovalLogDTO（审批日志 DTO）

```java
package com.bank.branch.platform.workflow.api.dto;

import java.time.LocalDateTime;

/**
 * 审批日志 DTO
 * 用于展示流程各节点的审批历史
 */
public class ApprovalLogDTO {

    /** 节点 BPMN Node ID */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 操作人工号 */
    private String operator;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作人机构名称 */
    private String operatorOrgName;

    /** 操作类型：SUBMIT / APPROVE / REJECT / CLAIM / TRANSFER */
    private String action;

    /** 操作意见 */
    private String opinion;

    /** 操作时间 */
    private LocalDateTime operateTime;

    // getter / setter 省略
}
```

### 5.5 ProcessDiagramDTO（流程进度图 DTO）

```java
package com.bank.branch.platform.workflow.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程进度图 DTO
 * 用于前端渲染流程进度可视化
 */
public class ProcessDiagramDTO {

    /** 流程实例ID */
    private String processInstanceId;

    /** 流程定义 Key */
    private String processDefinitionKey;

    /** 节点列表（按流程定义顺序） */
    private List<DiagramNodeDTO> nodes;

    /**
     * 节点信息
     */
    public static class DiagramNodeDTO {

        /** 节点 BPMN Node ID */
        private String nodeKey;

        /** 节点名称 */
        private String nodeName;

        /** 节点类型：startEvent / userTask / exclusiveGateway / endEvent */
        private String nodeType;

        /** 节点状态：COMPLETED / ACTIVE / PENDING */
        private String status;

        /** 处理人工号 */
        private String assignee;

        /** 处理人姓名 */
        private String assigneeName;

        /** 节点开始时间 */
        private LocalDateTime startTime;

        /** 节点结束时间 */
        private LocalDateTime endTime;

        // getter / setter 省略
    }

    // getter / setter 省略
}
```

### 5.6 NodeFormConfDTO（节点表单配置 DTO）

```java
package com.bank.branch.platform.workflow.api.dto;

import java.util.List;

/**
 * 节点表单配置 DTO
 * 描述某个节点可展示/可编辑/必填的字段集合
 */
public class NodeFormConfDTO {

    /** 流程定义 Key */
    private String processDefinitionKey;

    /** 节点 Key */
    private String nodeKey;

    /** 表单字段定义列表（全量字段） */
    private List<FormFieldDTO> formFields;

    /** 可编辑字段 Key 列表 */
    private List<String> editableFields;

    /** 必填字段 Key 列表 */
    private List<String> requiredFields;

    /**
     * 表单字段定义
     */
    public static class FormFieldDTO {

        /** 字段键名 */
        private String fieldKey;

        /** 字段显示名称 */
        private String fieldLabel;

        /** 字段类型：text / number / select / date / textarea */
        private String fieldType;

        // getter / setter 省略
    }

    // getter / setter 省略
}
```

### 5.7 NodeCandidateConfDTO（节点候选人配置 DTO）

```java
package com.bank.branch.platform.workflow.api.dto;

import java.util.List;

/**
 * 节点候选人配置 DTO
 */
public class NodeCandidateConfDTO {

    /** 配置ID */
    private String id;

    /** 流程定义 Key */
    private String processDefinitionKey;

    /** 节点 Key */
    private String nodeKey;

    /** 候选类型：ROLE / ORG / USER */
    private String candidateType;

    /** 候选值列表（角色编码 / 机构代码 / 用户工号） */
    private List<String> candidateValue;

    // getter / setter 省略
}
```

### 5.8 TimeoutRuleDTO（超时规则 DTO）

```java
package com.bank.branch.platform.workflow.api.dto;

/**
 * 超时规则 DTO
 */
public class TimeoutRuleDTO {

    /** 规则ID */
    private String id;

    /** 流程定义 Key */
    private String processDefinitionKey;

    /** 节点 Key */
    private String nodeKey;

    /** 黄灯预警阈值（工作小时数） */
    private Integer warningHours;

    /** 红灯超时阈值（工作小时数） */
    private Integer timeoutHours;

    // getter / setter 省略
}
```

---

## 6. 调用约束

### 6.1 同步调用约束

| 约束项 | 说明 |
|:---|:---|
| 调用方式 | 所有 API 为 Java 方法调用（嵌入式模块间调用），**不通过 HTTP** |
| 依赖强度 | 所有 API 为**同步强依赖（S）** |
| 事务边界 | `startProcess()` 必须在业务模块的事务内调用（共用数据源，同事务提交） |
| 异常传播 | API 抛出的 `BizException` 会导致调用方事务回滚 |
| 线程安全 | 所有 API 实现类为无状态 Spring Bean，线程安全 |

### 6.2 调用频率与缓存建议

| API 方法 | 调用频率 | 缓存建议 |
|:---|:---|:---|
| queryTasks() | 高频（工作台每次加载） | 调用方可做 5s 级别前端缓存；服务端候选组解析结果缓存 5 分钟 |
| countTasks() | 高频（工作台卡片） | 调用方可做 30s 级别缓存 |
| getProcessMap() | 中频 | 无需缓存（单次查询） |
| getApprovalLogs() | 低频 | 无需缓存 |
| startProcess() | 低频 | 不可缓存（写操作） |
| resolveCandidateGroups() | 高频（待办查询依赖） | Redis 缓存 5 分钟，角色变更时主动失效 |
| listReadableBusinessKeys() | 中频 | 无需缓存（数据实时性要求高） |
| getNodeFormConf() | 中频 | Redis 缓存 30 分钟，配置变更时主动失效 |

### 6.3 禁止的调用方式

| 禁止事项 | 说明 |
|:---|:---|
| 直接调用 Flowable API | 业务模块不得直接注入 RuntimeService / TaskService / HistoryService 等 |
| 直接查询 ACT_* 表 | 业务模块不得在 Mapper XML 中直接 JOIN 或 SELECT Flowable 引擎表 |
| 直接依赖 entity/mapper | 业务模块不得直接使用 workflow-center 的 entity 或 mapper |
| 绕过事务调用 startProcess | startProcess 必须在调用方事务内执行，不得在事务外或异步线程中调用 |

---

## 7. 领域事件

### 7.1 workflow.process.completed.v1

#### 事件定义

```java
package com.bank.branch.platform.workflow.event;

import java.time.LocalDateTime;

/**
 * 流程完成事件
 *
 * <p>发布时机：流程实例结束（正常完成 / 取消），事务提交后。</p>
 * <p>发布机制：@TransactionalEventListener(phase = AFTER_COMMIT)</p>
 *
 * <p>消费方职责：</p>
 * <ul>
 *   <li>customer-marketing-center：回写线索状态（APPROVED/REJECTED）、触达状态</li>
 *   <li>business-application-center：回写资产投放/中场支持状态</li>
 *   <li>performance-engine-center：回写目标修正状态</li>
 *   <li>portal-content-center：生成流程结束通知</li>
 * </ul>
 */
public class ProcessCompletedEvent {

    /** 流程实例ID */
    private String processInstanceId;

    /** 业务键 */
    private String businessKey;

    /** 业务类型 */
    private String bizType;

    /** 业务对象ID */
    private String bizId;

    /** 流程定义 Key */
    private String processDefinitionKey;

    /** 发起人工号 */
    private String startUser;

    /** 流程结果：APPROVED / REJECTED / CANCELLED */
    private String result;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 最后审批人工号 */
    private String lastApprover;

    /** 最后审批意见 */
    private String lastOpinion;

    // getter / setter 省略
}
```

#### 事件 Payload 示例

```json
{
  "processInstanceId": "PRC_001",
  "businessKey": "LOAN:LA202603060001",
  "bizType": "LOAN",
  "bizId": "LA202603060001",
  "processDefinitionKey": "loan_approve_v1",
  "startUser": "E10001",
  "result": "APPROVED",
  "endTime": "2026-03-06T15:30:00+08:00",
  "lastApprover": "E20001",
  "lastOpinion": "同意"
}
```

#### 消费方示例

```java
// customer-marketing-center 消费示例
@Component
public class LeadApprovalResultHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProcessCompleted(ProcessCompletedEvent event) {
        if (!"LEAD".equals(event.getBizType())) {
            return;
        }
        // 根据 event.getResult() 更新线索状态
        // APPROVED -> lead_status = APPROVED
        // REJECTED -> lead_status = REJECTED
    }
}
```

---

### 7.2 workflow.task.created.v1

#### 事件定义

```java
package com.bank.branch.platform.workflow.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务创建事件
 *
 * <p>发布时机：新待办任务创建，事务提交后。</p>
 * <p>消费方：portal-content-center（生成待办通知）</p>
 */
public class TaskCreatedEvent {

    /** Flowable 任务ID */
    private String taskId;

    /** 流程实例ID */
    private String processInstanceId;

    /** 业务键 */
    private String businessKey;

    /** 业务类型 */
    private String bizType;

    /** 流程标题 */
    private String title;

    /** 节点名称 */
    private String taskName;

    /** 当前处理人工号（已指定 assignee 时） */
    private String assignee;

    /** 候选组列表（候选组任务时） */
    private List<String> candidateGroups;

    /** 任务创建时间 */
    private LocalDateTime createTime;

    // getter / setter 省略
}
```

---

### 7.3 workflow.task.completed.v1

#### 事件定义

```java
package com.bank.branch.platform.workflow.event;

import java.time.LocalDateTime;

/**
 * 任务完成事件
 *
 * <p>发布时机：单个任务节点完成，事务提交后。</p>
 * <p>消费方：portal-content-center（更新/清除待办通知）</p>
 */
public class TaskCompletedEvent {

    /** Flowable 任务ID */
    private String taskId;

    /** 流程实例ID */
    private String processInstanceId;

    /** 业务键 */
    private String businessKey;

    /** 业务类型 */
    private String bizType;

    /** 节点名称 */
    private String taskName;

    /** 操作人工号 */
    private String operator;

    /** 操作类型：APPROVE / REJECT */
    private String action;

    /** 完成时间 */
    private LocalDateTime completeTime;

    // getter / setter 省略
}
```

---

## 8. 调用时序图

### 8.1 流程发起时序

```
业务模块                  WorkflowApi              Flowable                  biz_process_map
  |                           |                       |                           |
  |--- RBAC/SCOPE/状态校验 -->|                       |                           |
  |--- 保存业务主表 -------->|                       |                           |
  |--- startProcess(cmd) --->|                       |                           |
  |                           |--- 校验 businessKey -->|                           |
  |                           |--- 校验流程定义 ------->|                           |
  |                           |--- setAuthUserId ----->|                           |
  |                           |--- createProcessInstance -->|                      |
  |                           |                       |<-- processInstanceId ------|
  |                           |--- 写入映射 ---------------------------------------->|
  |                           |<-- LaunchResp ---------|                           |
  |<-- 回写业务状态 ----------|                       |                           |
  |--- 事务提交 ------------->|                       |                           |
```

### 8.2 签收时序

```
前端                Controller           TaskOperationService    Flowable           biz_process_map
  |                     |                        |                  |                    |
  |-- POST claim ------>|                        |                  |                    |
  |                     |-- claimTask() -------->|                  |                    |
  |                     |                        |-- 查询 Task ----->|                    |
  |                     |                        |<-- Task 信息 -----|                    |
  |                     |                        |-- 校验候选人 ---->|                    |
  |                     |                        |-- claim() ------->|                    |
  |                     |                        |<-- success -------|                    |
  |                     |                        |-- 更新 assignee ---------------------->|
  |                     |<-- success ------------|                  |                    |
  |<-- 200 OK ----------|                        |                  |                    |
```

### 8.3 待办查询时序

```
前端              Controller          TodoQueryService      CandidateResolver    auth-perm-center   Flowable
  |                  |                      |                     |                    |               |
  |-- GET tasks ---->|                      |                     |                    |               |
  |                  |-- queryTasks() ----->|                     |                    |               |
  |                  |                      |-- resolveCandidateGroups() -->|           |               |
  |                  |                      |                     |-- getUserRoles() -->|               |
  |                  |                      |                     |<-- roles ----------|               |
  |                  |                      |<-- candidateGroups -|                    |               |
  |                  |                      |-- 查询 assignee=empId 的任务 --------------------------->|
  |                  |                      |-- 查询 candidateGroup IN (...) 的任务 ------------------>|
  |                  |                      |<-- tasks ------------------------------------------------|
  |                  |                      |-- 关联 biz_process_map 补充业务信息                       |
  |                  |                      |-- 计算 SLA 红绿灯                                        |
  |                  |<-- PageResult -------|                     |                    |               |
  |<-- 200 JSON -----|                      |                     |                    |               |
```
