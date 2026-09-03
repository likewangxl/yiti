# 绩效计算中心数据模型

> 本文只记录当前实体与表的职责和关键关系，不提供可执行 DDL。字段、类型、索引和约束以目标数据库 schema 与实体/Mapper 为准；schema 变更由 DBA 审批实施。

## 1. 配置与版本

| 表 | 用途 | 关键关系 |
| --- | --- | --- |
| SYS_CONTROL | 各维度当前有效数据日期和版本 | scope_dim + 有效标记；版本切换保留历史记录 |
| PERF_METRIC_DEF | 指标定义 | metric_code 唯一；base_dim、metric_level、calc_mode、calc_logic_type、val_slot、status |
| PERF_METRIC_REF | 指标引用关系 | 上游指标与被引用指标的有向关系 |
| PERF_KPI_SCHEME | KPI 方案 | scheme_code、cycle_type、status |
| PERF_KPI_ITEM | 方案指标项 | scheme_id → PERF_KPI_SCHEME；metric_code → PERF_METRIC_DEF；权重、倍数、上下限、公式 |
| PERF_TARGET_PLAN | 目标方案 | kpi_scheme_id → PERF_KPI_SCHEME；目标维度和周期 |
| PERF_TARGET_VALUE | 目标/基础值 | plan_id、主体、cycle_key、metric_code 组成业务定位；支持 upsert |

## 2. 结果、任务与导入导出

| 表 | 用途 |
| --- | --- |
| EMP_INDEX_RESULT | 员工维度指标宽表结果，按 data_date、version 和槽位保存 |
| ORG_INDEX_RESULT | 机构维度指标宽表结果 |
| CUST_INDEX_RESULT | 客户维度指标宽表结果 |
| KPI_RESULT | 员工 KPI 结果及明细 JSON |
| PERF_KPI_SCORE | KPI 计算的主体/指标计分明细 |
| PERF_RUN_TASK | 指标执行、回算、外部数据任务和运行状态 |
| PERF_METRIC_CALC_TASK | 按指标级别的批量计算任务 |
| PERF_METRIC_CALC_LOG | 指标批量计算日志 |
| PERF_KPI_CALC_LOG | KPI 计算批次日志 |
| PERF_EXPORT_TASK | KPI、指标、分配和明细导出任务及文件引用 |
| PERF_IMPORT_BATCH | 导入批次、类型、状态、源文件和错误信息 |
| PERF_STAT_SHOW_ARCHIVE_STATUS | 统计展示归档任务状态 |

结果查询必须使用服务解析出的日期/版本边界。宽表的动态 val_n 列只能由服务验证后的 val_slot 访问，不能由外部输入直接拼接。

## 3. 调整与分配

| 表 | 用途 |
| --- | --- |
| CUST_ALLOC_RELATION | 客户、业务种类、分配维度、员工、比例、有效期和来源批次 |
| PERF_ALLOC_ADJUST_APPLY | 分配调整申请主表、状态、业务键、流程实例和申请快照 |
| PERF_ALLOC_ADJUST_ITEM | 申请中的 ORIGIN/NEW 明细、员工/机构和比例 |
| PERF_TARGET_ADJUST_APPLY | 目标调整申请、主体、周期、状态和变更 JSON |

分配调整申请与明细以 apply_id 关联；审批批准只将 NEW 明细写入 CUST_ALLOC_RELATION，ORIGIN 作为申请快照。目标调整提交在本地事务中写申请并 upsert PERF_TARGET_VALUE。

## 4. 评价与奖励

| 表 | 用途 |
| --- | --- |
| EVAL_TASK | 评价任务主表 |
| EVAL_TASK_TARGET | 任务中的被评价对象 |
| EVAL_RULE | 评价规则 |
| EVAL_RULE_GROUP | 规则组、权重和评分方式 |
| EVAL_SCORE | 评价人对目标提交的评分 |
| EVAL_TAG | 评价标签 |
| EVAL_USER_TAG | 用户标签/角色配置 |
| EVAL_USER_SETTING | 评价用户设置 |
| EVAL_ASSIGN_BATCH | 评价分配导入批次 |
| EVAL_ASSIGN_ITEM | 评价分配明细 |
| EVAL_REWARD_ITEM | 奖励导入与待办明细 |

评价任务、目标、规则组和评分通过各自主键关联；同一目标与评价人评分的唯一性由服务校验和数据库约束共同保证。

## 5. 外部引用与所有权

认证/组织、文件、字典、客户和流程表不属于本模块。代码仅通过公开 API 或受控查询引用 PT_USER、文件对象、字典、客户主数据和流程信息。报表模块如需读取绩效结果，使用本模块公开查询契约或获批准的只读边界，不复制本页为跨模块表契约。

## 6. 变更规则

禁止在本文追加生产建表、迁移、清理或初始化 SQL。新增字段、索引、唯一键或状态值时，先更新实体/Mapper 与测试，再由 DBA 按目标 schema 审批实施，并同步 03、06、07 文档中受影响的契约。
