# 内部相互评价模块设计方案

> 日期：2026-05-27
> 归属模块：performance-engine-center（eval 子域）
> 需求来源：docs/dafen.md

## 1. 概述

### 1.1 业务目标

为银行内部提供多层级、多角色的相互评价能力，作为绩效考核体系的一部分。管理员统一发起评价活动，评价人对被评价人打总分（10~100 整数），提交后不可修改。系统按"组内平均、组间加权"公式自动计算最终得分。

### 1.2 核心特性

- **双标签体系**：每个人员拥有"被评价人标签"和"评价人标签"两套独立标签
- **以被评价人为中心的关系配置**：为每类被评价人配置评价人组及权重
- **组内平均、组间加权**：同一评价人组内多人取平均分，各组按权重汇总
- **管理员发起、一次性打分、不可修改**
- **分数下限 10 分**：低于 10 系统阻止提交
- **Quartz 定时过期**：任务截止后自动关闭并触发得分计算

### 1.3 模块归属决策

放入 `performance-engine-center` 模块，包名 `com.bank.branch.platform.performance.eval`。理由：
- 评价是绩效考核体系的子域
- 复用已有 Quartz 基础设施和 AuthQueryApi 依赖
- 需求规模适中（7 张表、~25 个 Java 文件），不足以独立成模块
- 表前缀 `EVAL_*` 与绩效的 `PERF_*` / `KPI_*` 天然区分

## 2. 包结构

```
com.bank.branch.platform.performance/
├── eval/
│   ├── controller/
│   │   ├── EvalTagController.java          # 标签字典 CRUD
│   │   ├── EvalRuleController.java         # 评价关系规则配置
│   │   ├── EvalTaskController.java         # 评价任务管理（发起、关闭、进度）
│   │   └── EvalScoreController.java        # 评价人打分（我的评价）
│   ├── service/
│   │   ├── EvalTagService.java             # 标签维护
│   │   ├── EvalRuleService.java            # 规则配置 + 权重校验
│   │   ├── EvalTaskService.java            # 任务发起 + 动态解析 + 得分计算
│   │   └── EvalScoreService.java           # 打分提交 + 校验
│   ├── mapper/
│   │   ├── EvalTagMapper.java / .xml
│   │   ├── EvalUserTagMapper.java / .xml
│   │   ├── EvalRuleMapper.java / .xml
│   │   ├── EvalRuleGroupMapper.java / .xml
│   │   ├── EvalTaskMapper.java / .xml
│   │   ├── EvalTaskTargetMapper.java / .xml
│   │   └── EvalScoreMapper.java / .xml
│   ├── entity/
│   │   ├── EvalTag.java
│   │   ├── EvalUserTag.java
│   │   ├── EvalRule.java
│   │   ├── EvalRuleGroup.java
│   │   ├── EvalTask.java
│   │   ├── EvalTaskTarget.java
│   │   └── EvalScore.java
│   └── job/
│       └── EvalTaskExpireJob.java          # Quartz Job：过期任务关闭 + 计算得分
├── api/
│   ├── EvalQueryApi.java                   # 对外查询接口（供 report 模块未来引用）
│   └── dto/
│       └── EvalTaskSummaryDto.java         # 任务概要 DTO
```

## 3. 数据库表设计

所有表名大写、`EVAL_` 前缀，字段使用 `USER_ID` 对齐 `PT_USER.USER_ID`。

### 3.1 EVAL_TAG（标签字典表）

| 字段 | 类型 | 说明 |
|------|------|------|
| TAG_ID | BIGINT PK AUTO_INCREMENT | 主键 |
| TAG_NAME | VARCHAR(50) NOT NULL | 标签名称 |
| TAG_TYPE | TINYINT NOT NULL | 1=被评价人标签, 2=评价人标签 |
| STATUS | TINYINT DEFAULT 1 | 1=启用, 0=停用 |
| CREATE_TIME | DATETIME | 创建时间 |
| UPDATE_TIME | DATETIME | 更新时间 |

