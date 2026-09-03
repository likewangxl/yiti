# 红色引擎数据模型

> 本文只记录当前实体和关系，不提供可执行 DDL。字段、类型、索引、外键及约束以目标库 schema 和 Mapper 为准，结构变更由 DBA 审批实施。

## 1. 任务定义与执行

| 表 | 作用 | 关键关系 |
| --- | --- | --- |
| RE_TASK | 已发布任务定义、性质、业务类型、周期和版本 | 任务根对象 |
| RE_TASK_FILE_TYPE | 任务允许的文件类型和配置快照 | task_id → RE_TASK |
| RE_TASK_TARGET | 全部支部、指定支部或指定员工的目标范围 | task_id → RE_TASK |
| RE_TASK_INSTANCE | 任务周期/临时窗口实例 | task_id → RE_TASK；period_key 识别窗口 |
| RE_TASK_BRANCH_ASSIGNMENT | 实例到支部的 assignment、当前状态和提交版本 | task_instance_id → RE_TASK_INSTANCE |
| RE_TASK_TODO | 报送员/审核员待办及可用时间 | assignment_id → RE_TASK_BRANCH_ASSIGNMENT |
| RE_TASK_SUBMISSION | assignment 内的版本化提交、正文、状态和反馈 | assignment_id + version_no |
| RE_TASK_SUBMISSION_FILE | 提交版本与治理文件对象的关联及展示快照 | submission_id → RE_TASK_SUBMISSION |
| RE_TASK_STATUS_HISTORY | 任务、实例、assignment、提交版本状态轨迹 | task_id，按上下文关联 |

任务状态与提交状态由红色引擎自管。重提递增 RE_TASK_SUBMISSION.version_no，assignment 复用；已驳回版本和状态历史保留，详情只选择当前版本。

## 2. 四维材料和进度

| 表 | 作用 |
| --- | --- |
| RE_TASK_RE_SUBMIT_REL | 任务提交版本与既有 RE_SUBMIT 材料桥接 |
| RE_TASK_DIMENSION_PROGRESS | assignment + dimension_code 的上传/完成进度 |
| RE_SUBMIT | 四维材料上报主记录 |
| RE_SUBMIT_FILE | 材料与治理文件对象关联 |

带完整任务上下文的材料写入 RE_SUBMIT 后，通过 RE_TASK_RE_SUBMIT_REL 关联当前任务提交版本；无任务上下文的材料只走 RE_SUBMIT 兼容路径。桥接重复时不重复计数，旧材料内容不能混入普通任务正文。

## 3. 逾期与导出

| 表 | 作用 |
| --- | --- |
| RE_TASK_DEDUCTION | 任务逾期扣分、assignment 和执行信息 |
| RE_TASK_EXPORT_TASK | 任务导出参数、状态、文件引用和过期时间 |
| RE_OVERDUE_DEDUCTION | 兼容驾驶舱逾期扣分记录 |

逾期记录与任务/assignment 绑定；导出任务以任务、操作者和选项形成幂等键，文件引用由治理 FileApi 管理。红色引擎不自建对象存储。

## 4. 组织、映射与兼容业务对象

| 表 | 作用 |
| --- | --- |
| RE_PARTY_ORG | 党组织树、层级和负责人信息 |
| RE_USER_PARTY_MAP | 平台用户与党组织映射 |
| RE_SCORE | 材料上报审核评分 |
| RE_MEMBER_SCORE | 党建成员/考核评分兼容数据 |
| RE_ANNUAL_RESULT | 驾驶舱年度结果兼容数据 |

组织和映射是红色引擎自有数据；用户主体来自 auth-permission-center 的公开 UserApi。兼容业务对象是否可通过 REST 访问由当前资源授权决定，不改变其表归属。

## 5. 外部引用

任务附件、导出文件和材料文件只保存治理文件对象编号；PT_USER、SYS_DICT 和平台锁表由其他模块拥有。红色引擎不直接 join 外部私有表，也不接 Flowable 表。

## 6. 变更规则

新增或调整状态、唯一键、索引和字段时，必须同步实体、Mapper、服务状态机、03/06/07 文档及测试。不要在本文追加建表、迁移、清理或环境验收 SQL。
