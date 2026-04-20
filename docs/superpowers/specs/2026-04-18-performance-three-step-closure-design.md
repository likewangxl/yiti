# performance-engine-center 三步闭环设计方案

| 元数据 | 内容 |
|---|---|
| 文档类型 | 增量设计规格（Brainstorming 输出） |
| 目标模块 | `performance-engine-center` |
| 编写日期 | 2026-04-18 |
| 适用范围 | 仅覆盖本次确认的 3 步：指标库 Controller 收口、模块级回归、KPI 子域完整闭环 |
| 后续衔接 | 本文确认后进入 `writing-plans` 生成实施计划，再由 `subagent-driven-development` 执行 |

---

## 1. 背景

当前 `performance-engine-center` 已完成：

- 模块骨架、配置、错误码、DTO 契约、SQL 脚本
- `sys_control` 子域全栈实现与测试
- 指标库子域的部分中间层能力：实体、Mapper、XML、核心 Service、部分 Facade 与测试

用户要求本轮严格只做以下 3 步，并且按顺序推进：

1. 补齐指标库 Controller 闭环
2. 运行 `performance-engine-center` 模块级回归
3. 完成 KPI 子域完整闭环

同时强制要求：

- 全程 TDD
- 使用 `subagent-driven-development`
- 每个实现子任务完成后，必须由单独 agent 执行两层 review：
  - 先规格符合性 review
  - 再代码质量 review

---

## 2. 本次范围

### 2.1 In Scope

### Step 1：指标库 Controller 完整闭环

在现有指标库 `Mapper / Service / Facade` 基础上，补齐 HTTP 入口层，形成完整垂直切片，包括：

- 请求 DTO
- `MetricDefController`
- 指标库 10 个端点
- `@BizAuth`
- `@AuditLog`
- Controller IT
- 必要的 Facade/Assembler/命令对象补充

### Step 2：模块级回归

执行 `performance-engine-center` 模块测试回归，验证 Step 1 未破坏：

- 已有 `sys_control`
- 已完成的指标库中间层
- Spring 装配、MyBatis XML、Controller 装配

### Step 3：KPI 子域完整闭环

直接完成 KPI 子域完整垂直切片，包括：

- Entity
- Mapper + XML
- Service
- Facade
- Controller
- 请求 DTO
- Mapper IT / Service UT / Facade UT / Controller IT
- KPI 子域阶段回归

本步骤的“完整闭环”仅指 **KPI 方案配置闭环**，即围绕：

- `perf_kpi_scheme`
- `perf_kpi_item`
- `KpiApi` 中 V1.0 应实现的方案定义查询能力

本步骤**不包含**：

- `kpi_result` 结果表读写
- KPI 总分计算
- KPI 历史结果查询真实实现
- 当前 `KpiApi` 中仍标注为 V1.1/UOE 的结果类能力

### 2.2 Out of Scope

本次明确不做：

- `Target` 子域
- `RunTask` 子域
- `Alloc` 子域
- 跨模块 bootstrap 联动增强
- 无关模块重构
- performance 全模块大范围重构
- V1.1 执行型指标接口真实实现（指标值计算、批量快照查询等）

---

## 3. 关键约束

### 3.1 顺序约束

必须严格按照 `1 -> 2 -> 3` 执行，不允许跳阶段：

- Step 1 未闭环，不进入 Step 2
- Step 2 未通过，不进入 Step 3

### 3.2 TDD 约束

每个实现任务必须遵守：

1. 先写 failing test
2. 运行确认失败
3. 写最小实现
4. 运行确认通过
5. 自检
6. 进入 review

禁止：

- 先写实现再补测试
- 跳过红测试验证
- 将“理论上会失败”当作失败证据

### 3.3 Review 约束

每个实现子任务完成后，必须按以下顺序进入双 review：

1. 规格符合性 review
2. 代码质量 review

若任一 review 未通过，回到原实现子 agent 修复后重审。

### 3.4 边界约束

- 仅修改与本次 3 步直接相关的文件
- 不把后续子域内容提前混入
- 不因为做 Controller/KPI 顺手扩大到其它 performance 子域

---

## 4. 当前实现现状判断

基于当前工作树与已有实现，现状可概括为：

### 4.1 指标库

已经具备较强基础：

