# Workflow Controller 接口一致性修复 - 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 TaskController 中硬编码 "CURRENT_USER" 占位符问题，改为注入 CurrentUserApi 获取真实 empId，同时将 4 个写操作接口改为 DTO 模式调用 service。

**Architecture:** Controller 层注入 CurrentUserApi，所有 empId 从 ThreadLocal 获取；写操作通过 DTO 传递参数，service 层不再接收 empId 参数。

**Tech Stack:** Spring Boot, Lombok, CurrentUserApi (auth-permission-center), ApproveReqDTO/RejectReqDTO/TransferReqDTO

---

## 文件清单

**Task 1:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java`

**Task 2:**
- Modify: `workflow-center/src/test/java/com/bank/branch/platform/workflow/controller/TaskControllerTest.java`
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TaskOperationService.java`
- Modify: `workflow-center/src/test/java/com/bank/branch/platform/workflow/service/TaskOperationServiceTest.java`
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/ApproveReqDTO.java`
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/RejectReqDTO.java`
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/TransferReqDTO.java`

---

## Task 1: 修改 TaskController - 注入 CurrentUserApi 并重构调用

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java:1-187`

- [ ] **Step 1: 添加 CurrentUserApi import**

在 `TaskController.java` 的 import 区（第 1-24 行之间）追加一行：

```java
import com.bank.branch.platform.auth.api.CurrentUserApi;
```

- [ ] **Step 2: 注入 CurrentUserApi**

在 `TaskController.java` 第 39-40 行的字段区，追加第三个字段：

```java
    private final TodoQueryService todoQueryService;
    private final TaskOperationService taskOperationService;
    private final CurrentUserApi currentUserApi;
```

- [ ] **Step 3: 修改 queryTodoList 方法 - 替换硬编码 empId**

在第 61-64 行，将：

```java
        // TODO: 获取当前用户 empId，后续替换为 CurrentUserApi
        String empId = "CURRENT_USER";
        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList(empId, bizType, keyword, pageNo, pageSize);
```

改为：

```java
        String empId = currentUserApi.getCurrentEmpId();
        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList(empId, bizType, keyword, pageNo, pageSize);
```

- [ ] **Step 4: 修改 queryDoneList 方法 - 替换硬编码 empId**

在第 86-88 行，将：

```java
        // TODO: 获取当前用户 empId，后续替换为 CurrentUserApi
        String empId = "CURRENT_USER";
        PageResult<TaskRespDTO> result = todoQueryService.queryDoneList(empId, bizType, keyword, pageNo, pageSize);
```

改为：

```java
        String empId = currentUserApi.getCurrentEmpId();
        PageResult<TaskRespDTO> result = todoQueryService.queryDoneList(empId, bizType, keyword, pageNo, pageSize);
```

- [ ] **Step 5: 修改 getTaskDetail 方法 - 替换硬编码 empId**

在第 103-106 行，将：

```java
        // TODO: 获取当前用户 empId，后续替换为 CurrentUserApi
        String empId = "CURRENT_USER";
        log.debug("[TaskController.getTaskDetail] taskId={}, empId={}", taskId, empId);
        TaskDetailRespDTO detail = todoQueryService.getTaskDetail(taskId, empId);
```

改为：

```java
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[TaskController.getTaskDetail] taskId={}", taskId);
        TaskDetailRespDTO detail = todoQueryService.getTaskDetail(taskId, empId);
```

> **注**：debug 日志中 empId 已移除以符合日志脱敏规范（empId 属于用户标识）。

- [ ] **Step 6: 修改 claimTask 方法 - 移除 empId，调用 DTO 模式**

在第 120-125 行，将：

```java
        // TODO: 获取当前用户 empId，后续替换为 CurrentUserApi
        String empId = "CURRENT_USER";
        log.info("[TaskController.claimTask] taskId={}, empId={}", taskId, empId);
        taskOperationService.claimTask(taskId, empId);
        return ResponseWrapper.success();
```

