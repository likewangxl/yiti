# 奖励分配（REWARD）导入与分配 设计方案

- 日期：2026-07-11
- 模块：performance-engine-center / eval 子域
- 分支：feat/eval-group-dept
- 相关：`docs/superpowers/specs/2026-06-10-eval-pending-task-import-design.md`（EVAL 评价任务导入，本方案镜像其架构）

## 1. 背景与目标

`EVAL_IMPORT_TYPE` 字典早已预留 `REWARD(奖励分配)` 类型（`EVAL_IMP_REWARD`），但 2026-06-10 一期仅打通了 `EVAL(评价任务)` 的「手工导入 → 打分」全链路，REWARD 为预留未实现。

本期实现 **奖励分配** 全链路：管理员导入一批「被分配人 + 分配人 + 原始值/兑现值/分配合计」，分配人在「我的待处理任务」中按部门汇总，进入某部门后把该部门共同的「分配合计」这笔奖金/积分分配给部门下每一个人（每人 > 0、求和严格等于分配合计），一次性提交。

与 EVAL（打分）并存、互不影响：REWARD 是「把一笔总额分配给一组人」，EVAL 是「给每个被打分人打一个分」。

## 2. 上传 Excel（8 列）

以业务方样例（微信图片_20260711174947）为准，列顺序：

| 序 | 列名 | 落库字段 | 校验 |
|---|---|---|---|
| 1 | 被分配人工号 | be_assigned_user_id | 非空；**原样快照，不校验用户有效性** |
| 2 | 被分配人用户姓名 | be_assigned_user_name | 快照 |
| 3 | 部门名称 | dept_name | 分组键（汇总/分配以此聚合） |
| 4 | 原始值 | original_value | 可解析 BigDecimal（展示用） |
| 5 | 分配值 | —（导入忽略） | 导入时该列忽略，提交时由分配人填入 |
| 6 | 兑现值 | cash_value | 可解析 BigDecimal（展示用） |
| 7 | 分配人工号 | assign_user_id | 非空 + **是 PT_USER 有效登录名**，校验后归一为 USER_ID |
| 8 | 分配合计 | assign_total | 可解析 BigDecimal 且 > 0；**同 (分配人+部门) 组内必须全部一致** |

分配人处理与 EVAL 完全一致：Excel「分配人工号」填登录名（PT_USER.USER_NAME），校验存在后归一为 USER_ID 入库；「我的待处理」按当前登录人 USER_ID 匹配。被分配人不校验，仅作快照。

## 3. 数据模型

### 3.1 新建表 `EVAL_REWARD_ITEM`

| 列 | 类型 | 说明 |
|---|---|---|
| item_id | BIGINT PK AUTO | 主键 |
| batch_id | BIGINT NOT NULL | 所属批次（→ EVAL_ASSIGN_BATCH） |
| assign_user_id | VARCHAR(64) NOT NULL | 分配人 USER_ID（归一化后） |
| be_assigned_user_id | VARCHAR(64) NOT NULL | 被分配人工号（原样快照） |
| be_assigned_user_name | VARCHAR(128) | 被分配人姓名（快照） |
| dept_name | VARCHAR(128) NOT NULL DEFAULT '' | 部门名称（分组键） |
| original_value | DECIMAL(18,4) | 原始值（展示） |
| cash_value | DECIMAL(18,4) | 兑现值（展示） |
| assign_total | DECIMAL(18,4) NOT NULL | 分配合计（组内一致，分配目标池） |
| assign_value | DECIMAL(18,4) NULL | 分配值（提交时填入） |
| submitted | TINYINT NOT NULL DEFAULT 0 | 0未提交/1已提交 |
| submit_time | DATETIME NULL | 提交时间 |
| create_time | DATETIME | 创建时间 |

索引：`idx_reward_assigner (assign_user_id, batch_id, dept_name, submitted)`（汇总/明细查询）、`idx_reward_batch (batch_id)`。

### 3.2 批次复用 `EVAL_ASSIGN_BATCH`

- `task_type = REWARD`，`source = IMPORT`
- `deadline` = 分配截止时间（导入必填）
- `status` 沿用：3=处理中(IMPORTING) / 2=草稿 / 0=进行中(ACTIVE) / 1=已结束 / 4=导入失败
- `total_rows` / `imported_count` / `error_summary` 复用

## 4. 后端组件（镜像 EVAL，均落 `com.bank.branch.platform.performance.eval.*`）