### 3.2 EVAL_USER_TAG（人员-标签关联表）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT PK AUTO_INCREMENT | 主键 |
| USER_ID | BIGINT NOT NULL | 关联 PT_USER.USER_ID |
| TAG_ID | BIGINT NOT NULL | 关联 EVAL_TAG.TAG_ID |
| UNIQUE KEY (USER_ID, TAG_ID) | | 防重复 |

### 3.3 EVAL_RULE（评价关系规则主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| RULE_ID | BIGINT PK AUTO_INCREMENT | 主键 |
| RULE_NAME | VARCHAR(100) NOT NULL | 规则名称 |
| BE_EVAL_TAG_ID | BIGINT NOT NULL | 被评价人标签 ID |
| STATUS | TINYINT DEFAULT 1 | 1=启用, 0=停用 |
| CREATE_TIME | DATETIME | 创建时间 |
| UPDATE_TIME | DATETIME | 更新时间 |
| UNIQUE KEY (BE_EVAL_TAG_ID) | | 同一标签只能有一条规则 |

### 3.4 EVAL_RULE_GROUP（规则-评价人组明细表）

| 字段 | 类型 | 说明 |
|------|------|------|
| GROUP_ID | BIGINT PK AUTO_INCREMENT | 主键 |
| RULE_ID | BIGINT NOT NULL | 所属规则 |
| GROUP_TYPE | TINYINT NOT NULL | 1=按标签选人, 2=部门员工组 |
| EVAL_TAG_ID | BIGINT | type=1 时的评价人标签 ID |
| WEIGHT | DECIMAL(5,2) NOT NULL | 权重百分比（如 50.00） |
| SORT_ORDER | INT DEFAULT 0 | 排序 |

### 3.5 EVAL_TASK（评价任务表）

| 字段 | 类型 | 说明 |
|------|------|------|
| TASK_ID | BIGINT PK AUTO_INCREMENT | 主键 |
| TASK_NAME | VARCHAR(200) NOT NULL | 任务名称 |
| START_TIME | DATETIME NOT NULL | 开始时间 |
| END_TIME | DATETIME NOT NULL | 截止时间（Quartz 触发依据） |
| STATUS | TINYINT DEFAULT 0 | 0=进行中, 1=已结束 |
| CREATE_BY | BIGINT | 创建人 USER_ID |
| CREATE_TIME | DATETIME | 创建时间 |
| UPDATE_TIME | DATETIME | 更新时间 |

### 3.6 EVAL_TASK_TARGET（任务-被评价人明细）

| 字段 | 类型 | 说明 |
|------|------|------|
| TARGET_ID | BIGINT PK AUTO_INCREMENT | 主键 |
| TASK_ID | BIGINT NOT NULL | 所属任务 |
| BE_EVAL_USER_ID | BIGINT NOT NULL | 被评价人 USER_ID |
| RULE_ID | BIGINT NOT NULL | 发起时快照的规则 ID |
| FINAL_SCORE | DECIMAL(5,1) | 最终得分（计算后填入） |
| UNIQUE KEY (TASK_ID, BE_EVAL_USER_ID) | | 同一任务同一人不重复 |

### 3.7 EVAL_SCORE（评价打分记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| SCORE_ID | BIGINT PK AUTO_INCREMENT | 主键 |
| TASK_ID | BIGINT NOT NULL | 任务 ID |
| TARGET_ID | BIGINT NOT NULL | 关联 EVAL_TASK_TARGET |
| EVAL_USER_ID | BIGINT NOT NULL | 评价人 USER_ID |
| GROUP_ID | BIGINT NOT NULL | 所属评价人组 |
| SCORE | INT NOT NULL | 打分 10~100 |
| SUBMIT_TIME | DATETIME NOT NULL | 提交时间 |
| UNIQUE KEY (TARGET_ID, EVAL_USER_ID) | | 同一评价人对同一被评价人只打一次分 |

> 打分记录一旦提交不可更新或删除。

## 4. REST API 设计

### 4.1 标签管理（管理端）

