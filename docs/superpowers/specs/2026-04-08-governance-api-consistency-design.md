# Governance 模块接口一致性修复设计方案

> 日期：2026-04-08
> 状态：已批准

## 背景

根据 `docs/modules/system-governance-center/03-接口设计与报文.md` 文档对照实际代码，发现部分接口存在请求方式不一致的问题：接口文档定义应使用 `@RequestBody` 接收 JSON 请求体，但部分接口实际使用 `@RequestParam` 接收查询参数。

## 修改范围

### A. 字典管理（3 个接口）

| # | 接口 | 当前 | 目标 | 涉及 DTO |
|---|------|------|------|----------|
| A.3 | POST `/api/admin/sys/dicts` | @RequestParam | @RequestBody + DataScopeContext | DictCreateReqDTO |
| A.4 | PUT `/api/admin/sys/dicts/{id}` | @RequestParam | @RequestBody + DataScopeContext | DictUpdateReqDTO |
| A.6 | PUT `/api/admin/sys/dicts/{id}/status` | @RequestParam | @RequestBody + DataScopeContext | DictStatusReqDTO |

### B. 工作日历（2 个接口）

| # | 接口 | 当前 | 目标 | 涉及 DTO |
|---|------|------|------|----------|
| B.3 | POST `/api/admin/sys/calendar/init` | @RequestParam | @RequestBody + DataScopeContext | CalendarInitReqDTO |
| B.4 | POST `/api/admin/sys/calendar/import` | @RequestParam (multipart) | @RequestBody | MultipartFile + CalendarImportReqDTO |

### H. SQL 探查（1 个接口）

| # | 接口 | 当前 | 目标 | 涉及 DTO |
|---|------|------|------|----------|
| H.1 | POST `/api/admin/sql-probe/execute` | @RequestParam | @RequestBody + DataScopeContext | SqlProbeReqDTO → SqlProbeRespDTO |

> H.1 已在会话中初步实现。

## 修改方案

### 1. Controller 层统一改造

- 所有涉及 POST/PUT 的写接口，改为 `@Valid @RequestBody` 接收 DTO
- 操作工号 `operatorEmpId` 从 `DataScopeContext.current().getEmpId()` 自动获取，不再通过请求参数传入
- 涉及 Service 调用时，由 Controller 从 DataScopeContext 获取后传入

### 2. Service 层

- 方法签名保持不变
- operatorEmpId 由调用方（Controller）从 DataScopeContext 获取后传入

### 3. DTO 层

- 保持现有字段结构，**不增加** operatorEmpId 字段
- B.4 批量导入接口保留 MultipartFile 文件上传方式

### 4. API 路径

- 保持当前实现（如 `/api/admin/sql-probe`），暂不强制改为 `/api/admin/sys/sql-probe`

## 不修改范围

- E.通知消息：已是 @RequestBody 或无需修改
- F.系统配置：已是 @RequestBody
- G.文件管理：已是 @RequestBody
- C.任务调度：现有实现符合文档
- D.审计日志：现有实现符合文档

## 实施顺序

1. A.字典管理（A.3 → A.4 → A.6）
2. B.工作日历（B.3 → B.4）
3. H.SQL探查（H.1 收尾验证）

## 验收标准

- 所有改造接口通过 Knife4j UI 可正常调用
- 单元测试覆盖改造后的 Controller 和 Service 方法
- 与文档定义的请求/响应格式完全一致
