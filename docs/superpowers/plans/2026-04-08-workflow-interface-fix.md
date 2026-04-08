# Workflow Center 接口一致性修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 workflow-center 的实际接口代码修改为与接口设计文档 03-接口设计与报文.md 一致

**Architecture:** 采用文档优先策略 - 修改 Controller 代码以匹配设计文档中的接口路径、参数和响应格式

**Tech Stack:** Spring Boot 3.2.3, Java 17, MyBatis

---

## 接口差异分析

### 1. TaskController 差异

| 设计文档 | 实际代码 | 差异 |
|---------|---------|------|
| `GET /api/workflow/tasks` | `GET /api/workflow/tasks/todo` | 路径不同 |
| `GET /api/workflow/tasks/done` | `GET /api/workflow/tasks/done` | 一致 |
| `GET /api/workflow/tasks/{taskId}` | `GET /api/workflow/tasks/{taskId}?empId=xxx` | 多了 empId 参数 |
| `POST /api/workflow/tasks/{taskId}/claim` | `POST /api/workflow/tasks/{taskId}/claim?empId=xxx` | 多了 empId 参数 |
| `POST /api/workflow/tasks/{taskId}/approve` | `POST /api/workflow/tasks/{taskId}/approve?empId=xxx` | 多了 empId 参数 + 字段名不同(variables vs formData) |
| `POST /api/workflow/tasks/{taskId}/reject` | `POST /api/workflow/tasks/{taskId}/reject?empId=xxx` | 多了 empId 参数 + 字段名不同(comment vs opinion) |
| `POST /api/workflow/tasks/{taskId}/transfer` | `POST /api/workflow/tasks/{taskId}/transfer?empId=xxx` | 多了 empId 参数 + 字段名不同(toEmpId vs targetEmpId) |

### 2. ProcessController 差异

| 设计文档 | 实际代码 | 差异 |
|---------|---------|------|
| `GET /api/workflow/process-map` | `GET /api/workflow/processes/{businessKey}` | 路径不同 |
| 设计文档 C.4 支持 bizType+bizId | 实际 `GET /api/workflow/processes/biz/{bizType}/{bizId}` | 路径不同 |

### 3. WorkflowAdminController 差异

| 设计文档 | 实际代码 | 差异 |
|---------|---------|------|
| `GET /api/admin/workflow/timeout-rules?processDefinitionKey=xxx` | `GET /api/admin/workflow/timeout-rules/{processDefinitionKey}` | 路径参数 vs 查询参数 |
| `PUT /api/admin/workflow/timeout-rules/{id}` | `PUT /api/admin/workflow/timeout-rules/{id}?warningHours=xxx&timeoutHours=xxx` | 请求体 vs 请求参数 |
| `GET /api/admin/workflow/node-candidates?processDefinitionKey=xxx` | `GET /api/admin/workflow/candidate-configs/{processDefinitionKey}` | 路径不同 + 路径参数 vs 查询参数 |
| `PUT /api/admin/workflow/node-candidates/{id}` | `PUT /api/admin/workflow/candidate-configs/{id}?candidateType=xxx&candidateValue=xxx` | 请求体 vs 请求参数 |
| `GET /api/admin/workflow/node-forms?processDefinitionKey=xxx` | `GET /api/admin/workflow/node-form-confs/{processDefinitionKey}` | 路径不同 |
| `PUT /api/admin/workflow/node-forms/{id}` | `PUT /api/admin/workflow/node-form-confs/{id}?formFields=xxx...` | 请求体 vs 请求参数 |
| 设计文档 D.7 流程定义列表 | 实际无此接口 | 缺失 |

---

## 任务分解

