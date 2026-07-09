# 指标任务执行监控页设计方案（首期：指标重算）

- 日期：2026-07-09
- 模块：performance-engine-center（后端）+ xanzc_frontend（前端）
- 主题：一个监控页，展示各类任务的执行进度与错误原因；首期适用范围＝**指标重算**
- 相关代码：`entity/PerfRunTask.java`、`controller/PerfRunTaskController.java`、`service/PerfRunTaskService.java`、`mapper/PerfRunTaskMapper`、`api/dto/PerfRunTaskDTO.java`、`service/MetricCalcService.java`、`service/HistoryRecalcService.java`、`docs/schema/ddl-performance.sql`

## 1. 背景与目标

运维/管理员需要一个页面查看"指标重算"任务的执行情况：任务类型、子名称、执行状态、开始/结束时间、发起人，以及失败时的错误原因。

**示例行**：`指标重算 | M_0046 零售一般性存款余额-员工 | 完成 | 2026-07-09 15:00:00 | 2026-07-09 15:00:05 | 12094108（雷栋）`

现有 `PERF_RUN_TASK` 表 + 只读端点 `GET /api/perf/run-tasks` 已承载任务执行日志，需求列几乎全部有对应字段（含 `error_msg`）。本方案复用它，补齐三处：数据增强、来源标记、前端页面。

## 2. 关键决策（已与业务确认）

| # | 决策 | 取值 |
|---|---|---|
| A | 展示粒度 | **每指标一行**（子任务 `task_type=METRIC_RUN`），贴合示例 |
| B | 任务范围 | 加 `trigger_type` 列，页面**只展示重算触发**（`RECALC`） |
| C | 进度/刷新 | **状态列 + 手动刷新**（不做自动轮询/WebSocket） |
| D | 可见范围 | **运维/管理员看全部**（ALL 数据范围） |
| a | 端点 | **复用** `GET /api/perf/run-tasks` 扩展，不新建专用端点 |
| b | 错误详情 | **抽屉**（drawer）展示 `error_msg` 全文 |
| c | 日期过滤 | **数据日期范围**（from/to） |

## 3. 需求列 → 字段映射

| 需求列 | 字段 | 处理 |
|---|---|---|
| 任务类型（指标重算） | `task_type` + `trigger_type` | 前端字典：`trigger_type=RECALC`→"指标重算" |
| 子名称（M_0046 零售一般性存款余额-员工） | `task_key`(=metric_code) + 增强 `taskKeyName` | 后端 JOIN/批量查 `PERF_METRIC_DEF.metric_name` |
| 执行状态（完成） | `status` | 前端字典翻译（见 §7） |
| 开始时间 | `start_time` | 原样 |
| 结束时间 | `end_time` | 原样 |
| 发起人（12094108 雷栋） | `started_by` + 增强 `startedByName` | 后端批量解析工号→姓名 |
| 错误原因 | `error_msg`（longtext） | 抽屉展示全文 |

## 4. 数据模型改造

### 4.1 DDL（1 条 ALTER，直接执行，遵守"废弃 Flyway"红线）
```sql
ALTER TABLE `PERF_RUN_TASK`
  ADD COLUMN `trigger_type` varchar(20) DEFAULT NULL COMMENT '触发来源：RECALC/SCHEDULED/MANUAL' AFTER `task_type`,
  ADD KEY `idx_type_trigger` (`task_type`, `trigger_type`);
```
- 幂等：脚本用 `INFORMATION_SCHEMA` 预检列/索引是否存在再加（与项目既有对齐脚本风格一致）。
- 脚本落 `docs/superpowers/sql/2026-07-09-perf-run-task-add-trigger-type.sql`；同步更新 `docs/schema/ddl-performance.sql` 基线。
- 历史行 `trigger_type=NULL`：页面按 `RECALC` 过滤时不会命中历史 METRIC_RUN，符合"只看重算"预期。

### 4.2 实体
`PerfRunTask` 新增 `private String triggerType;`（对应 `trigger_type`）。

### 4.3 落库（关键：现在没落）
- `MetricCalcService`：`insertPendingTask` 现签名不含 triggerType；改为把已有的 `triggerType` 入参透传进来并 `task.setTriggerType(triggerType)`。`calcMetricWithStats` 调 `insertPendingTask(taskId, metricCode, dataDate, version, triggerType)`。
- `HistoryRecalcService`：
  - 调 `MetricCalcService.calcMetric(...)` 时**显式传 `triggerType="RECALC"`**（确认现调用点是否已传；未传则补）。
  - 父任务 `insertParentTask` 里 `parent.setTriggerType("RECALC")`。
- 其他触发方（定时 `MetricExecuteQuartzJob` / 手动执行）：本期可不强制回填（默认 `SCHEDULED`/`MANUAL` 由各自入口透传；**非本页范围**，不阻塞）。

## 5. 后端查询 + 数据增强

### 5.1 过滤参数（扩展现有端点）
`GET /api/perf/run-tasks` 新增可选参数：
- `triggerType`（如 `RECALC`）
- `taskKey`（指标编码模糊/精确，用于"指标搜索"；`PerfRunTaskService.pageDto` 已有 taskKey 形参，Controller 未透出，本期透出）
- `startedBy`（发起人工号过滤）
- `dataDateFrom` / `dataDateTo`（数据日期范围；保留原 `dataDate` 单值兼容）