| 方法 | URL | 说明 | RESOURCE_ID |
|------|-----|------|-------------|
| GET | `/api/admin/eval/tags` | 分页查询标签列表 | PERF_EVAL_1 |
| POST | `/api/admin/eval/tags` | 新建标签 | PERF_EVAL_2 |
| PUT | `/api/admin/eval/tags/*` | 编辑标签 | PERF_EVAL_3 |
| DELETE | `/api/admin/eval/tags/*` | 删除标签 | PERF_EVAL_4 |

### 4.2 人员标签关联（管理端）

| 方法 | URL | 说明 | RESOURCE_ID |
|------|-----|------|-------------|
| GET | `/api/admin/eval/user-tags` | 查询人员标签（按 userId 或 tagId） | PERF_EVAL_5 |
| POST | `/api/admin/eval/user-tags` | 批量绑定人员标签 | PERF_EVAL_6 |
| DELETE | `/api/admin/eval/user-tags` | 批量解绑人员标签 | PERF_EVAL_7 |

### 4.3 评价规则配置（管理端）

| 方法 | URL | 说明 | RESOURCE_ID |
|------|-----|------|-------------|
| GET | `/api/admin/eval/rules` | 分页查询规则列表 | PERF_EVAL_8 |
| GET | `/api/admin/eval/rules/*` | 规则详情（含评价人组） | PERF_EVAL_9 |
| POST | `/api/admin/eval/rules` | 新建规则（校验权重=100%） | PERF_EVAL_10 |
| PUT | `/api/admin/eval/rules/*` | 编辑规则 | PERF_EVAL_11 |
| DELETE | `/api/admin/eval/rules/*` | 删除规则 | PERF_EVAL_12 |

### 4.4 评价任务管理（管理端）

| 方法 | URL | 说明 | RESOURCE_ID |
|------|-----|------|-------------|
| GET | `/api/admin/eval/tasks` | 分页查询任务列表 | PERF_EVAL_13 |
| GET | `/api/admin/eval/tasks/*` | 任务详情 + 完成进度 | PERF_EVAL_14 |
| POST | `/api/admin/eval/tasks` | 发起评价任务（含截止时间） | PERF_EVAL_15 |
| PUT | `/api/admin/eval/tasks/*/close` | 手动关闭任务 + 触发计算 | PERF_EVAL_16 |

### 4.5 评价人打分（用户端 — "我的评价"）

| 方法 | URL | 说明 | RESOURCE_ID |
|------|-----|------|-------------|
| GET | `/api/eval/my-tasks` | 我的待评价任务列表 | PERF_EVAL_17 |
| GET | `/api/eval/my-tasks/*/targets` | 按部门分组的待评价人员 | PERF_EVAL_18 |
| POST | `/api/eval/scores` | 提交打分（10~100，不可修改） | PERF_EVAL_19 |

共 19 个接口，RESOURCE_ID 前缀 `PERF_EVAL_`。

## 5. 核心业务逻辑

### 5.1 任务发起流程

`EvalTaskService.createTask`：

1. 校验 END_TIME > 当前时间
2. 解析被评价人范围（管理员按部门/标签勾选）
3. 对每个被评价人：
   - 查找其被评价人标签 → 匹配 `EVAL_RULE`
   - 无匹配规则则跳过
   - 生成 `EVAL_TASK_TARGET` 记录，快照 `RULE_ID`
4. 注册 Quartz SimpleTrigger，以 `END_TIME` 为触发时间

**评价人不预生成空记录**：查询"我的待评价"时通过动态解析（标签/部门 → 用户列表）+ LEFT JOIN 已打分记录实现，避免大量空行。

### 5.2 打分提交

`EvalScoreService.submitScore`：

1. 校验任务状态 STATUS=0（进行中）
2. 校验当前时间 < END_TIME
3. 校验分数 10 ≤ score ≤ 100，整数
4. 校验当前用户属于该被评价人的某个评价人组
5. 校验唯一性（TARGET_ID + EVAL_USER_ID）
6. 插入 `EVAL_SCORE`，记录 GROUP_ID 和 SUBMIT_TIME
7. 无 UPDATE 接口，提交后不可修改

### 5.3 得分计算

`EvalTaskService.calculateScores`（任务关闭时触发）：