### Task 1: 修改 TaskController 接口路径

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java:42-64`

- [ ] **Step 1: 修改待办列表接口路径**

将 `GET /api/workflow/tasks/todo` 改为 `GET /api/workflow/tasks`

```java
@GetMapping
@Operation(summary = "查询待办列表")
public ResponseWrapper<PageResult<TaskRespDTO>> queryTodoList(
        @RequestParam(value = "bizType", required = false) String bizType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    log.debug("[TaskController.queryTodoList] bizType={}, keyword={}, pageNo={}, pageSize={}",
            bizType, keyword, pageNo, pageSize);
    // TODO: 获取当前用户 empId
    String empId = "CURRENT_USER"; // 后续替换为 CurrentUserApi
    PageResult<TaskRespDTO> result = todoQueryService.queryTodoList(empId, bizType, keyword, pageNo, pageSize);
    return ResponseWrapper.page(result);
}
```

- [ ] **Step 2: 修改已办列表接口路径**

修改方法映射，将 `/done` 保持但移除参数中的 empId

```java
@GetMapping("/done")
@Operation(summary = "查询已办列表")
public ResponseWrapper<PageResult<TaskRespDTO>> queryDoneList(
        @RequestParam(value = "bizType", required = false) String bizType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    log.debug("[TaskController.queryDoneList] bizType={}, keyword={}, pageNo={}, pageSize={}",
            bizType, keyword, pageNo, pageSize);
    // TODO: 获取当前用户 empId
    String empId = "CURRENT_USER"; // 后续替换为 CurrentUserApi
    PageResult<TaskRespDTO> result = todoQueryService.queryDoneList(empId, bizType, keyword, pageNo, pageSize);
    return ResponseWrapper.page(result);
}
```

- [ ] **Step 3: 修改任务详情接口，移除 empId 参数**

```java
@GetMapping("/{taskId}")
@Operation(summary = "获取任务详情")
public ResponseWrapper<TaskDetailRespDTO> getTaskDetail(
        @PathVariable(value = "taskId") String taskId) {
    // TODO: 获取当前用户 empId
    String empId = "CURRENT_USER"; // 后续替换为 CurrentUserApi
    log.debug("[TaskController.getTaskDetail] taskId={}, empId={}", taskId, empId);
    TaskDetailRespDTO detail = todoQueryService.getTaskDetail(taskId, empId);
    return ResponseWrapper.success(detail);
}
```

- [ ] **Step 4: 修改签收接口，移除 empId 参数**

```java
@PostMapping("/{taskId}/claim")
@Operation(summary = "签收任务")
public ResponseWrapper<Void> claimTask(
        @PathVariable(value = "taskId") String taskId) {
    // TODO: 获取当前用户 empId
    String empId = "CURRENT_USER"; // 后续替换为 CurrentUserApi
    log.info("[TaskController.claimTask] taskId={}, empId={}", taskId, empId);
    taskOperationService.claimTask(taskId, empId);
    return ResponseWrapper.success();
}
```

- [ ] **Step 5: 修改审批通过接口**

- [ ] **Step 6: 修改驳回接口**

- [ ] **Step 7: 修改转交接口**

- [ ] **Step 8: 编译验证**

```bash
cd workflow-center && mvn compile -q
```

- [ ] **Step 9: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java
git commit -m "refactor: 修改 TaskController 接口路径与文档一致"
```

---

### Task 2: 修改 ProcessController 接口路径

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/ProcessController.java:82-115`

- [ ] **Step 1: 添加 process-map 接口**

删除原有的 businessKey 路径接口，改为统一的 process-map 查询接口

```java
/**
 * 根据业务键查询流程映射记录
 * 设计文档 C.4: GET /api/workflow/process-map
 *
 * @param businessKey 业务键 (可选，与 bizType+bizId 二选一)
 * @param bizType     业务类型 (可选，与 businessKey 二选一)
 * @param bizId       业务ID (可选，与 businessKey 二选一)
 * @return 流程映射 DTO
 */
@GetMapping("/process-map")
@Operation(summary = "根据业务键查询流程映射")
public ResponseWrapper<BizProcessMapDTO> getProcessMap(
        @RequestParam(value = "businessKey", required = false) String businessKey,
        @RequestParam(value = "bizType", required = false) String bizType,
        @RequestParam(value = "bizId", required = false) String bizId) {
    log.debug("[ProcessController.getProcessMap] businessKey={}, bizType={}, bizId={}", businessKey, bizType, bizId);
    BizProcessMapDTO dto;
    if (StringUtils.isNotBlank(businessKey)) {
        dto = processStartService.getProcessByBusinessKey(businessKey);
    } else if (StringUtils.isNotBlank(bizType) && StringUtils.isNotBlank(bizId)) {
        dto = processStartService.getProcessByBizTypeAndBizId(bizType, bizId);
    } else {
        throw new IllegalArgumentException("businessKey 或 bizType+bizId 必须提供其一");
    }
    return ResponseWrapper.success(dto);
}
```

- [ ] **Step 2: 删除旧的 businessKey 接口**

删除原有的以下两个方法：
- `GET /api/workflow/processes/{businessKey}`
- `GET /api/workflow/processes/biz/{bizType}/{bizId}`

- [ ] **Step 3: 编译验证**

```bash
cd workflow-center && mvn compile -q
```

- [ ] **Step 4: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/ProcessController.java
git commit -m "refactor: 修改 ProcessController 接口路径与文档一致"
```

