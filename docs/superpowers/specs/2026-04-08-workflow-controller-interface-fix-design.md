# Workflow Center 接口一致性修复 - 设计文档

> **Date:** 2026-04-08
> **Status:** APPROVED

## 背景

核对 workflow-center 的接口设计文档 `03-接口设计与报文.md` 与实际代码，发现 **TaskController** 存在接口不一致问题：service 层已重构为 DTO 模式并从 `CurrentUserApi` 获取 empId，但 Controller 仍使用旧方法签名和硬编码占位符。

## 设计决策

**选择方案 A**：修改 TaskController 调用新 service 方法 + 注入 CurrentUserApi

**理由**：
1. 与当前 TaskOperationService 代码一致（service 已改为 DTO 模式，empId 从 CurrentUserApi 获取）
2. 符合设计文档"empId 从 session 隐式获取"的要求
3. Controller 保持简洁，职责清晰

## 修复范围

### 1. TaskController 修改

**文件：** `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java`

**修改点：**

| 行 | 原代码 | 改为 |
|----|--------|------|
| 40 | `private final TaskOperationService taskOperationService;` | 同上，追加注入 `private final CurrentUserApi currentUserApi;` |
| 62 | `String empId = "CURRENT_USER";` | `String empId = currentUserApi.getCurrentEmpId();` |
| 87 | `String empId = "CURRENT_USER";` | `String empId = currentUserApi.getCurrentEmpId();` |
| 104 | `String empId = "CURRENT_USER";` | `String empId = currentUserApi.getCurrentEmpId();` |
| 121-124 | `String empId = "CURRENT_USER"; ... taskOperationService.claimTask(taskId, empId);` | 删除 empId，改为 `taskOperationService.claimTask(taskId);` |
| 141-144 | `String empId = "CURRENT_USER"; ... taskOperationService.approveTask(taskId, empId, req.getFormData(), req.getOpinion());` | 删除 empId，改为 `taskOperationService.approveTask(taskId, req);` |
| 161-164 | `String empId = "CURRENT_USER"; ... taskOperationService.rejectTask(taskId, empId, req.getOpinion());` | 删除 empId，改为 `taskOperationService.rejectTask(taskId, req);` |
| 181-184 | `String empId = "CURRENT_USER"; ... taskOperationService.transferTask(taskId, empId, req.getTargetEmpId(), req.getReason());` | 删除 empId，改为 `taskOperationService.transferTask(taskId, req);` |

### 2. TaskControllerTest 修改

**文件：** `workflow-center/src/test/java/com/bank/branch/platform/workflow/controller/TaskControllerTest.java`

**修改点：**
- Mock `CurrentUserApi.getCurrentEmpId()` 返回 "E001"
- 验证 Controller 调用的是 DTO 模式 service 方法

### 3. TaskOperationServiceTest 验证

TaskOperationServiceTest 已完成修复（上一轮会话完成），所有 13 个测试通过，无需再改。

## 不在本次修复范围

以下问题本次不涉及，留待后续处理：
- TaskController 响应格式与设计文档的细粒度差异（TaskRespDTO 字段完整性）
- TaskDetailRespDTO 字段完整性
- ProcessController 响应格式差异
- 错误码 WF-40902/WF-40905 与设计文档的差异（WfErrorCode 枚举值）

## 架构原则

1. **empId 从 CurrentUserApi 隐式获取**：Controller 和 Service 都不直接接收 empId 参数
2. **DTO 传递**：所有写操作通过 DTO 传递业务数据
3. **向后兼容**：Service 方法签名已更新，测试需要同步适配

## 风险评估

- **风险低**：仅涉及 Controller 层调用方式和测试 mock 调整
- **影响范围**：TaskController 的 5 个写操作接口（claim/approve/reject/transfer）
- **回归测试**：TaskOperationServiceTest 全部通过，覆盖核心逻辑
