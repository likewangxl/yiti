# 红色引擎任务域数据模型说明

> 版本：v1.0
> 最后更新：2026-08-31
> 适用模块：`red-engine-center`
> 本文是已实施结构的模型说明，不是可执行 DDL，也不替代 DBA 审批。

## 1. 边界与实施口径

本次为任务管理、任务处理、两级审核、四大维度进度、逾期扣分和异步导出新增独立任务域表。结构已直接实施到隔离库 `yit_test`，未修改 `yiti` 或正在使用的 `yiti_test`，未引入 Flyway，也未提交 DDL 文件。后续生产库结构变更仍须由 DBA 按审批结果实施。

正式实现沿用红色引擎现有 Spring/MyBatis-Plus/平台文件契约和后台组件风格。此前交付的前端原型只作为业务流程、字段和分支参考，不复制原型技术栈、mock 数据或视觉实现，不改动既有红色引擎页面元素。

本模型使用平台现有事实表：

- `RE_PARTY_ORG`：党组织主数据；
- `PT_USER`：平台员工主数据；
- `FILE_OBJECT`：治理中心文件对象；
- `RE_SUBMIT`：既有四大维度材料上报记录；任务桥接表保留旧入口兼容；
- `SYS_DICT`：平铺字典，明细项使用 `dict_type=RE_ITEM_CODE`，不在任务域复制字典表。

## 2. 领域关系

```text
RE_TASK
 ├─ RE_TASK_FILE_TYPE       允许上传类型
 ├─ RE_TASK_TARGET          党支部/员工目标范围
 └─ RE_TASK_INSTANCE         每个周期的实际窗口
      └─ RE_TASK_BRANCH_ASSIGNMENT  支部维度分配
           ├─ RE_TASK_TODO            员工首页待办
           ├─ RE_TASK_SUBMISSION      同一 assignment 内版本化提交
           │    └─ RE_TASK_SUBMISSION_FILE  平台文件对象
           ├─ RE_TASK_RE_SUBMIT_REL   与既有 RE_SUBMIT 的四维桥接
           ├─ RE_TASK_STATUS_HISTORY   状态审计轨迹
           ├─ RE_TASK_DIMENSION_PROGRESS 四维上传进度
           └─ RE_TASK_DEDUCTION        逾期扣分

RE_TASK ── RE_TASK_EXPORT_TASK         异步 ZIP/Excel 导出任务
```

所有任务域主键使用 `BIGINT` 自增，异步导出任务使用平台风格的字符串 ID。任务域字符串默认使用 `utf8mb4_unicode_ci`；引用 `PT_USER.USER_ID`、`FILE_OBJECT.ID` 的列显式对齐平台的 `utf8mb4_general_ci`，避免外键字符集不一致。

## 3. 表与字段

