# 评价体系单一角色化设计（2026-06-10）

## 1. 背景与目标

当前评价模块（performance-engine-center/eval）中，一个人在评价体系里可同时持有
**1 个被评价标签（role_type=1，单选）+ N 个评价标签（role_type=2，多选）**，两类角色并存。

业务要求调整为：**人员标签页面不区分评价人与被评价人，一个人在评价体系中只能有一个角色**，
并同步调整评价规则页面相关内容。

### 1.1 核心决策（已与业务确认）

| 决策点 | 结论 |
|---|---|
| 角色模型 | **标签即角色，方向由规则决定**：`role_type` 作废，每人至多 1 个标签 |
| 改动范围 | **全栈**：前端 + 后端 + 数据迁移 |
| 规则页改动 | **仅术语中性化 + 保留自评排斥**（规则方向结构不可去） |
| 迁移冲突取舍 | **优先保留被评价标签**；无则取 id 最小的评价标签 |
| DTO/导入导出 | **一并收敛为单列**（导入模板格式随之变化） |
| 迁移执行库 | `yiti`（开发）、`onepl`（生产基线）、`onepl_test_bootstrap`（测试）三库 |

## 2. 核心思想

**标签即角色**。`EVAL_USER_TAG` 由「每人多行（role_type 1+2）」改为「**每人至多 1 行、无 role_type**」。

一个人是「被评价」还是「评价人」，**完全由评价规则引用其标签的位置决定**：

- 标签出现在某规则的 `EVAL_RULE.be_eval_tag_id`（评价对象槽）→ 该人在该规则下**被评价**；
- 标签出现在某规则的 `EVAL_RULE_GROUP.eval_tag_id`（评价人组槽）→ 该人在该规则下**是评价人**。

同一标签可在规则 A 作评价对象、在规则 B 作评价人——方向由规则承载，与人员侧无关。

规则本身「评价对象标签 + 评价人组标签/权重」的方向结构是评价的内在语义，**保持不变**。

## 3. 数据层设计

### 3.1 表结构变更

`EVAL_USER_TAG`：

```sql
-- 现状
PRIMARY KEY (ID),
UNIQUE KEY UK_USER_TAG_ROLE (USER_ID, TAG_ID, ROLE_TYPE)
ROLE_TYPE TINYINT NOT NULL COMMENT '1=被评价角色, 2=评价角色'

-- 目标
PRIMARY KEY (ID),
UNIQUE KEY UK_USER (USER_ID)            -- 每人至多一标签
-- ROLE_TYPE 列删除
```

`EVAL_TAG` / `EVAL_RULE` / `EVAL_RULE_GROUP` / `EVAL_TASK` / `EVAL_TASK_TARGET` /
`EVAL_SCORE` / `EVAL_USER_SETTING`：**不变**。

### 3.2 迁移脚本

`docs/superpowers/sql/2026-06-10-eval-single-role.sql`，执行前先
`mysqldump` 备份 `EVAL_USER_TAG`。次序：

1. **收敛为单标签**：每人保留一行——优先 `role_type=1`（被评价）；无则取该人 `role_type=2`
   中 `id` 最小者；删除其余行。
2. 删除冗余行后，`DROP KEY UK_USER_TAG_ROLE`，`ADD UNIQUE KEY UK_USER (USER_ID)`。
3. `ALTER TABLE EVAL_USER_TAG DROP COLUMN ROLE_TYPE`。

收敛 SQL 思路（保留逻辑用窗口/自连接实现，最终以脚本为准）：

```sql
-- 每人保留 1 行：role_type 升序（1 先于 2）、id 升序，取第一行；删其余
DELETE u FROM EVAL_USER_TAG u
JOIN (
  SELECT id,
         ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY role_type ASC, id ASC) AS rn
  FROM EVAL_USER_TAG
) ranked ON ranked.id = u.id
WHERE ranked.rn > 1;
```

> 预检：`SELECT user_id, COUNT(*) c FROM EVAL_USER_TAG GROUP BY user_id HAVING c>1;`
> 收敛后应返回 0 行，再执行 DDL 变更。

## 4. 后端设计（performance-engine-center/eval）

### 4.1 Entity

`EvalUserTag`：删除 `roleType` 字段。

### 4.2 Mapper

- `selectTagIdsByUserIdAndType(userId, roleType)` → **`selectTagIdByUserId(userId)`**
  （返回该人唯一标签 id，无则 null）。任务生成、打分匹配两处共用。
- XML 去 `role_type`：`batchInsert` 列去除、`selectByUserId` 投影去除、
  `selectUserTagsByUserIds` 去 `role_type` 投影。

### 4.3 Service

**EvalUserTagService**：

- `saveUserRoles(userId, beEvalTagId, evalTagIds)` → **`saveUserRole(userId, tagId)`**
  （覆盖式：删该人旧行，写入 ≤1 行）。
- `saveUserRolesWithSetting(userId, beEvalTagId, evalTagIds, evalEnabled)` →
  **`saveUserRoleWithSetting(userId, tagId, evalEnabled)`**。
