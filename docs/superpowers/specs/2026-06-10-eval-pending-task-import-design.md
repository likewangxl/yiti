# 待处理任务（导入式评价任务）设计说明

> 2026-06-10 / performance-engine-center · eval 子域
> 本期不使用 superpowers skill 流程，但遵守 TDD 红线（service 层先红后绿）。

## 目标

把"新增评价任务"升级为"新增待处理任务"，待处理任务按**来源**分「自动生成 / 手工导入」、
导入再按**类型**分「评价任务 / 奖励分配」。本期只打通 **手工导入 → 评价任务导入** 全链路，
并把用户端「我的评价」页改造为「待处理任务」分组视图。自动生成 = 现有规则驱动逻辑，原样保留。

## 关键判断（已与用户确认）

1. **新建独立表**承载导入式显式配对，不动现有 `EVAL_TASK/TASK_TARGET/SCORE`（= 自动生成路径）。
2. 旧规则驱动「按任务去打分」前端入口**暂隐藏**，后端 `/api/eval/my-tasks*` 端点保留。
3. 来源 / 导入类型 / 评价类型 / 权重标签 全部走 governance `DictApi`；权重标签取值由用户在系统设置里自配，导入按库校验。
4. 评分口径沿用现有：数值 10~100；等级 5 档（非常满意100/比较满意95/满意85/一般75/不满意59）。

## 数据模型（新表）

- **EVAL_ASSIGN_BATCH**（一次导入 = 一批）：batch_id, batch_name, task_type(EVAL/REWARD),
  source(IMPORT/AUTO), deadline(打分截止时间), status, create_by, create_time。
- **EVAL_ASSIGN_ITEM**（每行 = 一条显式配对）：item_id, batch_id,
  eval_user_id/eval_user_name/eval_user_tag/eval_user_dept（打分人快照），
  be_eval_user_id/be_eval_user_name/be_eval_dept/be_eval_tag（被打分人快照），
  weight_tag, score_type(NUM/GRADE), score(可空), submitted(0/1), submit_time。
  UK(batch_id, eval_user_id, be_eval_user_id)；IDX(eval_user_id, submitted)。

## 字典（SYS_DICT）

| dict_type | dict_code / dict_label |
|---|---|
| EVAL_PENDING_SOURCE | AUTO/自动生成 · IMPORT/手工导入 |
| EVAL_IMPORT_TYPE | EVAL/评价任务 · REWARD/奖励分配 |
| EVAL_SCORE_TYPE | NUM/数值打分 · GRADE/等级打分 |
| EVAL_WEIGHT_TAG | 用户自配（本期 seed 两个示例：主要 / 次要） |

## 导入模板（10 列，按序）

被打分员工编号 · 被打分员工姓名 · 被打分员工部门 · 被打分员工标签 ·
打分员工编号 · 打分员工姓名 · 打分员工标签 · 打分员工部门 · 权重标签 · 评价类型

校验：① 双方工号非空且为系统有效员工（`UserApi.getUserByEmpIds`，部门/标签不校验）；
② 权重标签命中 `EVAL_WEIGHT_TAG`；③ 评价类型命中 `EVAL_SCORE_TYPE` 标签→映射 NUM/GRADE；
④ 文件内 (打分人,被打分人) 不重复。任一行错 → 整批不入库（all-or-none）。
截止时间随上传单独选择，作用于整批。

## 接口

管理端（`/api/admin/eval`）：
- `GET  /assign/import-template`  下载模板（PERF_EVAL_23）
- `POST /assign/import`（multipart file + taskType + deadline）导入（PERF_EVAL_24）

用户端（`/api/eval`）：
- `GET  /pending-tasks`              当前人按被打分部门汇总：{batchId, taskType(+label), dept, pendingCount, deadline}（PERF_EVAL_25）
- `GET  /pending-tasks/items?batchId=&dept=`  该部门明细（含 score_type 决定数值/等级）（PERF_EVAL_26）
- `POST /pending-tasks/submit`（{itemId, score}）逐人提交（PERF_EVAL_27）

提交校验：归属（item.eval_user_id==当前人）→ 未提交 → 截止未过 → 分数符合 score_type → 写 score+submitted。

## 待办（本期不做）

一个 batch 对一个打分人聚合即一个待办、全部明细提交完才完成 —— 预留接缝，参考代码后续提供，初期不实现。

## 前端

- 路由：`eval/my-tasks` 标题「我的评价」→「待处理任务」。
- `MyTasks.vue` 重构为「分组列表 → 处理 → 明细打分」三层；按 score_type 切数值框 / 等级。
- `Tasks.vue`「新建任务」→「新增待处理任务」向导：来源(自动/导入) → 导入类型(评价/奖励) →
  评价任务导入：下载模板 + 上传 + 选截止时间。自动生成 / 奖励分配本期占位「暂未开放」。
- 字典下拉用现成 `useDict`。
