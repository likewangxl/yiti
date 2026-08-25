# 绩效计算中心 — 表结构 DDL

> 模块: performance-engine-center
> 版本: v1.0
> 最后更新: 2026-04-10
> DDL 源文件: `docs/sql/ddl-performance.sql`

本文档详细列出绩效计算中心管理的 17 张数据库表的完整 DDL、字段说明、索引设计和业务规则。所有表均在 MySQL 8.0 + InnoDB + utf8mb4 环境下创建，与 auth-permission-center、system-governance-center、workflow-center 共用同一数据库 Schema `onepl`。

---

## 1. 表清单

| # | 表名 | 中文名 | 类型 | 数据量预估 |
|---|------|--------|------|-----------|
| 1 | `SYS_CONTROL` | 数据版本控制表 | 配置 | ≈日数 x 3 (年度 1000+) |
| 2 | `PERF_METRIC_DEF` | 指标定义表 | 配置 | 200 ~ 500 |
| 3 | `PERF_METRIC_REF` | 指标引用关系表 | 配置 | 500 ~ 2000 |
| 4 | `PERF_KPI_SCHEME` | KPI 方案主表 | 配置 | 50 ~ 200 |
| 5 | `PERF_KPI_ITEM` | KPI 方案项表 | 配置 | 500 ~ 3000 |
| 6 | `PERF_TARGET_PLAN` | 目标方案表 | 配置 | 20 ~ 100/年 |
| 7 | `PERF_TARGET_VALUE` | 目标值/基础值表 | 业务 | 20万+/方案 |
| 8 | `PERF_IMPORT_BATCH` | 导入批次表 | 日志 | 10+/天 |
| 9 | `PERF_RUN_TASK` | 任务执行日志表 | 日志 | 100+/天 |
| 10 | `EMP_INDEX_RESULT` | 员工指标结果宽表 | 结果 | 员工数 x 日数 |
| 11 | `ORG_INDEX_RESULT` | 机构指标结果宽表 | 结果 | 机构数 x 日数 |
| 12 | `CUST_INDEX_RESULT` | 客户指标结果宽表 | 结果 | 客户数 x 日数 |
| 13 | `KPI_RESULT` | KPI 结果表 | 结果 | 员工数 x 周期数 |
| 14 | `CUST_ALLOC_RELATION` | 客户业绩分配关系表 | 业务 | 1000万+ |
| 15 | `PERF_ALLOC_ADJUST_APPLY` | 分配关系调整申请表 | 业务 | 100+/月 |
| 16 | `PERF_ALLOC_ADJUST_ITEM` | 分配关系调整明细表 | 业务 | 500+/月 |
| 17 | `PERF_TARGET_ADJUST_APPLY` | 目标修正申请表 | 业务 | 20+/月 |

**容量分级**:
- **结果表** (10/11/12/13): 海量数据, 必须分区
- **业务表** (7/14): 大量数据, 建议按时间分区
- **配置表** (1-6): 小量数据, 可全量缓存到 Redis
- **日志表** (8/9): 中等数据, 定期归档

---

## 2. 完整 DDL

### 2.1 sys_control — 数据版本控制表

**业务含义**: 作为全系统的"数据日期指针", 维护 EMP/ORG/CUST 三个维度"当前最新有效数据"指向的业务日期与版本号。所有绩效相关查询、KPI 计算、看板取数, 都应通过此表确定数据口径, 以保证系统内数据日期与版本的一致性。