- 删除**人员侧**局部排斥（评价≠被评价）逻辑——单标签后无对侧概念。
- `assembleRows`：每人装配单个 `tag`（EvalUserTagBriefDTO），不再分 beEval/eval。
- `batchBind` / `batchUnbind`（旧增量绑定，带 roleType）：若确为 dead code 则删除，
  否则收敛去 roleType。

**EvalTaskService.createTask**：`selectTagIdsByUserIdAndType(userId,1)` → 该人单标签
→ `evalRuleMapper.selectByBeEvalTagId(tag)` 匹配规则（逻辑等价，只换取标签方式）。

**EvalScoreService.resolveGroup**：`selectTagIdsByUserIdAndType(evalUserId,2)` → 该人单标签
→ 比对 `group.evalTagId`（逻辑等价）。

**EvalRuleService**：**不变**。`validateRoleConflict`（评价人组标签 ≠ 评价对象标签，
即规则内自评排斥）保留。`EVAL_ROLE_CONFLICT` 错误码保留（仅服务于规则自评排斥）。

### 4.4 Controller

- `SaveRolesReq{ beEvalTagId, evalTagIds, evalEnabled }` → **`{ tagId, evalEnabled }`**。
- `PUT /{userId}/roles` 调 `saveUserRoleWithSetting`。
- 旧 `POST` / `DELETE /api/admin/eval/user-tags`（bind/unbind，带 roleType）：核实 dead 后移除。
- 导入模板 `importTemplate`：sample 单角色列。

### 4.5 DTO

- `EvalUserRoleRowDTO`：删 `beEvalTag` / `evalTags`，新增单 `tag`（EvalUserTagBriefDTO）。
- `EvalUserTagRow`：删 `roleType`。
- `EvalUserTagImportRow`：`beEvalRoleName` + `evalRoleNames` → 单 `roleName`。
- `EvalUserRoleExportRow`：`beEvalRole` + `evalRoles` → 单 `role`。

### 4.6 Import Service

- 单角色列解析（命中扁平池 name→tagId）；空列校验「角色不能为空」。
- 删除局部排斥校验。
- 覆盖式入库调 `saveUserRoleWithSetting`。

## 5. 前端设计

### 5.1 UserTags.vue（人员标签页）

- 表格「被评价人角色」「评价人角色」两列 → 合并**单列「角色」**（显示该人唯一标签）。
- 编辑弹窗「被评价人角色」单选 + 「评价人角色」多选 → **一个「角色」单选**（可清空）。
- 删除互斥逻辑（`onBeEvalChange` / `evalOptions` 排除）。
- 导入说明 / 导出列名适配单列。
- 页头描述「维护人员的被评价人角色（单选）与评价人角色（多选）」→「维护人员评价角色（单选）」。

### 5.2 Rules.vue（评价规则页）

- **仅术语中性化**：「被评价人标签」label → **「评价对象标签」**（表头、表单、详情一致）。
- 评价人组、权重、评分方式、`evalTagOptions` 排除目标标签（自评排斥）**全部保留**。
- 结构、接口、字段名（`beEvalTagId`）后端契约保持，仅前端展示文案变化。

### 5.3 api/eval.js

- `saveUserRoles(userId, beEvalTagId, evalTagIds, evalEnabled)` →
  **`saveUserRoles(userId, tagId, evalEnabled)`**，body `{ tagId, evalEnabled }`。
- 删 `bindUserTags` / `unbindUserTags`（若后端端点移除）。

### 5.4 不改动

- `Tasks.vue` / `MyTasks.vue`：其中 `beEvalUserId/beEvalUserName` 是**任务的被评价目标人**
  （任务概念），与标签角色无关，**不改**。

## 6. TDD 与测试

红-绿-重构闭环。受影响测试：

| 测试 | 改造点 |
|---|---|
| `EvalUserRoleServiceTest` | saveUserRole 单标签语义、删局部排斥用例 |
| `EvalUserTagImportServiceTest` | 单角色列导入、删局部排斥用例 |
| `EvalUserTagExportTest` | 单 role 列导出 |
| `EvalTaskServiceTest` | createTask 用单标签取数 |
| `EvalScoreServiceTest` | resolveGroup 用单标签匹配 |
| `EvalRuleServiceTest` | 自评排斥保留，应仍绿（回归确认） |

跨模块 stale jar：改动后先 `mvn clean install -DskipTests` 再跑 bootstrap IT。

## 7. 运维与文档

- 迁移脚本三库执行（yiti / onepl / onepl_test_bootstrap），先备份。
- 更新 `docs/schema` 中 eval DDL 基线（若存在）。
- 更新 `performance-engine-center/CLAUDE.md` 变更日志（2026-06-10 单一角色化条目）。

## 8. 风险与边界

- **历史数据丢失**：同时持有评价标签的人，其评价标签在迁移中被丢弃（业务已确认优先保留被评价标签）。
  迁移前 mysqldump 备份，必要时可人工恢复。
- **方向语义不变**：任务生成与打分匹配的业务行为等价，仅取标签方式从 role_type 过滤改为单标签直取，
  回归测试须确认得分计算结果不变。
- **规则自评排斥保留**：避免同标签人自评/互评（业务确认保留）。