- `PerfMetricDef` / `PerfMetricRef` 实体
- `PerfMetricDefMapper` / `PerfMetricRefMapper`
- 对应 XML
- Mapper IT
- `MetricCycleDetectService`
- `MetricSlotService`
- `MetricRefService`
- `MetricDefService`
- `MetricApiImpl`
- `MetricQueryApiImpl`
- `MetricLifecycleFacade`
- 对应单元测试/集成测试

缺口主要集中在：

- 请求 DTO
- `MetricDefController`
- Controller IT
- 最终模块回归闭环

### 4.2 KPI 子域

当前可视为未开始完整闭环，需要从底层到入口层按 TDD 完整推进。

---

## 5. 执行架构

本次采用“阶段闭环推进”，而不是“跨阶段按技术层横切推进”。

### 5.1 推荐执行方案

采用方案 A：阶段闭环推进。

#### 原因

- 最符合用户明确的 `1 -> 2 -> 3` 顺序
- 最适合强制 TDD
- 最适合 `subagent-driven-development`
- 每个阶段都能形成可验证闸门

### 5.2 协作角色

#### 主控代理

负责：

- 读取 spec / plan
- 按阶段拆分任务
- 派发实现 subagent
- 派发两层 reviewer
- 判断阶段是否可以切换
- 汇总验证证据

#### 实现 subagent

负责：

- 只完成当前任务
- 严格按 TDD 执行
- 完成后报告状态、测试结果、自检结论

#### Reviewer A：规格符合性 reviewer

负责判断：

- 是否满足当前 spec / plan
- 是否少做
- 是否多做
- 是否越出阶段边界

#### Reviewer B：代码质量 reviewer

负责判断：

- 是否符合当前代码风格与模块模式
- 是否存在坏味道
- 测试是否合理
- 是否有不必要复杂度

---

## 6. 任务切分策略

### 6.1 Step 1：指标库 Controller 闭环

建议拆成 4 个实现任务：

#### Task 1.1 请求 DTO 与入参校验

目标：

- 补齐 `CreateMetricReqDTO`
- 补齐 `UpdateMetricReqDTO`
- 补齐 `ChangeStatusReqDTO`
- 补齐 `ReleaseSlotReqDTO`

验证重点：

- `@NotBlank`
- `@NotNull`
- reason 必填
- 非法 JSON / 非法请求体

#### Task 1.2 指标库读接口收口

目标：

- `GET /api/perf/metrics`
- `GET /api/perf/metrics/{metricCode}`
- `GET /api/perf/metrics/{metricCode}/refs`
- `GET /api/perf/metrics/{metricCode}/ref-by`
- `GET /api/perf/metrics/val-slots`

#### Task 1.3 指标库写接口收口

目标：

- `POST /api/perf/metrics`
- `PUT /api/perf/metrics/{metricCode}`
- `DELETE /api/perf/metrics/{metricCode}`
- `PUT /api/perf/metrics/{metricCode}/status`
- `POST /api/perf/metrics/{metricCode}/slot/release`

#### Task 1.4 Controller IT 收口

目标：

- 补齐 Controller 层集成测试
- 验证成功路径、参数校验、业务异常映射
- 验证锁入口链路的最小可行并发证明

### 6.2 Step 2：模块级回归

建议拆成 2 个实现任务：

#### Task 2.1 模块回归执行

目标：

- 执行 `performance-engine-center` 模块级测试
- 采集失败清单

#### Task 2.2 模块回归修复

目标：

- 修复 Step 1 引入的 regression
- 重新跑绿

### 6.3 Step 3：KPI 子域完整闭环

建议拆成 5 个实现任务：

#### Task 3.1 KPI 底层数据闭环

- Entity
- Mapper
- XML
- Mapper IT

#### Task 3.2 KPI Service 闭环

- Service
- 命令对象
- Service UT

#### Task 3.3 KPI Facade 闭环

- 对外 API 实现
- Assembler
- Facade UT

#### Task 3.4 KPI Controller 闭环

- Controller
- 请求 DTO
- Controller IT

#### Task 3.5 KPI 阶段回归

- 子域回归
- 修复剩余问题
- 再次执行 `performance-engine-center` 模块级回归，证明 Step 3 未破坏已有能力

---

## 7. 测试策略

### 7.1 测试层次

本轮采用四层测试：

- Mapper IT
- Service UT
- Facade UT
- Controller IT

### 7.2 Step 1 的测试重点

- DTO 校验失败
- Controller 成功路径
- 业务异常码映射
- 读写端点返回结构
- 必要的锁入口验证

### 7.3 Step 2 的测试重点