### 3.1 `RE_TASK`：任务定义

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ID` | BIGINT | 主键 |
| `TASK_NO` | VARCHAR(64) | 业务任务编号，唯一 |
| `TITLE` | VARCHAR(200) | 任务标题，服务层按非空校验 |
| `DESCRIPTION` | TEXT | 多行任务说明，可粘贴网页链接 |
| `NATURE` | VARCHAR(20) | `SCHEDULED` 定时任务 / `TEMPORARY` 临时任务 |
| `TYPE_CODE` | VARCHAR(50) | 任务类型；`FOUR_DIMENSION` 表示四大维度材料上报 |
| `AUDIENCE_TYPE` | VARCHAR(30) | `ALL_BRANCHES` / `SPECIFIED_BRANCHES` / `SPECIFIED_EMPLOYEES` |
| `CYCLE` | VARCHAR(20) | `WEEK_START`、`WEEK_END`、`MONTH_START`、`MONTH_END`、`QUARTER_START`、`QUARTER_END` |
| `DURATION_DAYS` | SMALLINT UNSIGNED | 定时窗口持续天数，含首尾 |
| `START_AT` / `END_AT` | DATETIME | 临时任务时间窗 |
| `EFFECTIVE_FROM` / `EFFECTIVE_TO` | DATE | 定时任务生效范围，可为空 |
| `REQUIRES_FILE` | TINYINT | 是否要求文件：0 否、1 是 |
| `STATUS` | VARCHAR(20) | `DRAFT` / `PUBLISHED` / `PAUSED` / `CLOSED` / `CANCELLED` |
| `PUBLISHED_AT` / `PUBLISHED_BY` | DATETIME / VARCHAR(50) | 发布时间和发布人 |
| `VERSION_NO` | INT | 乐观锁版本 |
| `CREATED_BY`、`CREATE_TIME`、`UPDATED_BY`、`UPDATE_TIME` | — | 平台审计字段 |
| `DELETED` | TINYINT | 软删：0 有效、1 删除 |

数据库检查约束保证定时/临时任务的基础字段分支、临时任务结束时间不早于开始时间、四大维度类型只能为定时任务、状态值和软删值合法；周期实际自然长度仍由服务层按年月/周计算并校验。

索引：任务编号唯一；按状态、删除标识、任务性质/类型/周期、时间窗和生效范围建立查询索引。

### 3.2 `RE_TASK_FILE_TYPE`：任务允许文件类型

记录任务开启文件上传后的允许类型。`FILE_TYPE_CODE`、`FILE_TYPE_NAME`、扩展名/MIME 白名单、大小上限、顺序和启用状态均由任务配置维护。任务未要求上传文件时不应创建类型行；类型校验由服务层执行，实际对象上传必须复用治理中心 `FileApi`。

唯一约束为任务、类型编码和扩展名组合；外键 `TASK_ID → RE_TASK.ID`。

### 3.3 `RE_TASK_TARGET`：任务对象范围

`TARGET_TYPE=BRANCH` 时填写 `BRANCH_ID`，`TARGET_TYPE=EMPLOYEE` 时填写 `EMPLOYEE_ID`，数据库检查约束保证二者互斥且与类型匹配。`TARGET_KEY` 由服务层规范化为 `BRANCH:<id>` 或 `EMPLOYEE:<userId>`，用于同一任务目标去重；`TARGET_LABEL` 是展示快照。

外键：`TASK_ID → RE_TASK.ID`、`BRANCH_ID → RE_PARTY_ORG.ID`、`EMPLOYEE_ID → PT_USER.USER_ID`。选择“全部党支部”时由 `AUDIENCE_TYPE` 表达全量范围，不要求为每个党支部复制目标行；指定范围才落目标行。

### 3.4 `RE_TASK_INSTANCE`：周期任务实例

任务定义只描述规则，本表保存实际可处理窗口。`PERIOD_KEY` 在任务内唯一：定时任务使用年月/季度等周期键，临时任务使用一次性键。`WINDOW_START_AT`、`WINDOW_END_AT` 是员工待办和逾期判断的唯一时间窗口；状态为 `PENDING`、`OPEN`、`CLOSED` 或 `CANCELLED`。

唯一约束为 `TASK_ID + PERIOD_KEY`，用于周期生成幂等；窗口结束时间不得早于开始时间。外键 `TASK_ID → RE_TASK.ID`。

### 3.5 `RE_TASK_BRANCH_ASSIGNMENT`：支部任务分配

一个任务实例对一个党支部最多一条分配记录。`STATUS` 覆盖 `UNREPORTED`、`BRANCH_PENDING`、`ORG_PENDING`、`APPROVED`、`REJECTED_BY_BRANCH`、`REJECTED_BY_ORG` 和 `CLOSED`；`CURRENT_VERSION` 与最新提交版本保持一致，重提不新建 assignment。

唯一约束为 `TASK_INSTANCE_ID + BRANCH_ID`，并建立支部、状态和最近提交时间索引。外键指向任务实例、党支部和最近提交人平台用户。

### 3.6 `RE_TASK_TODO`：员工首页待办

任务窗口开始后，为实际收到任务的员工生成待办。`ROLE_CODE` 支持 `REPORTER`、`BRANCH_SECRETARY`、`ORG_REVIEWER`，默认报送员；`STATUS` 为 `PENDING`、`COMPLETED` 或 `CANCELLED`。提交完成后服务层将对应报送员待办置为完成，首页只查询当前窗口且仍为待处理的记录。

唯一约束为 `ASSIGNMENT_ID + EMPLOYEE_ID + ROLE_CODE`，防止重复生成；外键指向支部分配和 `PT_USER.USER_ID`。

### 3.7 `RE_TASK_SUBMISSION`：任务提交版本

临时任务在线填报、支部审核和组织审核均使用本表。`ASSIGNMENT_ID` 是重提复用的业务主键，`VERSION_NO` 从 1 递增，唯一约束为 `ASSIGNMENT_ID + VERSION_NO`。填报内容分为临时任务文本 `CONTENT_TEXT` 和可扩展结构化 `FORM_DATA`，四维任务可记录 `DIMENSION_CODE`/`ITEM_CODE`。

状态值与冻结状态机一致：

```text
BRANCH_PENDING → BRANCH_APPROVED → ORG_PENDING → APPROVED
       └──────→ REJECTED_BY_BRANCH ─┐
