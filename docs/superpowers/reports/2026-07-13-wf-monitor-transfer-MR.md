# 审批流监控 + 转交待认领 — 合并请求(MR)留档

- **日期**：2026-07-13
- **分支**：`feat/workflow-monitor-transfer`（已推送 origin，HEAD `2f1c6cba`）
- **目标**：合并入 `master`
- **MR 入口**：https://gitee.com/leidong01/yiti/pull/new/leidong01:feat/workflow-monitor-transfer...leidong01:master
- **规模**：本功能 26 个提交 / 45 文件 +3937/-173（workflow-center 267 单测+IT 全绿，前端 `npm run build` 通过）
- **状态**：功能实现完成、自身模块全绿；经 MR 合入 master（因 master 已领先约 94 提交、并入可视化设计器特性，需评审下解决冲突并复验）

> 说明：直推共享 `master` 不安全——本分支基点落后 `origin/master` 约 94 提交，其中含大范围重叠的**可视化审批流程设计器**特性（`FlowDesignController`、`service/flow/`、`WF_FLOW_*`、`WorkflowQueryApi`/`TodoQueryApi`、会签 MI、`ProcessCommandController`），改写了本功能同样要动的 workflow-center 源码。故走 MR 让分叉在评审下合并、并在合并后全量重编译验证。

---

## MR 标题

```
feat(workflow): 审批流监控 + 转交待认领（秘书岗按机构监控 + 两阶段转交）
```

## MR 正文

### 功能
为秘书岗提供审批流监控与两阶段转交。

**A 审批流监控（查看）**
- 数据范围驱动：秘书=ORG / 行长=ALL / 管理员直通；新 BizType `WORKFLOW_MONITOR`
- 新表 `WF_PROCESS_ORG`（参与机构快照，含存量回填 SQL）+ 索引 EXISTS 过滤
- 监控页 `system/workflow-monitor`（进行中/已完成、流程图/历史/节点）+ 菜单 + 端点权限

**B 两阶段转交（发起即锁定 → 认领生效 / 拒绝填理由 / 撤回）**
- 新表 `WF_TASK_TRANSFER`（生命周期 PENDING_ACCEPT/ACCEPTED/REJECTED/CANCELLED/INVALIDATED）+ 生成列唯一索引 `uk_active_task` 保单活
- 转交锁覆盖全部 5 个办理入口（含网关无会话 approveByEmp/rejectByEmp）；孤儿转交 INVALIDATED 兜底
- 4 个写操作全 @AuditLog；前端转交弹窗 + 工作台待认领/我发起的（可撤回）

### 验证
- workflow-center 267 单测+IT 全绿；前端 `npm run build` 通过
- 逐任务 + 终审 8 轮评审，已修复：getUserMainOrg 抛异常陷阱、collation 不一致、空 orgScope 的 IN() 语法错、转交锁被网关路径绕过(C1)、孤儿转交、发件箱 vs 撤回授权错配(I3) 等
- 顺带修复既有无关红点：auth H2 测试 schema 缺 `EXT_ORG_INFO.DEPT_NO`

### ⚠️ 评审/合并须知（重要）
1. 本分支基点落后 master 约 94 提交，master 已并入**可视化审批流程设计器**特性，二者都改 workflow-center 同批文件。合并时预计冲突点：`workflow-center/CLAUDE.md`、`TaskController`/`TaskOperationService`/`ProcessController`/`TodoQueryService`、`BizType.java` 计数与 `BizTypeTest`、`data.sql`/`seed-v1.sql` 种子。合并后请务必跑 `mvn clean install -DskipTests && mvn verify` + 前端 `npm run build` 复验。
2. 本分支携带其基线 `feat/perf-task-monitor` 的 8 个 perf 提交，建议在 perf-task-monitor 合入后再合本分支，或两者一并评审。
3. 新端点/菜单/数据范围已在 dev(yiti) 库落表（秘书 ROLE_ID=231 / 行长=2）；生产需重放种子 SQL（见 `docs/schema/ddl-workflow-monitor.sql` 与 seed 追加段）。

### 提交（本功能 26 个）
设计+计划 2 · 数据地基(WF_PROCESS_ORG/WF_TASK_TRANSFER+回填) · 监控查询/Controller/前端 · 两阶段转交发起/锁/认领/拒绝/撤回/Controller/前端 · 下线旧单阶段转交 · 终审整改(C1/I1/I2/I3)

---

## 关联文档
- 设计：`docs/superpowers/specs/2026-07-13-workflow-monitor-and-transfer-handshake-design.md`
- 计划：`docs/superpowers/plans/2026-07-13-workflow-monitor-and-transfer-handshake.md`
- DDL：`docs/schema/ddl-workflow-monitor.sql`
