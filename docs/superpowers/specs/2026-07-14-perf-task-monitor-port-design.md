# 指标重算任务监控页 移植到 master 设计/记录

- 日期：2026-07-14
- 分支：`feat/perf-task-monitor-port`（从当前 `master` @ `82efb631` 切出）
- 背景：功能原始产物遗留在旧分支 `feat/perf-task-monitor`（本地）/ `origin/feat/workflow-monitor-transfer`，从未合入 master（落后 master 127 提交），导致「指标重算任务监控页」在当前 master 缺失。
- 原始功能设计见同目录 `2026-07-09-perf-task-monitor-design.md`（随本次移植一并带入），本文件只记录**移植决策与执行**。

## 1. 目标
把旧分支上「指标重算任务监控页 `TaskMonitor.vue`」及其后端依赖**完整移植**到当前 master，页面按 `task_type=METRIC_RUN AND trigger_type=RECALC` 展示每指标一行的重算任务。

## 2. 关键决策
| # | 决策 | 取值 | 理由 |
|---|---|---|---|
| A | 识别模型 | **方案 B：移植 `trigger_type` 列**（持久化触发来源），页面按 `METRIC_RUN + trigger_type=RECALC` 过滤 | 与原页 1:1 一致，per-metric 粒度。master 的 `task_type=RECALC` 是 KPI 得分回算，与本页正交、不冲突 |
| B | 移植方式 | **cherry-pick 原始 8 个提交**（借 git 三方合并），而非合并陈旧分支 | 分支落后 master 127 提交，直接 merge 会搅乱 master；cherry-pick 只重放净变更 |
| C | 冲突面 | 极小 | 分支 fork 点 `6469e37b` 已含 master 的 `dataScopeFilter`，查询层无需额外协调；master 自 fork 后仅动过 `router/index.js`、`MetricCalcServiceTest.java` 两文件，均由 git 自动三方合并成功、无冲突标记 |
| D | 工作位置 | `lijh/yiti` 切 feature 分支，不直接改 master；完成后经确认再合并/推送 | 历史上 master 的 commit/push 从 lijh/yiti 发起 |

## 3. 移植内容（8 提交净变更）
- **后端**：`PerfRunTask` 加 `triggerType` 字段；`MetricCalcService`/`HistoryRecalcService` 落库 `trigger_type`（回算=RECALC）；`RunTaskQuery` + `PerfRunTaskService`/`PerfRunTaskMapper(.java/.xml)` 加 `triggerType/taskKey/startedBy/dataDateFrom-To` 过滤（叠加在既有 `dataScopeFilter` 之上）；`PerfRunTaskDTO` 增 `taskKeyName`(指标名) / `startedByName`(发起人姓名) 批量解析；`PerfRunTaskController` 透出新参数。
- **前端**：`views/perf/TaskMonitor.vue` + `api/perf.js#listRunTasks` + `router` 路由 `perf/task-monitor`「任务监控」。
- **DB/菜单**：迁移 SQL（`PERF_RUN_TASK.trigger_type` + `idx_type_trigger`，幂等）+ 菜单 SQL（`M_PERF_TASK_MONITOR` 挂 `M_GROUP_PERF`，复用 `P_PERF_RT_LIST` 角色集）。
- **文档**：原始 design/plan + trigger_type 未执行脚本清单登记。

## 4. DB 现状（dev = yiti）
2026-07-09 原始工作当时已把 DDL/菜单落到共享 dev 库并留存至今，本次经只读预检确认**均已在位**，无需再执行：
- `PERF_RUN_TASK.trigger_type` 列存在；`M_PERF_TASK_MONITOR` 菜单存在且值与 SQL 一致（URL `/perf/task-monitor`、rank 7、父 `M_GROUP_PERF`、19 个角色绑定）。
- ⚠️ 现有 6583 行 `PERF_RUN_TASK` 全为 `METRIC_RUN` 且 `trigger_type=NULL`（旧代码未落库），故**页面按 RECALC 过滤当前为空**，符合原设计「历史行不回填」预期；需新代码跑一次指标重算后方有 RECALC 行。

## 5. 验证
- 全模块 `mvn clean install -DskipTests`：成功（exit 0）。
- `mvn test -pl performance-engine-center`：**1096 通过 / 0 失败 / 0 错误**；其中 `MetricCalcServiceTest`(17)含 `triggerType=RECALC` 落库断言、`HistoryRecalcServiceTest`(13)、`PerfRunTaskServiceTest`(10)含过滤+DTO 增强用例。
- `PerfRunTaskMapperIT`（failsafe，验证 trigger_type/date/startedBy 的 SQL 层过滤）：见执行记录。
- 前端 `vite build`：成功（✓ built in ~10s）。
- 未驱动的部分：受 dev sidecar 网关+鉴权，未经真实 HTTP 驱动页面；且无 RECALC 数据行，页面暂空（预期）。可选后续：新后端跑一次重算生成 RECALC 行做端到端演示。

## 6. 交付边界 / 后续
- 交付：feature 分支上的完整代码 + 已就绪的 DB。
- 待用户确认后：合并 `feat/perf-task-monitor-port` → master 并 push。
- 非目标：历史 METRIC_RUN 的 trigger_type 回填、其他触发来源(SCHEDULED/MANUAL)接入、自动轮询。