ORG_PENDING ───→ REJECTED_BY_ORG ────┴→ 新版本 BRANCH_PENDING
```

`BRANCH_REVIEWER_ID`、`ORG_REVIEWER_ID`、审核时间和 `REVIEW_OPINION` 由服务端回填。数据库约束保证两种驳回状态的意见非空；接口还必须以 `@NotBlank` 校验“驳回意见必填”。外键指向任务、实例、assignment、党支部和相关平台用户。

### 3.8 `RE_TASK_SUBMISSION_FILE`：任务提交附件

记录提交版本与治理中心文件对象的关系和展示快照（名称、大小、类型、顺序）。唯一约束为 `SUBMISSION_ID + FILE_OBJECT_ID`，外键为提交版本和 `FILE_OBJECT.ID`；本模块不自建对象存储，不绕过 `FileApi`。

### 3.9 `RE_TASK_RE_SUBMIT_REL`：任务与既有四维上报桥接

该表不是重提版本表，而是把新任务实例/支部 assignment 与既有 `RE_SUBMIT` 材料记录关联起来。四大维度继续使用原有材料上报入口时，服务层写入 `RE_SUBMIT_ID`，可选记录对应的任务提交版本以及维度/明细项编码。重提关系由 `RE_TASK_SUBMISSION.VERSION_NO` 表达。

唯一约束为 `TASK_INSTANCE_ID + ASSIGNMENT_ID + RE_SUBMIT_ID`；外键指向任务、实例、assignment、既有 `RE_SUBMIT` 和可选任务提交版本。

### 3.10 `RE_TASK_STATUS_HISTORY`：状态流转历史

以 `ACTION_CODE`、`FROM_STATUS`、`TO_STATUS`、操作人、意见和发生时间保存定义、实例、assignment、提交版本的状态轨迹。`TASK_ID` 必填，其余上下文外键按发生对象填写；系统调度动作允许 `OPERATOR_ID` 为空。支部/组织驳回动作的意见同样必须非空。

### 3.11 `RE_TASK_DIMENSION_PROGRESS`：四大维度进度

按 `ASSIGNMENT_ID + DIMENSION_CODE` 唯一记录维度进度。`UPLOAD_COUNT` 统计累计上传次数，`FIRST_UPLOADED_AT`、`COMPLETED_AT` 和提交版本引用保留完成依据。四大维度任务允许多次上传；同一季度某维度发生一次有效上传后即置为 `COMPLETED`，后续上传只增加次数和更新最近提交引用，不回退完成状态。

### 3.12 `RE_TASK_DEDUCTION`：逾期上报扣分

由任务实例、assignment 和支部定位逾期候选，保存扣分分值、必填原因、执行人、执行时间和 `PENDING`/`EXECUTED`/`CANCELLED` 状态。一个 assignment 只允许一条扣分记录，避免重复执行；服务层负责组织管理员权限、逾期条件和审计。

### 3.13 `RE_TASK_EXPORT_TASK`：异步导出任务

组织审核员从任务管理详情发起导出时保存任务参数、明细项编码 JSON、状态、行数、Sheet 数、结果 ZIP 文件对象、过期时间和错误信息。状态为 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`、`CANCELLED` 或 `EXPIRED`。

`SHEET_ROW_LIMIT` 数据库约束固定为 5000；单个 Excel 文件按每 Sheet 5000 行拆分，任务结果仍是一个 ZIP。临时任务导出不要求明细项；四大维度导出必须先选择 `RE_ITEM_CODE` 中的明细项。ZIP 有附件时按党支部建立子目录，无附件时仅保留 Excel。

## 4. 字典与外部表契约

- 明细项统一读取 `SYS_DICT` 中启用的 `dict_type=RE_ITEM_CODE`，当前字典已有 `1.1/1.2/1.3/2.1/2.2/4.1/4.2/sup` 等编码；后续可由治理字典维护调整。本模型不复制字典数据，也不建立无法表达“字典类型+编码”语义的硬外键。
- 用户字段只保存 `PT_USER.USER_ID`，姓名展示通过平台用户契约补齐。
- 党支部字段只保存 `RE_PARTY_ORG.ID`，支部层级和数据范围由服务层校验。
- 附件对象归治理中心 `FILE_OBJECT`，上传、绑定、下载和对象存储均走 `FileApi`/`ObsStorageClient`；任务表只保留关联和快照。
- 任务审核状态由红色引擎自管，不接 Flowable，不写 `business_key` 或 `BIZ_PROCESS_MAP`。

## 5. 结构验收摘要

2026-08-31 在隔离库完成只读核验：13 张任务域表均存在；主键、唯一约束、任务/实例/assignment/提交/附件/导出外键可见；数据库检查约束覆盖状态、时间窗、目标互斥、驳回意见和 Sheet 5000 限制；新表均为空，无事件或触发器。运行配置另由 `application-redengine-task-e2e.yml` 隔离，未启动应用。

可重复执行的只读验收脚本位于 `red-engine-center/src/test/resources/schema/task-domain-schema-acceptance.sh`，目标 schema 固定为 `yit_test`，凭据通过环境变量传入：

```bash
REDENGINE_TASK_DB_USERNAME=... REDENGINE_TASK_DB_PASSWORD=... \\
  bash red-engine-center/src/test/resources/schema/task-domain-schema-acceptance.sh
```

脚本只查询元数据和固定表行数，不创建、修改或删除数据库对象；它不是 DDL 交付物。