### 4.1 实体 / Mapper
- `entity/EvalRewardItem`（`@TableName("EVAL_REWARD_ITEM")`，MyBatis-Plus 注解）
- `mapper/EvalRewardItemMapper extends BaseMapper<EvalRewardItem>` + `EvalRewardItemMapper.xml`
  - `batchInsert`
  - `selectRewardPendingGroups(assignUserId)` → 按 (batch, dept) 聚合未提交、ACTIVE、未过期，附 `assign_total`
  - `selectByAssignerBatchDept(assignUserId, batchId, dept)` → 该组全部明细（含已提交）
  - `batchMarkAssigned(entries, submitTime)` 或逐条 `markAssigned(itemId, assignValue, submitTime)`（带 submitted=0 乐观条件）
  - 管理端：`selectByBatchId`/`countByBatchId`/`selectRewardBatchesByCondition`(过滤 task_type=REWARD)/`countRewardBatchesByCondition`/`selectDistinctAssignerIdsByBatch`/`deleteByBatchId`

### 4.2 DTO
- `EvalRewardImportRow`（8 列 EasyExcel 头）
- `EvalRewardPendingGroupDTO`：batchId / taskType / taskName / dept / pendingCount / deadline / assignTotal
- `EvalRewardPendingItemDTO`：itemId / beAssignedUserId / beAssignedUserName / deptName / originalValue / cashValue / assignTotal / assignValue / submitted
- `SubmitRewardBatchReq`：batchId / dept / entries[{ itemId, assignValue }]

### 4.3 Service
- `EvalRewardImportService`（镜像 `EvalAssignImportService`）：`parseRows` / `createImportingBatch(REWARD)` / `@Async processImport` → `validate` → all-or-none `persistSuccess`/`markFailed`
- `EvalRewardService`（用户端）：`listMyRewardPendingGroups` / `listMyRewardPendingItems` / `submitRewardBatch`
- `EvalRewardAdminService`（管理端）：`pageRewardBatches` / `getRewardBatchDetail` / `publishBatch`（可复用 `EvalAssignAdminService.publishBatch`，仅改批次状态；若复用则本类不重复实现）/ `exportRewardItems`

### 4.4 Controller
- `EvalRewardAdminController` `/api/admin/eval/reward`：`GET /import-template`、`POST /import`（taskType 固定 REWARD，异步受理返回 batchId+status=3）、`GET /batches`、`GET /batches/{id}`、`POST /batches/{id}/publish`、`GET /batches/{id}/export`
- `EvalRewardPendingController` `/api/eval/reward-tasks`：`GET`（我的奖励分配汇总）、`GET /items?batchId&dept`、`POST /submit-batch`

全部标 `@BizAuth(bizType = BizType.EVAL, action = ...)`；写/导入端点补 `@AuditLog`（与 EVAL 端点一致）。

## 5. 导入校验（逐行 + 跨行，all-or-none，异步）

逐行：
1. 分配人工号非空，且命中 PT_USER 有效登录名（`UserApi.mapUsernamesToEmpId` 批量），归一为 USER_ID
2. 被分配人工号非空（不校验用户）
3. 原始值/兑现值/分配合计可解析为 BigDecimal；分配合计 > 0
4. 分配值列导入时忽略

跨行：
5. 按 (assign_user_id 归一后 + dept_name) 分组，组内 `assign_total` 必须全部一致，否则记录行级错误

任一行错误 → 整批 all-or-none，明细一条不写，前 N 条错误写 `ERROR_SUMMARY` JSON（复用现有 `buildErrorSummary`/`errorKeep` 机制）。文件空/格式错走既有错误码 `EVAL_IMPORT_FILE_EMPTY`/`EVAL_IMPORT_FILE_INVALID`。

## 6. 分配提交（一次性 all-or-none）

`submitRewardBatch(assignerId, batchId, dept, entries)`：
1. 批次存在且 `status=ACTIVE(0)`（否则 `EVAL_BATCH_NOT_ACTIVE`）
2. 批次 `deadline` 未过（否则 `EVAL_TASK_CLOSED`）
3. entries 覆盖该 (分配人+批次+部门) 组**全部未提交明细**（给每个人都分配）；逐条校验归属当前分配人（否则 `EVAL_NO_PERMISSION`）、未提交（否则 `EVAL_SCORE_DUPLICATE`）、存在（否则 `EVAL_ASSIGN_ITEM_NOT_FOUND`）
4. 每人 `assignValue > 0`（否则 `EVAL_REWARD_ASSIGN_NOT_POSITIVE`）
5. `sum(assignValue)` 用 BigDecimal `compareTo` 严格等于该组 `assign_total`（否则 `EVAL_REWARD_SUM_MISMATCH`）
6. 同一 `@Transactional` 逐条 `markAssigned`，任一不满足整批回滚；提交后 `submitted=1` 锁定不可改