Mapper `selectByCondition`/`countByCondition` 对应加动态条件：`trigger_type = #{triggerType}`、`task_key LIKE`、`started_by = #{startedBy}`、`data_date BETWEEN #{from} AND #{to}`（均为 `<if>` 可空分支）。

### 5.2 DTO 增强
`PerfRunTaskDTO` 新增：
- `private String taskKeyName;`（子名称中文，来源 `PERF_METRIC_DEF.metric_name`；非指标类任务为 null）
- `private String startedByName;`（发起人姓名）

`PerfRunTaskService.pageDto`：拿到本页 `List<PerfRunTask>` 后：
1. 收集 `task_key`（仅 `task_type=METRIC_RUN`）批量查 `PerfMetricDefMapper.selectByMetricCodes`（或复用现有批量查）→ metricCode→metricName map；
2. 收集 `started_by` 批量解析姓名（走 `AddressBookApi`；**注意** memory 记录的坑：工号存 `PT_USER.USERNAME`，非 USER_ID；用 `filterExistingUsernames`/通讯录员工表口径，避免 N+1）；
3. 拼进 DTO。查不到时 name 字段留 null（前端回退显示编码/工号）。

### 5.3 页面调用
监控页固定带 `taskType=METRIC_RUN & triggerType=RECALC`，其余过滤透传。

## 6. 权限与数据范围
- 复用资源 `P_PERF_RT_LIST`（现有 `GET /api/perf/run-tasks` 的 `@BizAuth(bizType=PERF_CONFIG, action=LIST)`）。
- 运维/管理员角色绑定该资源且数据范围为 **ALL** → 端点现有数据范围机制天然返回全部；普通用户即便进页也只见自己发起的（现状行为，安全兜底）。
- 前端菜单项挂到绩效/运维分组，按资源码控制可见。

## 7. 前端页面

### 7.1 文件
新建 `xanzc_frontend/src/views/perf/TaskMonitor.vue`，风格仿 `system/Jobs.vue`。API 走 `src/api/perf.js` 新增 `listRunTasks(params)`（若无）。

### 7.2 表格列
| 列 | 内容 |
|---|---|
| 任务类型 | 由 `triggerType` 翻译："指标重算" |
| 子名称 | `taskKey`·`taskKeyName`（如 `M_0046·零售一般性存款余额-员工`；无名回退编码） |
| 执行状态 | 中文 badge（见字典） |
| 开始时间 | `startTime` |
| 结束时间 | `endTime`（空显示 `-`） |
| 发起人 | `startedBy`·`startedByName`（如 `12094108·雷栋`；无名回退工号） |
| 操作 | "详情"按钮 → 抽屉 |

### 7.3 过滤区 + 刷新
- 状态下拉、数据日期范围（el-date-picker range）、指标（编码/名称关键字→`taskKey`）、发起人（工号）。
- **手动刷新**按钮；分页（pageSize 默认 20，最大 100）。

### 7.4 详情抽屉
点"详情"或行 → el-drawer：展示该任务全部字段 + **`errorMsg` 全文**（错误原因）+ `resultPreviewJson`/`paramsJson` 预览（含 SubjectStats 的 total/success/failed，只读展示）。

### 7.5 字典（前端映射）
- 状态：`PENDING`→待执行、`RUNNING`→执行中、`SUCCESS`→完成、`PARTIAL_FAILED`→部分失败、`FAILED`→失败。
- 触发来源：`RECALC`→指标重算（首期只此一类）。
- badge 配色：完成=绿、执行中=蓝、部分失败=橙、失败=红、待执行=灰。

## 8. 测试

**后端（TDD）**
- `trigger_type` 落库：`MetricCalcService` 单测断言 `insert` 的 `PerfRunTask.triggerType` 等于入参；`HistoryRecalcService` 父/子任务 triggerType=RECALC。
- 过滤：Mapper IT 按 `triggerType/taskKey/startedBy/dataDate 范围` 查询命中正确。
- DTO 增强：`pageDto` 单测 mock metricName/name 解析，断言 `taskKeyName`/`startedByName` 拼装 + 查不到留 null。
- Controller：新参数透传 + `@BizAuth(LIST)` baseline 不破。

**前端**
- vite 构建通过；人工验证：过滤/分页/状态翻译/详情抽屉看 error_msg。

## 9. 扩展性
- `task_type × trigger_type` 组合可扩展到 KPI 计算（`KPI_RUN`）、定时执行（`SCHEDULED`）等；页面加 tab 或放开 `triggerType`/`taskType` 过滤即可，无需重构。
- 首期只做"指标重算"一类，其余触发来源的落库回填按需增量补。

## 10. 交付边界
- 后端：1 DDL + 实体字段 + 落库透传（MetricCalc/HistoryRecalc）+ 端点过滤参数 + Mapper 条件 + DTO 增强（metricName/startedByName 批量解析）。
- 前端：1 新页 `TaskMonitor.vue` + api + 菜单项 + 字典。
- 非目标：自动轮询、实时进度条、其他任务类型的完整接入、历史 METRIC_RUN 的 trigger_type 回填。