```sql
CREATE TABLE `SYS_CONTROL` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `scope_dim` varchar(20) NOT NULL COMMENT '范围维度: EMP/ORG/CUST',
  `latest_data_date` date NOT NULL COMMENT '最新业务数据日期 (T-1)',
  `current_version` varchar(32) NOT NULL COMMENT '当前数据版本号, 建议格式: yyyyMMdd-VN',
  `is_valid` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效: 1-有效 0-历史版本',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注, 如重算原因',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人 emp_id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_date` (`scope_dim`, `latest_data_date`, `current_version`),
  KEY `idx_scope_valid` (`scope_dim`, `is_valid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据版本控制表';
```

**字段说明**:
| 字段 | 含义 | 示例 |
|---|---|---|
| `scope_dim` | 维度: `EMP`=员工, `ORG`=机构, `CUST`=客户 | `EMP` |
| `latest_data_date` | 该维度当前指向的业务数据日期 | `2026-04-09` |
| `current_version` | 版本号, 用于同一数据日期的重算版本区分 | `20260409-V1` |
| `is_valid` | 是否为"当前有效版本" | `1` |

**核心查询**:
```sql
-- 获取员工维度当前数据日期
SELECT latest_data_date, current_version
FROM sys_control
WHERE scope_dim = 'EMP' AND is_valid = 1
LIMIT 1;
```

---

### 2.2 perf_metric_def — 指标定义表

```sql
CREATE TABLE `PERF_METRIC_DEF` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `metric_code` varchar(64) NOT NULL COMMENT '指标编码 (全局唯一), 如 DEP_BAL_EMP',
  `metric_name` varchar(100) NOT NULL COMMENT '指标中文名',
  `metric_name_en` varchar(100) DEFAULT NULL COMMENT '指标英文名',
  `base_dim` varchar(20) NOT NULL COMMENT '基础维度: EMP/ORG/CUST',
  `metric_level` tinyint(1) NOT NULL DEFAULT '1' COMMENT '指标级别: 1-一级 2-二级 3-三级',
  `calc_freq` varchar(20) NOT NULL DEFAULT 'DAY' COMMENT '计算频率: DAY/MONTH/QUARTER/YEAR',
  `calc_mode` varchar(20) NOT NULL DEFAULT 'AUTO' COMMENT '计算方式: AUTO-自动 MANUAL-手工导入',
  `calc_logic_type` varchar(20) NOT NULL DEFAULT 'SQL' COMMENT '计算逻辑类型: SQL/PROC/EXPR/SUMMARY',
  `sql_text` text COMMENT '计算 SQL (calc_logic_type=SQL 或 PROC 时使用)',
  `expr_text` varchar(500) DEFAULT NULL COMMENT '表达式 (calc_logic_type=EXPR 时使用, 引用其他指标编码)',
  `summary_rule` varchar(20) DEFAULT NULL COMMENT '汇总规则 (calc_logic_type=SUMMARY 时): SUM/AVG/MAX/MIN',
  `ref_metric_codes` text COMMENT '依赖的指标编码 JSON 数组 (非 SQL 类型才有)',
  `val_slot` int(11) NOT NULL COMMENT '槽位号 1-200, 映射到结果宽表的 val_{slot} 列',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位: 元/户/笔/%',
  `decimal_places` tinyint(1) DEFAULT '4' COMMENT '小数位数',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED（V1.0；V1.1 规划扩展 DRAFT）',
  `cron_expr` varchar(120) DEFAULT NULL COMMENT 'V1.7 自定义 cron；留空按 calc_freq 推导默认',
  `subject_sql` longtext DEFAULT NULL COMMENT 'V1.7 EXPR/GROOVY 类型主体集合 SQL（SQL/PROC/SUMMARY 不需要）',
  `last_run_time` datetime DEFAULT NULL COMMENT 'V1.7 最近一次自动调度执行时间',
  `description` varchar(500) DEFAULT NULL COMMENT '指标描述',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人 emp_id',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人 emp_id',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_code` (`metric_code`),
  UNIQUE KEY `uk_base_dim_slot` (`base_dim`, `val_slot`, `deleted`),
  KEY `idx_base_dim` (`base_dim`),
  KEY `idx_status` (`status`),
  KEY `idx_metric_def_schedulable` (`status`, `calc_mode`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义表';
```

**字段说明要点**:
- `calc_logic_type = SQL`: 使用 `sql_text` 中的 SELECT 语句直接计算一级指标
- `calc_logic_type = PROC`: 使用 `sql_text` 中的存储过程名
- `calc_logic_type = EXPR`: 二级/三级指标, 使用 `expr_text` 中的表达式, 如 `(${DEP_BAL_EMP} + ${LOAN_BAL_EMP}) * 0.5`
- `calc_logic_type = SUMMARY`: 对 `ref_metric_codes` 数组中的指标按 `summary_rule` 做聚合
- `val_slot`: 每个维度下独立的槽位空间 (1-200), 映射到结果宽表的列
- `cron_expr` (V1.7)：自定义 Cron 表达式；留空时 `MetricCronResolver` 按 `calc_freq` 推导默认值（DAY=02:00 等）
- `subject_sql` (V1.7)：EXPR/GROOVY 类型指标的主体集合 SQL，由 `SubjectFetcher` 执行获取动态主体列表；SQL/PROC/SUMMARY 类型无需填写
- `last_run_time` (V1.7)：最近一次通过 Quartz 自动调度执行的时间戳，用于 HealthCheck 兜底补偿和监控统计

**V1.7 新增索引**：`idx_metric_def_schedulable (status, calc_mode, deleted)` 用于 `selectSchedulable` 查询（启动同步 + HealthCheck 扫描）

---

### 2.3 perf_metric_ref — 指标引用关系表

```sql
CREATE TABLE `PERF_METRIC_REF` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `metric_code` varchar(64) NOT NULL COMMENT '主指标编码 (复合指标)',
  `ref_metric_code` varchar(64) NOT NULL COMMENT '被引用的指标编码',
  `ref_level` tinyint(1) NOT NULL DEFAULT '1' COMMENT '引用层级 (由解析时计算, 用于避免循环依赖)',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_ref` (`metric_code`, `ref_metric_code`),
  KEY `idx_ref_metric` (`ref_metric_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标引用关系表';
```

**用途**: 加速级联刷新时的依赖查找 (给定 A, 查所有直接引用 A 的 B 指标)。由系统根据 `perf_metric_def.ref_metric_codes` 自动维护。

---

### 2.4 perf_kpi_scheme — KPI 方案主表

```sql
CREATE TABLE `PERF_KPI_SCHEME` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `scheme_code` varchar(64) NOT NULL COMMENT 'KPI 方案编码, 全局唯一',
  `scheme_name` varchar(100) NOT NULL COMMENT 'KPI 方案名称',
  `cycle_type` varchar(20) NOT NULL COMMENT '考核周期: MONTHLY/QUARTERLY',
  `open_detail` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否向员工开放明细: 1-开放 0-不开放',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expire_date` date DEFAULT NULL COMMENT '失效日期 (NULL 为长期有效)',
  `target_scope` varchar(500) DEFAULT NULL COMMENT '适用范围 JSON, 如适用岗位/机构',
  `emp_tag_scope` varchar(500) DEFAULT NULL COMMENT '员工标签范围(PERSON_TAG.TAG_ID CSV，空=不限定全员；2026-07-20 取代原 emp_role_scope 角色编码 CSV)',
  `description` varchar(500) DEFAULT NULL COMMENT '方案说明',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态: 0-草稿 1-启用 2-停用',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scheme_code` (`scheme_code`, `deleted`),
  KEY `idx_status` (`status`),
  KEY `idx_cycle_type` (`cycle_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KPI 方案主表';
```

---

### 2.5 perf_kpi_item — KPI 方案项表

```sql
CREATE TABLE `PERF_KPI_ITEM` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `scheme_id` varchar(32) NOT NULL COMMENT '关联 perf_kpi_scheme.id',
  `metric_code` varchar(64) NOT NULL COMMENT '指标编码 (引用 perf_metric_def.metric_code)',
  `weight` decimal(5,2) NOT NULL COMMENT '权重, 如 25.00 表示 25%',
  `multiplier` decimal(5,2) NOT NULL DEFAULT '1.00' COMMENT '系数/倍率, 用于灵活加权',
  `min_score` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '得分下限, 一般 0',
  `max_score` decimal(5,2) NOT NULL DEFAULT '200.00' COMMENT '得分上限, 一般 200 (100% 完成 = 100)',
  `score_formula_type` varchar(20) NOT NULL DEFAULT 'RATIO' COMMENT '打分算法: RATIO-达成率线性 STEP-阶梯',
  `formula_params` varchar(500) DEFAULT NULL COMMENT '打分参数 JSON, 如阶梯参数',
  `seq_no` int(11) NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scheme_metric` (`scheme_id`, `metric_code`),
  KEY `idx_metric_code` (`metric_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KPI 方案项表';
```

**业务规则**:
- 同一方案下 `metric_code` 唯一 (UK)
- 同一方案所有 item 的 `weight` 之和应为 100.00 (Service 层校验, 非数据库约束)

---

### 2.6 perf_target_plan — 目标方案表

```sql
CREATE TABLE `PERF_TARGET_PLAN` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `plan_code` varchar(64) NOT NULL COMMENT '目标方案编码, 全局唯一',
  `plan_name` varchar(100) NOT NULL COMMENT '目标方案名称',
  `kpi_scheme_id` varchar(32) NOT NULL COMMENT '关联的 KPI 方案 id',
  `target_dim` varchar(20) NOT NULL COMMENT '目标维度: EMP-按员工 ORG-按机构',
  `target_cycle` varchar(20) NOT NULL COMMENT '目标周期: YEAR/QUARTER/MONTH',
  `year` int(11) NOT NULL COMMENT '适用年份',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expire_date` date DEFAULT NULL COMMENT '失效日期',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态: 0-草稿 1-启用 2-停用',
  `description` varchar(500) DEFAULT NULL COMMENT '说明',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`, `deleted`),
  KEY `idx_kpi_scheme_id` (`kpi_scheme_id`),
  KEY `idx_status_year` (`status`, `year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标方案表';
```

---

### 2.7 perf_target_value — 目标值/基础值表

```sql
CREATE TABLE `PERF_TARGET_VALUE` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `plan_id` varchar(32) NOT NULL COMMENT '关联 perf_target_plan.id',
  `subject_type` varchar(20) NOT NULL COMMENT '对象类型: EMP/ORG',
  `subject_id` varchar(64) NOT NULL COMMENT '对象 ID (emp_id 或 org_code)',
  `cycle_key` varchar(20) NOT NULL COMMENT '周期键, 如 2026/2026Q1/2026-03',
  `metric_code` varchar(64) NOT NULL COMMENT '指标编码',
  `target_value` decimal(20,4) DEFAULT NULL COMMENT '目标值',
  `base_value` decimal(20,4) DEFAULT NULL COMMENT '基期值/期初值 (用于环比/同比)',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `source` varchar(20) NOT NULL DEFAULT 'IMPORT' COMMENT '来源: IMPORT-导入 MANUAL-手动 ADJUST-调整申请',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_subject_cycle_metric` (`plan_id`, `subject_type`, `subject_id`, `cycle_key`, `metric_code`),
  KEY `idx_subject` (`subject_type`, `subject_id`),
  KEY `idx_metric_code` (`metric_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标值/基础值表';
```

**UK 语义**: 一个方案下同一对象同一周期同一指标只能有一条目标值, 目标修正时走 `UPDATE`。

---

### 2.8 perf_import_batch — 导入批次表

```sql
CREATE TABLE `PERF_IMPORT_BATCH` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `batch_no` varchar(64) NOT NULL COMMENT '批次号, 格式: IMP-yyyyMMdd-HHmmss-xxxx',
  `import_type` varchar(32) NOT NULL COMMENT '导入类型: INDEX_RESULT/KPI_RESULT/TARGET_VALUE/ALLOC_RELATION',
  `dim` varchar(20) DEFAULT NULL COMMENT '维度: EMP/ORG/CUST (对结果类导入)',
  `as_of_date` date DEFAULT NULL COMMENT '数据日期',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_object_id` varchar(64) NOT NULL COMMENT 'MinIO 对象 ID',
  `file_md5` varchar(64) DEFAULT NULL COMMENT '文件 MD5, 防重复',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小 (字节)',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PARSING/PREVIEW/IMPORTING/SUCCESS/FAILED/PARTIAL',
  `total_rows` int(11) DEFAULT '0' COMMENT '总行数',
  `success_rows` int(11) DEFAULT '0' COMMENT '成功行数',
  `error_rows` int(11) DEFAULT '0' COMMENT '失败行数',
  `error_file_object_id` varchar(64) DEFAULT NULL COMMENT '错误文件 MinIO 对象 ID',
  `error_summary` varchar(1000) DEFAULT NULL COMMENT '错误汇总',
  `params_json` text COMMENT '导入参数 JSON',
  `imported_by` varchar(32) NOT NULL COMMENT '导入人 emp_id',
  `imported_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  `finished_time` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_import_type_date` (`import_type`, `as_of_date`),
  KEY `idx_imported_by_time` (`imported_by`, `imported_time`),
  KEY `idx_file_md5` (`file_md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入批次表';
```

---

### 2.9 perf_run_task — 任务执行日志表

```sql
CREATE TABLE `PERF_RUN_TASK` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `task_id` varchar(64) NOT NULL COMMENT '任务 ID, 格式: TASK-yyyyMMdd-HHmmss-xxxx',
  `task_type` varchar(32) NOT NULL COMMENT '任务类型: METRIC_TRIAL/METRIC_RUN/KPI_RUN/RECALC/DATA_SYNC',
  `task_key` varchar(100) DEFAULT NULL COMMENT '任务关键字, 如 metric_code 或 scheme_code',
  `data_date` date DEFAULT NULL COMMENT '数据日期',
  `data_version` varchar(32) DEFAULT NULL COMMENT '数据版本',
  `params_json` text COMMENT '执行参数 JSON',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  `progress` tinyint(3) DEFAULT '0' COMMENT '进度 0-100',
  `started_by` varchar(32) NOT NULL COMMENT '发起人 emp_id (系统发起 = SYSTEM)',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '耗时 (毫秒)',
  `error_msg` text COMMENT '错误信息',
  `result_preview_json` text COMMENT '结果预览 JSON (如试运行前 20 条)',
  `affected_rows` int(11) DEFAULT '0' COMMENT '影响行数',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_task_type_date` (`task_type`, `data_date`),
  KEY `idx_started_by_time` (`started_by`, `created_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';
```

---

### 2.10 emp_index_result — 员工指标结果宽表

**设计原则**: 采用 200 列槽位宽表, 避免每个指标一张表的表爆炸问题, 也避免长表 (metric_code, metric_value) 查询时的大量 JOIN。

```sql
CREATE TABLE `EMP_INDEX_RESULT` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `data_date` date NOT NULL COMMENT '数据日期',
  `version` varchar(32) NOT NULL COMMENT '数据版本号',
  `emp_id` varchar(32) NOT NULL COMMENT '员工 ID',
  `org_code` varchar(32) DEFAULT NULL COMMENT '所属机构 (冗余, 便于按机构查询)',
  `val_1` decimal(20,4) DEFAULT NULL COMMENT '槽位 1',
  `val_2` decimal(20,4) DEFAULT NULL COMMENT '槽位 2',
  -- ... 此处为节约文档空间省略 val_3 ~ val_199
  `val_200` decimal(20,4) DEFAULT NULL COMMENT '槽位 200',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_date_version` (`emp_id`, `data_date`, `version`),
  KEY `idx_date_version` (`data_date`, `version`),
  KEY `idx_org_code_date` (`org_code`, `data_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工指标结果宽表'
PARTITION BY RANGE (TO_DAYS(data_date)) (
  PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
  PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
  -- ... 按月滚动创建
  PARTITION pmax VALUES LESS THAN MAXVALUE
);
```

**val_1 ~ val_200 完整列定义 (生产 DDL)**:
```sql
-- 实际生产 DDL 应使用代码生成器生成 200 个 val_X 列
-- 示例:
`val_1` decimal(20,4) DEFAULT NULL,
`val_2` decimal(20,4) DEFAULT NULL,
`val_3` decimal(20,4) DEFAULT NULL,
-- ...
`val_200` decimal(20,4) DEFAULT NULL,
```

**设计解释**:
1. **UK(emp_id, data_date, version)** 保证同一员工同一日期同一版本只有一条记录, 支持 UPSERT
2. **INDEX(data_date, version)** 用于全量扫描某日某版本的数据
3. **INDEX(org_code, data_date)** 用于按机构聚合
4. **RANGE 分区** 按 `data_date` 分区, 便于历史归档和查询裁剪

---

### 2.11 org_index_result — 机构指标结果宽表

```sql
CREATE TABLE `ORG_INDEX_RESULT` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `data_date` date NOT NULL COMMENT '数据日期',
  `version` varchar(32) NOT NULL COMMENT '数据版本号',
  `org_code` varchar(32) NOT NULL COMMENT '机构编码',
  `parent_org_code` varchar(32) DEFAULT NULL COMMENT '上级机构 (冗余)',
  `val_1` decimal(20,4) DEFAULT NULL COMMENT '槽位 1',
  -- ... val_2 ~ val_199
  `val_200` decimal(20,4) DEFAULT NULL COMMENT '槽位 200',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_date_version` (`org_code`, `data_date`, `version`),
  KEY `idx_date_version` (`data_date`, `version`),
  KEY `idx_parent_org_date` (`parent_org_code`, `data_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构指标结果宽表'
PARTITION BY RANGE (TO_DAYS(data_date)) (
  PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
  PARTITION pmax VALUES LESS THAN MAXVALUE
);
```

---

### 2.12 cust_index_result — 客户指标结果宽表

```sql
CREATE TABLE `CUST_INDEX_RESULT` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `data_date` date NOT NULL COMMENT '数据日期',
  `version` varchar(32) NOT NULL COMMENT '数据版本号',
  `cust_id` varchar(64) NOT NULL COMMENT '客户 ID',
  `cust_manager_emp_id` varchar(32) DEFAULT NULL COMMENT '客户经理 (冗余, 主责分配)',
  `val_1` decimal(20,4) DEFAULT NULL COMMENT '槽位 1',
  -- ... val_2 ~ val_199
  `val_200` decimal(20,4) DEFAULT NULL COMMENT '槽位 200',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_date_version` (`cust_id`, `data_date`, `version`),
  KEY `idx_date_version` (`data_date`, `version`),
  KEY `idx_manager_date` (`cust_manager_emp_id`, `data_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户指标结果宽表'
PARTITION BY RANGE (TO_DAYS(data_date)) (
  PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
  PARTITION pmax VALUES LESS THAN MAXVALUE
);
```

---

### 2.13 kpi_result — KPI 结果表

```sql
CREATE TABLE `KPI_RESULT` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `emp_id` varchar(32) NOT NULL COMMENT '员工 ID',
  `scheme_id` varchar(32) NOT NULL COMMENT '关联 KPI 方案 id',
  `cycle_type` varchar(20) NOT NULL COMMENT '考核周期: MONTHLY/QUARTERLY',
  `cycle_date` date NOT NULL COMMENT '考核周期起始日期, 如 2026-03-01 代表 2026 年 3 月',
  `as_of_date` date NOT NULL COMMENT '数据截止日期 (每日快照)',
  `data_version` varchar(32) NOT NULL COMMENT '数据版本',
  `kpi_total_score` decimal(18,4) DEFAULT NULL COMMENT 'KPI 总得分',
  `detail_json` text COMMENT '每个指标的明细 JSON: {metric_code, target, actual, rate, score, weight}',
  `rank_in_org` int(11) DEFAULT NULL COMMENT '所在机构排名 (可选)',
  `rank_in_role` int(11) DEFAULT NULL COMMENT '所在岗位排名 (可选)',
  `calc_batch_id` varchar(64) DEFAULT NULL COMMENT '计算批次号 (关联 perf_run_task.task_id)',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_cycle_asof` (`emp_id`, `cycle_type`, `cycle_date`, `as_of_date`),
  KEY `idx_cycle_date` (`cycle_date`, `cycle_type`),
  KEY `idx_scheme_id` (`scheme_id`),
  KEY `idx_as_of_date` (`as_of_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KPI 结果表'
PARTITION BY RANGE (TO_DAYS(cycle_date)) (
  PARTITION p2026 VALUES LESS THAN (TO_DAYS('2027-01-01')),
  PARTITION p2027 VALUES LESS THAN (TO_DAYS('2028-01-01')),
  PARTITION pmax VALUES LESS THAN MAXVALUE
);
```

**UK(emp_id, cycle_type, cycle_date, as_of_date) 作用**:
- 同一员工同一考核周期每日只有一条"快照"记录
- 支持"每日更新当前周期 KPI": `INSERT ... ON DUPLICATE KEY UPDATE`
- 历史回算时按相同 UK 覆盖已有记录
- 员工看板查询指定周期指定截止日的唯一得分

---

### 2.14 cust_alloc_relation — 客户业绩分配关系表

**时间线设计**: 使用 `effective_date` + `end_date` 组成时间区间, 记录历史版本, 便于按业务日期回溯当时的分配关系。

```sql
CREATE TABLE `CUST_ALLOC_RELATION` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `cust_id` varchar(64) NOT NULL COMMENT '客户 ID',
  `alloc_dim` varchar(20) NOT NULL COMMENT '分配维度: RULE-按规则 ACCOUNT-按账户 RATIO-按比例',
  `biz_kind` varchar(32) DEFAULT NULL COMMENT '业务类型: DEPOSIT/LOAN/CARD/FUND/...',
  `account_no` varchar(64) DEFAULT NULL COMMENT '账户号 (alloc_dim=ACCOUNT 时必填)',
  `emp_id` varchar(32) NOT NULL COMMENT '分配到的员工 ID',
  `ratio` decimal(5,2) NOT NULL COMMENT '分配比例 0-100 (按账户=100, 按比例≤100)',
  `effective_date` date NOT NULL COMMENT '生效日期 (含)',
  `end_date` date NOT NULL DEFAULT '9999-12-31' COMMENT '失效日期 (含), 9999-12-31 表示当前有效',
  `source` varchar(20) NOT NULL DEFAULT 'SYSTEM' COMMENT '来源: SYSTEM-系统同步 ADJUST-人工调整',
  `source_batch_id` varchar(64) DEFAULT NULL COMMENT '来源批次号',
  `source_process_date` date DEFAULT NULL COMMENT '来源业务日期',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_cust_id_date` (`cust_id`, `effective_date`, `end_date`),
  KEY `idx_emp_id_date` (`emp_id`, `effective_date`, `end_date`),
  KEY `idx_account_no` (`account_no`),
  KEY `idx_effective_date` (`effective_date`),
  KEY `idx_source_batch_id` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户业绩分配关系表';
```

**时间线设计说明**:
1. 每条记录代表一个 (客户, 业务类型, 账户, 员工) 组合在某一时间段内的分配关系
2. 查询某个业务日期 D 的有效分配: `WHERE effective_date <= D AND end_date >= D`
3. 分配关系变更时:
   - 原记录: 将 `end_date` 更新为变更日 -1
   - 新记录: 插入 `effective_date = 变更日, end_date = 9999-12-31`
4. 不设 UK, 因为同一客户可分配给多个员工 (按比例)

---

### 2.15 perf_alloc_adjust_apply — 分配关系调整申请表

```sql
CREATE TABLE `PERF_ALLOC_ADJUST_APPLY` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `apply_no` varchar(64) NOT NULL COMMENT '申请单号, 格式: ALLOC-yyyyMMdd-xxxx',
  `cust_id` varchar(64) NOT NULL COMMENT '客户 ID',
  `cust_name` varchar(100) DEFAULT NULL COMMENT '客户名 (冗余)',
  `alloc_dim` varchar(20) NOT NULL COMMENT '分配维度: RULE/ACCOUNT/NEW',
  `biz_kind` varchar(32) DEFAULT NULL COMMENT '业务类型',
  `account_no` varchar(64) DEFAULT NULL COMMENT '账户号',
  `effective_date` date NOT NULL COMMENT '申请的生效日期',
  `reason` varchar(500) NOT NULL COMMENT '调整原因',
  `current_alloc_snapshot` text COMMENT '当前分配关系快照 JSON',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_APPROVAL/APPROVED/REJECTED/CANCELLED/SYNCED',
  `business_key` varchar(64) DEFAULT NULL COMMENT '工作流 business_key',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '工作流流程实例 ID',
  `process_status` varchar(32) DEFAULT NULL COMMENT '流程状态, 冗余: RUNNING/COMPLETED/TERMINATED',
  `approved_time` datetime DEFAULT NULL COMMENT '审批通过时间',
  `sync_time` datetime DEFAULT NULL COMMENT '同步到 cust_alloc_relation 的时间',
  `owner_emp_id` varchar(32) NOT NULL COMMENT '申请人 emp_id',
  `owner_org_id` varchar(32) NOT NULL COMMENT '申请人所属机构 (数据权限)',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`, `deleted`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_owner_emp_status` (`owner_emp_id`, `status`),
  KEY `idx_owner_org_status` (`owner_org_id`, `status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_process_instance_id` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分配关系调整申请表';
```

---

### 2.16 perf_alloc_adjust_item — 分配关系调整明细表

```sql
CREATE TABLE `PERF_ALLOC_ADJUST_ITEM` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键',
  `apply_id` varchar(32) NOT NULL COMMENT '关联 perf_alloc_adjust_apply.id',
  `item_kind` varchar(20) NOT NULL DEFAULT 'NEW' COMMENT '明细类型：NEW=新分配，ORIGIN=原业绩分配快照',
  `acct_no` varchar(64) DEFAULT NULL COMMENT '账号',
  `emp_id` varchar(32) NOT NULL COMMENT '分配到的员工 ID',
  `username` varchar(64) DEFAULT NULL COMMENT '员工登录名快照',
  `emp_chn_name` varchar(64) DEFAULT NULL COMMENT '员工中文名快照',
  `org_code` varchar(32) DEFAULT NULL COMMENT '机构编码快照',
  `org_name` varchar(128) DEFAULT NULL COMMENT '机构名称快照',
  `ratio` decimal(5,2) NOT NULL COMMENT '分配比例 0-100',
  `remark` varchar(200) DEFAULT NULL COMMENT '说明',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_emp_kind` (`apply_id`, `emp_id`, `item_kind`),
  KEY `idx_apply_id` (`apply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分配关系调整明细表';
```

> **2026-08-25 需求变更：item_kind 字段为最终结构前置。** 业绩调整应用模型与 MyBatis 映射按 `item_kind` 区分 `NEW`（新分配）和 `ORIGIN`（原业绩分配快照），草稿编辑通过明细全量重建保存 ORIGIN 修改，审批通过只落 NEW。当前物理 `yiti` 库的 `PERF_ALLOC_ADJUST_ITEM` 尚未提供 `item_kind`，需 DBA 先在目标库按审批结果补齐字段及 `(apply_id, emp_id, item_kind)` 唯一性约束后才能启用本实现；本次不执行数据库写入、不提交可执行 DDL，以上仅为设计/实施前置说明。

---

### 2.17 perf_target_adjust_apply — 目标修正申请表

```sql
CREATE TABLE `PERF_TARGET_ADJUST_APPLY` (
  `id` varchar(32) NOT NULL COMMENT '业务编码主键，与生产 DDL ddl-performance.sql 对齐',
  `apply_no` varchar(64) NOT NULL COMMENT '申请单号, 格式: TGTADJ-yyyyMMdd-xxxx',
  `plan_id` varchar(32) NOT NULL COMMENT '关联 perf_target_plan.id',
  `subject_type` varchar(20) NOT NULL COMMENT 'EMP/ORG',
  `subject_id` varchar(64) NOT NULL COMMENT '对象 ID',
  `cycle_key` varchar(20) NOT NULL COMMENT '周期键',
  `metric_code` varchar(64) DEFAULT NULL COMMENT '指标编码 (单项调整时有值; 整周期调整为 NULL)',
  `old_target_value` decimal(20,4) DEFAULT NULL COMMENT '原目标值',
  `new_target_value` decimal(20,4) DEFAULT NULL COMMENT '新目标值',
  `adjust_details_json` text COMMENT '整周期调整时的明细 JSON [{metric_code, old, new}]',
  `reason` varchar(500) NOT NULL COMMENT '修正原因',
  `effective_from_cycle_key` varchar(20) DEFAULT NULL COMMENT '从哪个周期开始生效 (影响历史回算范围)',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_APPROVAL/APPROVED/REJECTED/CANCELLED/APPLIED',
  `business_key` varchar(64) DEFAULT NULL COMMENT '工作流 business_key',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '工作流实例 ID',
  `process_status` varchar(32) DEFAULT NULL COMMENT '流程状态',
  `approved_time` datetime DEFAULT NULL COMMENT '审批通过时间',
  `applied_time` datetime DEFAULT NULL COMMENT '应用到 perf_target_value 的时间',
  `recalc_task_id` varchar(64) DEFAULT NULL COMMENT '触发的历史回算任务 ID',
  `owner_emp_id` varchar(32) NOT NULL COMMENT '申请人',
  `owner_org_id` varchar(32) NOT NULL COMMENT '所属机构',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`, `deleted`),
  KEY `idx_plan_subject` (`plan_id`, `subject_type`, `subject_id`),
  KEY `idx_owner_emp_status` (`owner_emp_id`, `status`),
  KEY `idx_owner_org_status` (`owner_org_id`, `status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_process_instance_id` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标修正申请表';
```

---

## 3. 索引说明

| 表 | 索引 | 类型 | 用途 |
|---|---|---|---|
| `SYS_CONTROL` | `uk_scope_date(scope_dim, latest_data_date, current_version)` | UK | 防重复写入, 保证每天每维度每版本唯一 |
| `SYS_CONTROL` | `idx_scope_valid(scope_dim, is_valid)` | KEY | 快速查找当前有效版本 (常用查询) |
| `PERF_METRIC_DEF` | `uk_metric_code(metric_code)` | UK | 指标编码全局唯一 |
| `PERF_METRIC_DEF` | `uk_base_dim_slot(base_dim, val_slot, deleted)` | UK | 同一维度下槽位号唯一 |
| `PERF_METRIC_DEF` | `idx_base_dim(base_dim)` | KEY | 按维度筛选指标 |
| `PERF_METRIC_REF` | `uk_metric_ref(metric_code, ref_metric_code)` | UK | 防重复引用 |
| `PERF_METRIC_REF` | `idx_ref_metric(ref_metric_code)` | KEY | 反向查询谁引用了我 (级联刷新用) |
| `PERF_KPI_SCHEME` | `uk_scheme_code(scheme_code, deleted)` | UK | 方案编码唯一 |
| `PERF_KPI_ITEM` | `uk_scheme_metric(scheme_id, metric_code)` | UK | 同一方案下一个指标只能有一个 KPI 项 |
| `PERF_TARGET_PLAN` | `uk_plan_code(plan_code, deleted)` | UK | 方案编码唯一 |
| `PERF_TARGET_PLAN` | `idx_status_year(status, year)` | KEY | 查询某年的启用方案 |
| `PERF_TARGET_VALUE` | `uk_plan_subject_cycle_metric(plan_id, subject_type, subject_id, cycle_key, metric_code)` | UK | 目标值唯一 |
| `PERF_TARGET_VALUE` | `idx_subject(subject_type, subject_id)` | KEY | 查询某员工/机构的目标 |
| `PERF_IMPORT_BATCH` | `uk_batch_no(batch_no)` | UK | 批次号唯一 |
| `PERF_IMPORT_BATCH` | `idx_import_type_date(import_type, as_of_date)` | KEY | 按类型+日期查询 |
| `PERF_IMPORT_BATCH` | `idx_file_md5(file_md5)` | KEY | 文件重复检测 |
| `PERF_RUN_TASK` | `uk_task_id(task_id)` | UK | 任务号唯一 |
| `PERF_RUN_TASK` | `idx_task_type_date(task_type, data_date)` | KEY | 按类型+日期查询 |
| `PERF_RUN_TASK` | `idx_started_by_time(started_by, created_time)` | KEY | 用户查询自己的任务 |
| `EMP_INDEX_RESULT` | `uk_emp_date_version(emp_id, data_date, version)` | UK | 同一员工同日期同版本唯一 |
| `EMP_INDEX_RESULT` | `idx_date_version(data_date, version)` | KEY | 全量扫描某日某版本 |
| `EMP_INDEX_RESULT` | `idx_org_code_date(org_code, data_date)` | KEY | 按机构聚合 |
| `ORG_INDEX_RESULT` | `uk_org_date_version(org_code, data_date, version)` | UK | 机构结果唯一 |
| `CUST_INDEX_RESULT` | `uk_cust_date_version(cust_id, data_date, version)` | UK | 客户结果唯一 |
| `CUST_INDEX_RESULT` | `idx_manager_date(cust_manager_emp_id, data_date)` | KEY | 客户经理聚合 |
| `KPI_RESULT` | `uk_emp_cycle_asof(emp_id, cycle_type, cycle_date, as_of_date)` | UK | KPI 快照唯一 |
| `KPI_RESULT` | `idx_cycle_date(cycle_date, cycle_type)` | KEY | 按周期查询 |
| `CUST_ALLOC_RELATION` | `idx_cust_id_date(cust_id, effective_date, end_date)` | KEY | 查询客户分配关系 (时间线) |
| `CUST_ALLOC_RELATION` | `idx_emp_id_date(emp_id, effective_date, end_date)` | KEY | 查询员工所管客户 |
| `CUST_ALLOC_RELATION` | `idx_source_batch_id` | KEY | 按同步批次溯源 |
| `PERF_ALLOC_ADJUST_APPLY` | `uk_apply_no(apply_no, deleted)` | UK | 申请号唯一 |
| `PERF_ALLOC_ADJUST_APPLY` | `idx_owner_org_status(owner_org_id, status)` | KEY | 机构维度权限过滤 |
| `PERF_ALLOC_ADJUST_APPLY` | `idx_business_key` | KEY | 按 business_key 查询 (工作流回调用) |
| `PERF_ALLOC_ADJUST_ITEM` | `uk_apply_emp_kind(apply_id, emp_id, item_kind)` | UK | 同一申请、同类明细内员工唯一；允许同一员工同时存在 NEW 与 ORIGIN |
| `PERF_TARGET_ADJUST_APPLY` | `uk_apply_no(apply_no, deleted)` | UK | 申请号唯一 |
| `PERF_TARGET_ADJUST_APPLY` | `idx_plan_subject(plan_id, subject_type, subject_id)` | KEY | 按计划+对象查询 |

---

## 4. 逻辑外键

本系统采用"逻辑外键", 不在数据库层创建真实 FK 约束 (性能+迁移考虑), 由 Service 层保证一致性。

| 源表 | 源字段 | 目标表 | 目标字段 | 说明 |
|---|---|---|---|---|
| `PERF_KPI_ITEM` | `scheme_id` | `PERF_KPI_SCHEME` | `id` | KPI 项属于 KPI 方案 |
| `PERF_TARGET_PLAN` | `kpi_scheme_id` | `PERF_KPI_SCHEME` | `id` | 目标方案绑定 KPI 方案 |
| `PERF_TARGET_VALUE` | `plan_id` | `PERF_TARGET_PLAN` | `id` | 目标值属于目标方案 |
| `PERF_TARGET_VALUE` | `metric_code` | `PERF_METRIC_DEF` | `metric_code` | 目标值关联指标 |
| `PERF_METRIC_REF` | `metric_code` | `PERF_METRIC_DEF` | `metric_code` | 指标引用关系 |
| `PERF_METRIC_REF` | `ref_metric_code` | `PERF_METRIC_DEF` | `metric_code` | 被引用指标 |
| `PERF_KPI_ITEM` | `metric_code` | `PERF_METRIC_DEF` | `metric_code` | KPI 项关联指标 |
| `PERF_ALLOC_ADJUST_ITEM` | `apply_id` | `PERF_ALLOC_ADJUST_APPLY` | `id` | 明细属于申请单 |
| `PERF_ALLOC_ADJUST_APPLY` | `cust_id` | `CUST_MASTER` (customer-marketing) | `cust_id` | 跨模块, 调 CustomerQueryApi 校验 |
| `PERF_ALLOC_ADJUST_ITEM` | `emp_id` | `PT_EMP` (auth) | `emp_id` | 跨模块, 调 EmpQueryApi 校验 |
| `PERF_TARGET_ADJUST_APPLY` | `plan_id` | `PERF_TARGET_PLAN` | `id` | 目标调整针对某个计划 |
| `PERF_TARGET_ADJUST_APPLY` | `metric_code` | `PERF_METRIC_DEF` | `metric_code` | 指定指标的调整 |
| `CUST_ALLOC_RELATION` | `cust_id` | `CUST_MASTER` (customer-marketing) | `cust_id` | 跨模块, 调 CustomerQueryApi 校验 |
| `CUST_ALLOC_RELATION` | `emp_id` | `PT_EMP` (auth) | `emp_id` | 跨模块, 调 EmpQueryApi 校验 |
| `EMP_INDEX_RESULT` | `emp_id` | `PT_EMP` (auth) | `emp_id` | 跨模块 |
| `ORG_INDEX_RESULT` | `org_code` | `PT_ORG` (auth) | `org_code` | 跨模块 |
| `KPI_RESULT` | `emp_id` | `PT_EMP` (auth) | `emp_id` | 跨模块 |
| `KPI_RESULT` | `scheme_id` | `PERF_KPI_SCHEME` | `id` | 模块内 |

**清理策略**:
- 软删除 `PERF_KPI_SCHEME` 时, Service 层校验是否存在 `PERF_TARGET_PLAN` 引用, 拒绝删除
- 软删除 `PERF_METRIC_DEF` 时, Service 层校验是否存在 `PERF_KPI_ITEM`、`PERF_TARGET_VALUE` 引用

---

## 5. 审计字段规范

所有"配置类"和"业务类"表必须包含以下审计字段:

```sql
`created_by` varchar(32) DEFAULT NULL COMMENT '创建人 emp_id',
`created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_by` varchar(32) DEFAULT NULL COMMENT '更新人 emp_id',
`updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
```

**由 MyBatis 拦截器 `AuditFieldInterceptor` 自动填充**:
- INSERT: 自动填充 `created_by`、`created_time`、`updated_by`、`updated_time`
- UPDATE: 自动填充 `updated_by`、`updated_time`

**例外** (结果类/日志类表):
- `EMP_INDEX_RESULT`、`ORG_INDEX_RESULT`、`CUST_INDEX_RESULT`、`KPI_RESULT`: 由批处理写入, 只保留 `created_time`、`updated_time`
- `PERF_RUN_TASK`: 只保留 `started_by`、`created_time`
- `PERF_IMPORT_BATCH`: 只保留 `imported_by`、`imported_time`

---

## 6. 宽表槽位映射设计

### 6.1 机制

`perf_metric_def.val_slot` 字段 (1~200) 决定了指标的计算结果存储到结果宽表的哪一列 (`val_{slot}`)。

**维度隔离**: 每个维度 (EMP/ORG/CUST) 的 val_slot 独立, 互不影响, 即:
- 员工维度指标 `DEP_BAL_EMP` 可用 val_slot=1
- 机构维度指标 `DEP_BAL_ORG` 也可用 val_slot=1
- 客户维度指标 `DEP_BAL_CUST` 也可用 val_slot=1

**约束**: UK(base_dim, val_slot, deleted) 保证同一维度下同一槽位只能分配给一个指标。

### 6.2 映射示例

| metric_code | metric_name | base_dim | val_slot | 存储列 |
|---|---|---|---|---|
| `DEP_BAL_EMP` | 员工存款日均余额 | EMP | 1 | `emp_index_result.val_1` |
| `LOAN_BAL_EMP` | 员工贷款日均余额 | EMP | 2 | `emp_index_result.val_2` |
| `FEE_INCOME_EMP` | 员工中收 | EMP | 3 | `emp_index_result.val_3` |
| `NEW_CUST_CNT_EMP` | 员工新增客户数 | EMP | 4 | `emp_index_result.val_4` |
| `DEP_BAL_ORG` | 机构存款日均余额 | ORG | 1 | `org_index_result.val_1` |
| `LOAN_BAL_ORG` | 机构贷款日均余额 | ORG | 2 | `org_index_result.val_2` |
| `DEP_BAL_CUST` | 客户存款日均余额 | CUST | 1 | `cust_index_result.val_1` |
| `AUM_CUST` | 客户 AUM | CUST | 2 | `cust_index_result.val_2` |

### 6.3 动态 SQL 生成示例

新增指标 `DEP_BAL_EMP` 后, 自动计算任务执行:

```sql
-- 由 MetricCalcService 根据 metric_def 拼接
INSERT INTO emp_index_result (emp_id, data_date, version, org_code, val_1)
SELECT
    t.emp_id,
    '2026-04-09' AS data_date,
    '20260409-V1' AS version,
    t.org_code,
    SUM(t.dep_bal_amt) AS val_1
FROM source_deposit_daily t
WHERE t.stat_date = '2026-04-09'
GROUP BY t.emp_id, t.org_code
ON DUPLICATE KEY UPDATE val_1 = VALUES(val_1);
```

### 6.4 槽位分配规则

- 1~100: 保留给一级指标
- 101~150: 保留给二级指标
- 151~200: 保留给三级指标

(由 `MetricService.allocateSlot()` 按规则分配, 人工也可在创建时指定)

### 6.5 读取与元数据解耦

业务查询必须通过 `metric_code` 而非 val_slot 访问, 由 Service 层翻译:

```java
// 正确用法
BigDecimal val = metricResultService.getValue("E99001", "2026-04-09", "DEP_BAL_EMP");
// 内部流程:
//   1. 查 perf_metric_def 拿到 val_slot=1
//   2. SELECT val_1 FROM emp_index_result WHERE ...
```

---

## 7. sys_control 业务规则

### 7.1 记录规则

1. **每天每维度只允许 1 条 `is_valid=1` 记录** (uk_scope_date 只约束唯一性, 有效性由 Service 层保证)
2. **维度间独立**: EMP/ORG/CUST 可以指向不同的 `latest_data_date`
3. 每个维度可有多条历史记录 (不同的 `current_version`, 只有一条 `is_valid=1`)

### 7.2 版本切换原子性

版本切换必须在事务内完成:

```sql
-- 1. 将原有效版本置为无效
UPDATE sys_control SET is_valid = 0
WHERE scope_dim = 'EMP' AND is_valid = 1;

-- 2. 插入或更新新版本为有效
INSERT INTO sys_control(scope_dim, latest_data_date, current_version, is_valid)
VALUES('EMP', '2026-04-09', '20260409-V1', 1)
ON DUPLICATE KEY UPDATE is_valid = 1, update_time = NOW();
```

**并发保护**: 对 `scope_dim` 加 Redis 分布式锁, Key = `perf:sys_control:switch:{scope_dim}`, TTL=30s。

### 7.3 历史查询

允许按 `scope_dim + latest_data_date` 查询历史版本:

```sql
-- 查询 2026-03-01 当时的员工维度数据版本
SELECT current_version
FROM sys_control
WHERE scope_dim = 'EMP' AND latest_data_date = '2026-03-01'
ORDER BY update_time DESC LIMIT 1;
-- 允许命中 is_valid=0 的记录
```

### 7.4 初始化时机

- **手动初始化**: 系统首次部署后, 运维手动 INSERT 一条记录
- **自动创建**: 首次数据导入成功后, 由 `MetricCalcService` 自动创建

---

## 8. 关键字段取值枚举

| 字段 | 取值 | 含义 |
|---|---|---|
| `scope_dim` / `base_dim` / `target_dim` | `EMP` | 员工 |
| | `ORG` | 机构 |
| | `CUST` | 客户 |
| `calc_mode` | `AUTO` | 自动计算 (由调度触发) |
| | `MANUAL` | 手工导入 (不参与自动计算) |
| `calc_logic_type` | `SQL` | SQL 查询 |
| | `PROC` | 存储过程 |
| | `EXPR` | 表达式, 引用其他指标 |
| | `SUMMARY` | 聚合规则 |
| `metric_level` | `1` | 一级 (原子指标) |
| | `2` | 二级 (依赖一级) |
| | `3` | 三级 (依赖二级) |
| `calc_freq` | `DAY` | 每日 |
| | `MONTH` | 每月 |
| | `QUARTER` | 每季 |
| | `YEAR` | 每年 |
| `summary_rule` | `SUM` | 求和 |
| | `AVG` | 均值 |
| | `MAX` | 最大 |
| | `MIN` | 最小 |
| `cycle_type` | `MONTHLY` | 月度考核 |
| | `QUARTERLY` | 季度考核 |
| `target_cycle` | `YEAR` / `QUARTER` / `MONTH` | 目标周期 |
| `metric_def.status` | `ACTIVE` | 启用 |
| | `DISABLED` | 停用 |
| `apply.status` (分配/目标调整) | `DRAFT` | 草稿 |
| | `IN_APPROVAL` | 审批中 |
| | `APPROVED` | 审批通过 (目标调整后进入 `APPLIED`, 分配调整后进入 `SYNCED`) |
| | `REJECTED` | 审批拒绝 |
| | `CANCELLED` | 撤回 |
| | `APPLIED` | 目标调整已应用到 perf_target_value |
| | `SYNCED` | 分配调整已同步到 cust_alloc_relation |
| `task_type` | `METRIC_TRIAL` | 指标试运行 |
| | `METRIC_RUN` | 指标正式执行 |
| | `KPI_RUN` | KPI 计算 |
| | `RECALC` | 历史回算 |
| | `DATA_SYNC` | 数据同步 |
| `run_task.status` | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELLED` | 任务状态 |
| `import_type` | `INDEX_RESULT` | 指标结果导入 |
| | `KPI_RESULT` | KPI 结果导入 |
| | `TARGET_VALUE` | 目标值导入 |
| | `ALLOC_RELATION` | 分配关系导入 |
| `import_batch.status` | `PENDING` / `PARSING` / `PREVIEW` / `IMPORTING` / `SUCCESS` / `FAILED` / `PARTIAL` | 导入状态 |
| `alloc_dim` | `RULE` | 按规则分配 |
| | `ACCOUNT` | 按账号分配 |
| | `RATIO` | 按比例分配 |

---

## 9. 分区与归档建议

### 9.1 分区策略

| 表 | 分区方式 | 粒度 | 说明 |
|---|---|---|---|
| `EMP_INDEX_RESULT` | RANGE(TO_DAYS(data_date)) | 月 | 结果表海量数据, 按月分区便于归档 |
| `ORG_INDEX_RESULT` | RANGE(TO_DAYS(data_date)) | 月 | 同上 |
| `CUST_INDEX_RESULT` | RANGE(TO_DAYS(data_date)) | 月 | 客户数据量最大, 必须分区 |
| `KPI_RESULT` | RANGE(TO_DAYS(cycle_date)) | 年 | 按考核周期分区, 便于年度归档 |
| `PERF_RUN_TASK` | RANGE(TO_DAYS(created_time)) | 季度 | 日志表 |
| `PERF_IMPORT_BATCH` | RANGE(TO_DAYS(imported_time)) | 季度 | 日志表 |

### 9.2 分区维护

使用事件调度器每月 1 日自动创建下月分区:

```sql
CREATE EVENT ev_create_partition_monthly
ON SCHEDULE EVERY 1 MONTH
STARTS '2026-05-01 02:00:00'
DO
  CALL sp_create_next_month_partition('emp_index_result', 'data_date');
```

### 9.3 归档规则

| 表 | 在线保留 | 归档方式 |
|---|---|---|
| `EMP_INDEX_RESULT` / `ORG_INDEX_RESULT` / `CUST_INDEX_RESULT` | 近 24 个月 | 按月 EXCHANGE PARTITION 到归档表 |
| `KPI_RESULT` | 近 3 年 | 按年归档 |
| `PERF_RUN_TASK` | 近 3 个月 | 定期 DROP 旧分区 |
| `PERF_IMPORT_BATCH` | 近 12 个月 | 归档到备份库 |
| `CUST_ALLOC_RELATION` | 全量保留 | 不归档 (审计需求) |

### 9.4 查询裁剪

所有查询结果宽表的 SQL 必须带上 `data_date` 条件, 否则会全分区扫描:

```sql
-- 正确
SELECT val_1, val_2 FROM emp_index_result
WHERE emp_id = 'E99001' AND data_date = '2026-04-09' AND version = '20260409-V1';

-- 错误 (全分区扫描)
SELECT val_1, val_2 FROM emp_index_result WHERE emp_id = 'E99001';
```

---

**文档结束**