## 7. 新错误码（PERF-40069 起）

| 码 | 常量 | 文案 |
|---|---|---|
| PERF-40069 | EVAL_REWARD_ASSIGN_NOT_POSITIVE | 分配值必须大于0 |
| PERF-40070 | EVAL_REWARD_SUM_MISMATCH | 分配值之和必须等于分配合计 |

其余复用：`EVAL_ASSIGN_ITEM_NOT_FOUND` / `EVAL_NO_PERMISSION` / `EVAL_SCORE_DUPLICATE` / `EVAL_TASK_CLOSED` / `EVAL_BATCH_NOT_ACTIVE` / `EVAL_BATCH_NOT_DRAFT`。导入的「组内分配合计不一致」「数值解析失败」为行级错误消息（写 ERROR_SUMMARY），不占错误码。

## 8. 前端（汇总复用 + 明细分流）

- `MyTasks.vue`：汇总页并行调用 `/api/eval/pending-tasks`（EVAL）与 `/api/eval/reward-tasks`（REWARD）两个接口，客户端合并为一张待处理列表（按 `taskType` 打标）。点击 EVAL 行 → 现有打分明细；点击 REWARD 行 → 奖励分配明细。**后端 EVAL 服务/测试零改动。**
- 新增 `RewardTask.vue`（奖励分配明细）：
  - 表格列：被分配人工号 / 姓名 / 部门 / 原始值 / 兑现值 / 分配值（每行可编辑数值输入）
  - 顶部两条实时信息：
    - ①「给每个人分配 [X]」快捷填充：输入 X 后一键把全组每行分配值设为 X（如填 10 → 全员 = 10）
    - ②「距离还剩 [分配合计 − Σ分配值]」：未超显示剩余，超出显示负数
  - 提交按钮仅当 `Σ分配值 == 分配合计` 且全部 `> 0` 时可用，调 `/submit-batch`
- `api/eval.js`：新增 `rewardPendingGroups()` / `rewardPendingItems(batchId, dept)` / `submitRewardBatch(batchId, dept, items)` / 管理端 `importReward` / `rewardImportTemplate` / `rewardBatches` / `rewardBatchDetail` / `publishRewardBatch` / `exportRewardBatch`
- 管理端导入页：增加「奖励分配」导入入口 + 模板下载（镜像现有 EVAL 导入 UI，taskType=REWARD）

## 9. 迁移与资源

- SQL：`docs/superpowers/sql/2026-07-11-eval-reward-item.sql`（幂等 `CREATE TABLE IF NOT EXISTS EVAL_REWARD_ITEM` + 索引；`EVAL_IMP_REWARD` 字典项已存在，无需新增）。目标库 yiti + onepl_test_bootstrap 手工执行。
- 同步基线 `docs/schema/ddl-eval.sql`
- PT_RESOURCE：为新端点注册资源行（镜像 2026-06-10 `PERF_EVAL_23~27` 命名），配 `@BizAuth`

## 10. 测试（严格 TDD 红-绿-重构）

- `EvalRewardImportServiceTest`：有效导入 / 分配人工号无效 / 组内分配合计不一致 / 数值解析失败 / 分配合计≤0 / all-or-none
- `EvalRewardServiceTest`：汇总分组 / 明细 / 提交成功 / 分配值≤0 / 求和≠合计 / 超过截止 / 非归属 / 重复提交 / 未覆盖全组
- `EvalRewardItemMapperIT`：`selectRewardPendingGroups` 聚合 / `selectByAssignerBatchDept`
- `EvalRewardAdminControllerTest` / `EvalRewardPendingControllerTest`：端点鉴权 + 编排
- Mapper 一律 `extends BaseMapper`，仅 batchInsert/聚合/条件更新落 XML（遵守 MyBatis-Plus 红线）

## 11. 非目标（YAGNI）

- 不做「待办自动生成」（REWARD 仅手工导入路径）
- 不做中途草稿保存（仅一次性提交）
- 不做分配结果回写到绩效宽表/兑现流程（本期仅落 EVAL_REWARD_ITEM，后续如需再议）
- 不做被分配人用户有效性校验