```
对每个 EVAL_TASK_TARGET:
  1. 查出关联 EVAL_RULE 的所有 GROUP
  2. 对每个 GROUP:
     - 收集该 GROUP 下所有 EVAL_SCORE
     - 无人打分 → 该组得分 = 0
     - 平均分 = SUM(score) / COUNT，四舍五入保留 1 位小数
     - 组得分 = 平均分 × weight / 100
  3. FINAL_SCORE = 各组得分之和，ROUND 到 1 位小数
  4. UPDATE EVAL_TASK_TARGET.FINAL_SCORE
```

**示例**（营销正职）：
- 省分行正行长组（权重 50%）：1 人打 80 → 组得分 80×0.5 = 40
- 省分行副行长组（权重 30%）：3 人打 70/80/90 → 平均 80 → 组得分 80×0.3 = 24
- 部门员工组（权重 20%）：10 人平均 85 → 组得分 85×0.2 = 17
- **总分** = 40 + 24 + 17 = 81.0

### 5.4 Quartz Job

- **Job Key**：`EVAL_TASK_EXPIRE`
- **触发方式**：每个任务发起时注册一个 SimpleTrigger，以 END_TIME 为触发时间
- **执行逻辑**：查询所有 STATUS=0 且 END_TIME ≤ NOW 的任务 → 逐个关闭 + 计算得分 → 更新 STATUS=1

## 6. 错误码

前缀复用 `PERF`，遵循 `{PREFIX}-{HTTP_STATUS}{SEQ}` 规范：

| 错误码 | 场景 |
|--------|------|
| PERF-40050 | 标签名称重复 |
| PERF-40051 | 被评价人标签已存在同名规则 |
| PERF-40052 | 评价人组权重之和不等于 100% |
| PERF-40053 | 分数不在 10~100 范围 |
| PERF-40054 | 已评价不可重复提交 |
| PERF-40055 | 任务已结束不可打分 |
| PERF-40056 | 当前用户无权评价该人员 |
| PERF-40057 | 截止时间必须晚于当前时间 |
| PERF-40058 | 被评价人无匹配的评价规则 |

## 7. 测试策略

### 7.1 单元测试（surefire，`*Test.java`）

- `EvalTagServiceTest` — 标签 CRUD + 名称重复校验
- `EvalRuleServiceTest` — 规则创建 + 权重校验（100%、超/不足）
- `EvalScoreServiceTest` — 打分校验（范围、唯一性、权限、任务状态）
- `EvalTaskServiceTest` — 得分计算逻辑（多组加权平均、无人打分、边界值）

### 7.2 集成测试（failsafe，`*IT.java`）

- `EvalTaskIT` — 完整流程：创建标签 → 配置规则 → 发起任务 → 打分 → 关闭 → 验证得分
- `EvalScoreIT` — 打分 REST 测试（权限校验、重复提交拒绝）

### 7.3 测试基础设施

- H2 内存库 + `@Sql` 加载 fixture
- 复用 performance-engine-center 已有的测试配置

## 8. 界面流程总览

```
标签维护（基础数据）
    → 配置评价关系规则（管理端设置页面）
    → 管理员发起评价任务（选范围 + 设截止时间）
    → 评价人登录看到待办（"我的评价"菜单）
    → 按部门-人员逐一打分（10~100 整数）
    → 提交后不可修改
    → 到期 Quartz 自动关闭 / 管理员手动关闭
    → 系统自动计算最终得分（存入 FINAL_SCORE）
```

**关键交互**：
- 规则页面权重和必须为 100%，增删组时实时校验
- 打分输入前后端双重校验 10~100 整数
- 提交后该条记录灰化显示"已评"
- 所有打分记录均记录评价人 USER_ID，不匿名
- 当前需求不展示评价结果，但数据完整存储

## 9. 扩展性

当前需求暂不展示评价结果，后续可扩展：
- 个人得分报告与排名
- 多维度指标（替换总分为指标矩阵）
- 评价周期定时任务
- 移动端适配
- report-analytics-center 通过 EvalQueryApi 引用评价数据生成报表