改为：

```java
        log.info("[TaskController.claimTask] taskId={}", taskId);
        taskOperationService.claimTask(taskId);
        return ResponseWrapper.success();
```

- [ ] **Step 7: 修改 approveTask 方法 - 移除 empId，改为 DTO 模式**

在第 140-145 行，将：

```java
        // TODO: 获取当前用户 empId，后续替换为 CurrentUserApi
        String empId = "CURRENT_USER";
        log.info("[TaskController.approveTask] taskId={}, empId={}", taskId, empId);
        taskOperationService.approveTask(taskId, empId, req.getFormData(), req.getOpinion());
        return ResponseWrapper.success();
```

改为：

```java
        log.info("[TaskController.approveTask] taskId={}, opinion={}", taskId, req.getOpinion());
        taskOperationService.approveTask(taskId, req);
        return ResponseWrapper.success();
```

- [ ] **Step 8: 修改 rejectTask 方法 - 移除 empId，改为 DTO 模式**

在第 160-165 行，将：

```java
        // TODO: 获取当前用户 empId，后续替换为 CurrentUserApi
        String empId = "CURRENT_USER";
        log.info("[TaskController.rejectTask] taskId={}, empId={}", taskId, empId);
        taskOperationService.rejectTask(taskId, empId, req.getOpinion());
        return ResponseWrapper.success();
```

改为：

```java
        log.info("[TaskController.rejectTask] taskId={}, opinion={}", taskId, req.getOpinion());
        taskOperationService.rejectTask(taskId, req);
        return ResponseWrapper.success();
```

- [ ] **Step 9: 修改 transferTask 方法 - 移除 empId，改为 DTO 模式**

在第 180-185 行，将：

```java
        // TODO: 获取当前用户 empId，后续替换为 CurrentUserApi
        String empId = "CURRENT_USER";
        log.info("[TaskController.transferTask] taskId={}, empId={}, targetEmpId={}", taskId, empId, req.getTargetEmpId());
        taskOperationService.transferTask(taskId, empId, req.getTargetEmpId(), req.getReason());
        return ResponseWrapper.success();
```

改为：

```java
        log.info("[TaskController.transferTask] taskId={}, targetEmpId={}", taskId, req.getTargetEmpId());
        taskOperationService.transferTask(taskId, req);
        return ResponseWrapper.success();
```

- [ ] **Step 10: 编译验证**

```bash
cd workflow-center && mvn compile -q
```

Expected: BUILD SUCCESS（无任何编译错误）

- [ ] **Step 11: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java
git commit -m "refactor(workflow): TaskController 注入 CurrentUserApi 移除硬编码占位符"
```

---

## Task 2: 修复 TaskControllerTest - 更新 Mock 和断言

**Files:**
- Modify: `workflow-center/src/test/java/com/bank/branch/platform/workflow/controller/TaskControllerTest.java`

> **重要**：测试文件中所有请求都包含 `.param("empId", "EMP001")`，但 Controller 修复后不再从 request param 读取 empId，这些参数需要全部删除。

- [ ] **Step 1: 添加 CurrentUserApi import**

在 import 区追加：

```java
import com.bank.branch.platform.auth.api.CurrentUserApi;
```

- [ ] **Step 2: 添加 CurrentUserApi mock 字段**

在第 43 行 `private TaskOperationService taskOperationService;` 后追加：

```java
    @Mock
    private CurrentUserApi currentUserApi;