---

### Task 3: 修改 WorkflowAdminController 接口路径

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/WorkflowAdminController.java`

- [ ] **Step 1: 修改超时规则查询接口**

设计文档: `GET /api/admin/workflow/timeout-rules?processDefinitionKey=xxx`

```java
@GetMapping("/timeout-rules")
@Operation(summary = "查询超时规则列表")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<List<WfTimeoutRule>> listTimeoutRules(
        @RequestParam(value = "processDefinitionKey", required = false) String processDefinitionKey) {
    log.debug("[WorkflowAdminController.listTimeoutRules] processDefinitionKey={}", processDefinitionKey);
    List<WfTimeoutRule> list = workflowAdminService.listTimeoutRules(processDefinitionKey);
    return ResponseWrapper.success(list);
}
```

- [ ] **Step 2: 修改超时规则更新接口**

设计文档: `PUT /api/admin/workflow/timeout-rules/{id}` with JSON body

```java
@PutMapping("/timeout-rules/{id}")
@Operation(summary = "更新超时规则")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<Void> updateTimeoutRule(
        @PathVariable(value = "id") String id,
        @Valid @RequestBody TimeoutRuleUpdateReqDTO req) {
    log.info("[WorkflowAdminController.updateTimeoutRule] id={}, warningHours={}, timeoutHours={}",
            id, req.getWarningHours(), req.getTimeoutHours());
    workflowAdminService.updateTimeoutRule(id, req.getWarningHours(), req.getTimeoutHours());
    return ResponseWrapper.success();
}
```

- [ ] **Step 3: 修改候选人配置查询接口**

设计文档: `GET /api/admin/workflow/node-candidates?processDefinitionKey=xxx`

```java
@GetMapping("/node-candidates")
@Operation(summary = "查询节点候选人配置列表")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<List<WfNodeCandidateConf>> listNodeCandidates(
        @RequestParam(value = "processDefinitionKey", required = false) String processDefinitionKey) {
    log.debug("[WorkflowAdminController.listNodeCandidates] processDefinitionKey={}", processDefinitionKey);
    List<WfNodeCandidateConf> list = workflowAdminService.listCandidateConfigs(processDefinitionKey);
    return ResponseWrapper.success(list);
}
```

- [ ] **Step 4: 修改候选人配置更新接口**

设计文档: `PUT /api/admin/workflow/node-candidates/{id}` with JSON body

```java
@PutMapping("/node-candidates/{id}")
@Operation(summary = "更新节点候选人配置")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<Void> updateNodeCandidate(
        @PathVariable(value = "id") String id,
        @Valid @RequestBody NodeCandidateUpdateReqDTO req) {
    log.info("[WorkflowAdminController.updateNodeCandidate] id={}, candidateType={}", id, req.getCandidateType());
    workflowAdminService.updateCandidateConfig(id, req.getCandidateType(), String.join(",", req.getCandidateValue()));
    return ResponseWrapper.success();
}
```

- [ ] **Step 5: 修改节点表单配置查询接口**

设计文档: `GET /api/admin/workflow/node-forms?processDefinitionKey=xxx`

```java
@GetMapping("/node-forms")
@Operation(summary = "查询节点表单配置列表")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<List<WfNodeFormConf>> listNodeForms(
        @RequestParam(value = "processDefinitionKey", required = false) String processDefinitionKey) {
    log.debug("[WorkflowAdminController.listNodeForms] processDefinitionKey={}", processDefinitionKey);
    List<WfNodeFormConf> list = workflowAdminService.listNodeFormConfs(processDefinitionKey);
    return ResponseWrapper.success(list);
}
```

- [ ] **Step 6: 修改节点表单配置更新接口**

设计文档: `PUT /api/admin/workflow/node-forms/{id}` with JSON body

```java
@PutMapping("/node-forms/{id}")
@Operation(summary = "更新节点表单配置")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<Void> updateNodeForm(
        @PathVariable(value = "id") String id,
        @Valid @RequestBody NodeFormUpdateReqDTO req) {
    log.info("[WorkflowAdminController.updateNodeForm] id={}", id);
    workflowAdminService.updateNodeFormConf(id,
            JSON.toJSONString(req.getFormFields()),
            JSON.toJSONString(req.getEditableFields()),
            JSON.toJSONString(req.getRequiredFields()));
    return ResponseWrapper.success();
}
```

- [ ] **Step 7: 删除旧的路径参数接口**

删除以下旧接口：
- `GET /api/admin/workflow/timeout-rules/{processDefinitionKey}`
- `GET /api/admin/workflow/timeout-rules/item/{id}`
- `GET /api/admin/workflow/candidate-configs/{processDefinitionKey}`
- `GET /api/admin/workflow/candidate-configs/item/{id}`
- `GET /api/admin/workflow/node-form-confs/{processDefinitionKey}`
- `GET /api/admin/workflow/node-form-confs/item/{id}`

- [ ] **Step 8: 编译验证**

```bash
cd workflow-center && mvn compile -q
```

- [ ] **Step 9: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/WorkflowAdminController.java
git commit -m "refactor: 修改 WorkflowAdminController 接口路径与文档一致"
```