- 模块级整体测试通过
- 失败归因清晰

### 7.4 Step 3 的测试重点

- KPI Mapper IT
- KPI Service UT
- KPI Facade UT
- KPI Controller IT
- KPI 子域阶段回归

### 7.5 测试证据要求

review 时必须附带：

- 运行命令
- 红测试证据
- 绿测试证据
- 本任务修改范围说明

---

## 8. 权限与审计策略

### 8.1 指标库 Controller 权限

必须使用现有枚举，不发明新 `BizAction`：

- 读接口使用 `BizAction.READ` 或 `BizAction.LIST`
- 写接口使用 `BizAction.WRITE`
- 删除接口使用 `BizAction.DELETE`
- 配置/状态接口按现有能力选择 `BizAction.CONFIG` 或 `BizAction.WRITE`

统一使用：

- `@BizAuth(bizType = BizType.PERF_CONFIG, action = ...)`

### 8.2 审计策略

高危写操作必须使用：

- `@AuditLog(action = ..., resourceType = ..., reasonRequired = true)`

读接口不打审计。

### 8.3 KPI Controller 权限与审计策略

KPI 子域按“方案配置闭环”处理，Controller 权限/审计最小规则如下：

| 类型 | 端点类别 | 建议 `BizAction` | 审计要求 |
|---|---|---|---|
| 读 | 方案查询、按 ID 查询、方案项查询 | `READ` / `LIST` | 不打审计 |
| 写 | 新建/编辑方案、新增/编辑方案项 | `WRITE` | `@AuditLog`，通常不强制 reason |
| 删除 | 删除方案、删除方案项 | `DELETE` | `@AuditLog(reasonRequired = true)` |
| 发布/停用 | 发布方案、状态变更 | `CONFIG` | `@AuditLog(reasonRequired = true)` |

统一使用：

- `@BizAuth(bizType = BizType.PERF_CONFIG, action = ...)`

实现计划阶段必须把 KPI Controller 各端点与上表映射明确化，不允许自行扩展不存在的 `BizAction`。

---

## 9. 完成定义

### 9.1 Step 1 完成标准

同时满足：

- 指标库 10 个端点闭环
- 请求 DTO 就位
- `MetricDefController` 就位
- 对应 Controller IT 通过
- 每个子任务都通过双 review

### 9.2 Step 2 完成标准

同时满足：

- `performance-engine-center` 模块级回归通过
- 所有回归失败已修复
- 未遗留 blocker

### 9.3 Step 3 完成标准

同时满足：

- KPI 子域 Entity/Mapper/XML/Service/Facade/Controller 全闭环
- KPI 各层测试通过
- KPI 阶段回归通过
- Step 3 完成后再次执行 `performance-engine-center` 模块级回归并通过
- 每个子任务都通过双 review

### 9.4 本轮任务整体完成标准

同时满足：

- Step 1 完成
- Step 2 完成
- Step 3 完成
- 最终模块级回归通过
- 无越界实现
- 验证证据完整

---

## 10. 风险与控制

### 风险 1：Controller 层测试环境与真实鉴权链不完全一致

控制方式：

- 以当前测试装配可验证的部分为主
- 不伪造错误结论
- 对无法在该层真实覆盖的认证行为，在计划中注明验证边界

### 风险 2：指标库已有部分实现，执行时容易重复造轮子

控制方式：

- 先盘点现有文件
- Controller 层只补入口与必要粘合
- 不回头推翻已验证通过的中间层

### 风险 3：KPI 子域完整闭环范围较大

控制方式：

- 按垂直切片拆任务
- 每任务双 review
- 阶段内及时回归

### 风险 4：subagent 可能越界开发

控制方式：

- 每次只发单任务
- reviewer A 专门拦截越界
- 未经通过不得进入下一任务

---

## 11. 后续流程

本文确认后，下一步流程固定为：

1. 进入 `writing-plans`
2. 产出 `docs/superpowers/plans/2026-04-18-performance-three-step-closure-impl.md`
3. 进入 `subagent-driven-development`
4. 按“实现 -> 规格 review -> 代码质量 review”顺序逐任务执行

---

## 12. 结论

本次不做 performance 全域推进，而是聚焦于：

- 指标库入口闭环
- 模块级稳定性证明
- KPI 子域完整闭环

该范围足够清晰，适合产出一份单独实施计划，并在当前会话中使用 `subagent-driven-development` 有序执行。