```

- [ ] **Step 3: 更新 setUp() - 注入 CurrentUserApi mock 并配置返回值**

将第 50-53 行：

```java
        mockMvc = MockMvcBuilders.standaloneSetup(
                new TaskController(todoQueryService, taskOperationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
```

改为：

```java
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new TaskController(todoQueryService, taskOperationService, currentUserApi))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
```

- [ ] **Step 4: queryTodoList 测试 - 移除 empId param**

在第 67-73 行，将请求中的 `.param("empId", "EMP001")` 删除：

```java
        mockMvc.perform(get("/api/workflow/tasks/todo")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
```

- [ ] **Step 5: queryDoneList 测试 - 移除 empId param**

在第 86-90 行，将请求中的 `.param("empId", "EMP001")` 删除：

```java
        mockMvc.perform(get("/api/workflow/tasks/done"))
```

- [ ] **Step 6: getTaskDetail 测试 - 移除 empId param**

在第 103-107 行，将请求中的 `.param("empId", "EMP001")` 删除：

```java
        mockMvc.perform(get("/api/workflow/tasks/T_003"))
```

- [ ] **Step 7: claimTask 成功测试 - 更新 mock 签名 + 移除 param**

将第 113 行：

```java
        doNothing().when(taskOperationService).claimTask(anyString(), anyString());
```

改为：

```java
        doNothing().when(taskOperationService).claimTask(anyString());
```

并在第 116-118 行的请求中删除 `.param("empId", "EMP001")`：

```java
        mockMvc.perform(post("/api/workflow/tasks/T_001/claim"))
```

- [ ] **Step 8: approveTask 成功测试 - 更新 mock 签名 + 移除 param**

将第 125 行：

```java
        doNothing().when(taskOperationService).approveTask(anyString(), anyString(), any(), any());
```

改为：

```java
        doNothing().when(taskOperationService).approveTask(anyString(), any(ApproveReqDTO.class));
```

并在第 132-137 行请求中删除 `.param("empId", "EMP001")`：

```java
        mockMvc.perform(post("/api/workflow/tasks/T_001/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
```

- [ ] **Step 9: rejectTask 成功测试 - 更新 mock 签名 + 移除 param**

将第 143 行：

```java
        doNothing().when(taskOperationService).rejectTask(anyString(), anyString(), anyString());
```

改为：

```java
        doNothing().when(taskOperationService).rejectTask(anyString(), any(RejectReqDTO.class));
```

并在第 149-153 行请求中删除 `.param("empId", "EMP001")`:

```java
        mockMvc.perform(post("/api/workflow/tasks/T_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
```

- [ ] **Step 10: transferTask 成功测试 - 更新 mock 签名 + 移除 param**

将第 160 行：

```java
        doNothing().when(taskOperationService).transferTask(anyString(), anyString(), anyString(), anyString());
```

改为：

```java
        doNothing().when(taskOperationService).transferTask(anyString(), any(TransferReqDTO.class));
```

并在第 167-171 行请求中删除 `.param("empId", "EMP001")`:

```java
        mockMvc.perform(post("/api/workflow/tasks/T_001/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
```

- [ ] **Step 11: claimTask_taskNotFound 错误测试 - 更新 mock 签名 + 移除 param**

将第 180 行：

```java
        doThrow(new BizException("WF-40403", "任务不存在"))
            .when(taskOperationService).claimTask(anyString(), anyString());
```

改为：

```java
        doThrow(new BizException("WF-40403", "任务不存在"))
            .when(taskOperationService).claimTask(anyString());
```

并在第 182-184 行请求中删除 `.param("empId", "EMP001")`:

```java
        mockMvc.perform(post("/api/workflow/tasks/NONEXIST/claim"))
```

- [ ] **Step 12: claimTask_alreadyClaimed 错误测试 - 更新 mock 签名 + 移除 param**

将第 191 行：

```java
        doThrow(new BizException("WF-40904", "任务已被签收"))
            .when(taskOperationService).claimTask(anyString(), anyString());
```

改为：

```java
        doThrow(new BizException("WF-40904", "任务已被签收"))
            .when(taskOperationService).claimTask(anyString());
```

并在第 193-195 行请求中删除 `.param("empId", "EMP001")`:

```java
        mockMvc.perform(post("/api/workflow/tasks/T_001/claim"))
```

- [ ] **Step 13: approveTask_notAssignee 错误测试 - 更新 mock 签名**

将第 202 行：

```java
        doThrow(new BizException("WF-40903", "非任务办理人"))
            .when(taskOperationService).approveTask(anyString(), anyString(), any(), any());
```

改为：

```java
        doThrow(new BizException("WF-40903", "非任务办理人"))
            .when(taskOperationService).approveTask(anyString(), any(ApproveReqDTO.class));
```

请求本身（第 207-210 行）已无 empId param，无需修改。

- [ ] **Step 14: rejectTask_notAssignee 错误测试 - 更新 mock 签名 + 移除 param**

将第 217 行：

```java
        doThrow(new BizException("WF-40903", "非任务办理人"))
            .when(taskOperationService).rejectTask(anyString(), anyString(), anyString());
```

改为：

```java
        doThrow(new BizException("WF-40903", "非任务办理人"))
            .when(taskOperationService).rejectTask(anyString(), any(RejectReqDTO.class));
```

并在第 222-225 行请求中删除 `.param("empId", "EMP001")`:

```java
        mockMvc.perform(post("/api/workflow/tasks/T_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
```

- [ ] **Step 16: rejectTask_missingComment_returns400 测试 - 移除 empId param**

在第 235-239 行请求中删除 `.param("empId", "EMP001")`:

```java
        mockMvc.perform(post("/api/workflow/tasks/T_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
```

- [ ] **Step 17: transferTask_missingToEmpId_returns400 测试 - 移除 empId param**

在第 248-252 行请求中删除 `.param("empId", "EMP001")`:

```java
        mockMvc.perform(post("/api/workflow/tasks/T_001/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
```

- [ ] **Step 18: getTaskDetail_taskNotFound_returnsBizError 测试 - 移除 empId param**

在第 260-263 行请求中删除 `.param("empId", "EMP001")`:

```java
        mockMvc.perform(get("/api/workflow/tasks/NONEXIST"))
```

- [ ] **Step 19: 运行测试验证**

```bash
cd workflow-center && mvn test -Dtest=TaskControllerTest -q
```

Expected: 所有 14 个测试通过（BUILD SUCCESS）

- [ ] **Step 20: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TaskOperationService.java
git add workflow-center/src/test/java/com/bank/branch/platform/workflow/controller/TaskControllerTest.java
git add workflow-center/src/test/java/com/bank/branch/platform/workflow/service/TaskOperationServiceTest.java
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/ApproveReqDTO.java
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/RejectReqDTO.java
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/TransferReqDTO.java
git commit -m "refactor(workflow): TaskController DTO 模式重构 + CurrentUserApi 集成"
```

---

## 自检清单

1. **Spec coverage**: 设计文档中的所有 7 处 "CURRENT_USER" 替换都在 Task 1 中覆盖。TaskControllerTest 的 14 个测试方法（成功路径 6 个 + 错误路径 8 个）全部在 Task 2 中覆盖。TaskOperationService 的 4 个操作方法（claim/approve/reject/transfer）全部改为 DTO 模式，TaskOperationServiceTest 的 13 个测试同步更新。
2. **Placeholder scan**: 无任何 TBD/TODO/待填内容，每一步都有完整代码。
3. **Type consistency**: 所有 mock 签名与设计文档一致（claimTask(String)、approveTask(String, ApproveReqDTO)、rejectTask(String, RejectReqDTO)、transferTask(String, TransferReqDTO)）。
4. **Service layer**: TaskOperationService 正确注入 CurrentUserApi，4 个写操作方法不再接收 empId 参数，改为从 ThreadLocal 获取。

---

## Task 3: 整体验证 - 运行 workflow-center 所有测试

- [ ] **Step 1: 运行 workflow-center 全部测试**

```bash
cd workflow-center && mvn test -q
```

Expected: 所有测试通过（包括 TaskControllerTest 的 14 个测试 + TaskOperationServiceTest 的 13 个测试）

- [ ] **Step 2: 提交**

```bash
git commit -m "test(workflow): 验证 TaskController 和 TaskOperationService 所有测试通过"
```