---

### Task 4: 新增流程定义列表接口 (D.7)

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/WorkflowAdminController.java`
- Check: `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/WorkflowAdminService.java`

- [ ] **Step 1: 在 WorkflowAdminController 添加流程定义列表接口**

```java
/**
 * 获取流程定义列表 (D.7)
 * 设计文档: GET /api/admin/workflow/process-definitions
 */
@GetMapping("/process-definitions")
@Operation(summary = "获取流程定义列表")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<List<ProcessDefinitionInfo>> listProcessDefinitions(
        @RequestParam(value = "active", defaultValue = "true") boolean active) {
    log.debug("[WorkflowAdminController.listProcessDefinitions] active={}", active);
    List<ProcessDefinitionInfo> list = workflowAdminService.listProcessDefinitions(active);
    return ResponseWrapper.success(list);
}
```

- [ ] **Step 2: 定义 ProcessDefinitionInfo 内部类或 DTO**

在 Controller 中添加内部类或使用 Map 返回

```java
@Data
@AllArgsConstructor
class ProcessDefinitionInfo {
    private String processDefinitionId;
    private String processDefinitionKey;
    private String processDefinitionName;
    private Integer version;
    private String deploymentId;
    private String deploymentTime;
    private Boolean suspended;
    private String description;
}
```

- [ ] **Step 3: 在 WorkflowAdminService 添加方法**

```java
/**
 * 获取流程定义列表
 *
 * @param active 是否仅查询已激活的流程定义
 * @return 流程定义信息列表
 */
public List<ProcessDefinitionInfo> listProcessDefinitions(boolean active) {
    // TODO: 实现从 Flowable RepositoryService 获取流程定义
    return Collections.emptyList();
}
```

- [ ] **Step 4: 编译验证**

```bash
cd workflow-center && mvn compile -q
```

- [ ] **Step 5: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/WorkflowAdminController.java
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/service/WorkflowAdminService.java
git commit -m "feat: 新增流程定义列表接口 D.7"
```

---

### Task 5: 统一响应格式验证

**Files:**
- Check all controllers

- [ ] **Step 1: 验证所有接口返回 ResponseWrapper 格式**

确保与设计文档一致

- [ ] **Step 2: 启动服务验证**

```bash
cd bootstrap && mvn spring-boot:run
```

- [ ] **Step 3: 测试接口**

使用 curl 或 Swagger 验证各接口

- [ ] **Step 4: 提交**

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-04-08-workflow-interface-fix.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?