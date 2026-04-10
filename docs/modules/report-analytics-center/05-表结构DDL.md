# 报表分析中心 -- 表结构 DDL

> 版本：V1 | 最后更新：2026-04-10
> 模块代号：report-analytics-center
> DDL 源文件：`docs/schema/ddl-report.sql`（待生成）
> 种子数据：`docs/schema/seed-v1.sql` 第 10 节（SQL 探查白名单等 sys_config_kv 配置）

---

## 1. 表清单

| 序号 | 表名 | 说明 | 主键策略 | 所属 | V1 状态 |
|:---:|:---|:---|:---|:---:|:---:|
| 1 | rpt_saved_query | 动态查询保存方案 | UUID(id) varchar(32) | 自有 | 启用 |
| 2 | sql_probe_history | SQL 探查历史 | UUID(id) varchar(32) | 自有 | 启用 |
| 3 | rpt_snapshot_task | 快照任务配置（V1 预留，仅建表不启用） | UUID(id) varchar(32) | 自有 | 预留 |

**关键说明**：

- 本模块只有 **3 张自有表**，除此之外**全部通过 Api 调用其他模块获取数据**；
- 所有 `rpt_*` 前缀的表为报表模块专属，不允许其它模块直接读写；
- `sql_probe_history` 没有 `rpt_` 前缀是因为它属于"系统级探查日志"而非"业务报表数据"，但在报表模块内部管理；
- V1 不维护任何业务汇总快照表（如日/月汇总），所有数据通过 `MetricApi` 实时获取；
- V1 不使用 `rpt_snapshot_task`，仅保留表结构以便 V2 扩展。

---

## 2. 完整 DDL

### 2.1 rpt_saved_query -- 动态查询保存方案

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 方案 ID（UUID 主键） |
| emp_id | varchar(32) | NOT NULL | - | 员工工号（方案所属用户） |
| name | varchar(200) | NOT NULL | - | 方案名称（用户可编辑） |
| dim | varchar(20) | NOT NULL | - | 维度：EMP-员工, ORG-机构, CUST-客户 |
| subject_ids | text | NOT NULL | - | 对象 ID 列表（JSON 数组） |
| metric_codes | text | NOT NULL | - | 指标编码列表（JSON 数组） |
| version | int(11) | NULL | 0 | 乐观锁版本号 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_time | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

```sql
CREATE TABLE IF NOT EXISTS `rpt_saved_query` (
  `id` varchar(32) NOT NULL COMMENT '方案ID（UUID）',
  `emp_id` varchar(32) NOT NULL COMMENT '员工工号',
  `name` varchar(200) NOT NULL COMMENT '方案名称',
  `dim` varchar(20) NOT NULL COMMENT '维度：EMP/ORG/CUST',
  `subject_ids` text NOT NULL COMMENT '对象ID列表(JSON数组)',
  `metric_codes` text NOT NULL COMMENT '指标编码列表(JSON数组)',
  `version` int(11) DEFAULT '0' COMMENT '乐观锁版本号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id_time` (`emp_id`, `created_time`),
  KEY `idx_emp_id_name` (`emp_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态查询保存方案';
```

**设计要点**：

- `dim` 取值范围受应用层约束（EMP/ORG/CUST），不使用数据库枚举，便于 V2 扩展；
- `subject_ids` 与 `metric_codes` 为 JSON 数组，单方案对象数上限 100、指标数上限 20，由应用层校验；
- 每用户方案数上限 10 条，超出则在保存逻辑中删除 `created_time` 最旧的一条（同事务）；
- `version` 字段用于乐观锁，防止同一用户多个标签页并发编辑同一方案；
- `idx_emp_id_time` 用于"我的方案列表"查询（按创建时间倒序）；
- `idx_emp_id_name` 用于方案名称重名校验（emp_id + name 组合）。

---

### 2.2 sql_probe_history -- SQL 探查历史

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 历史 ID（UUID 主键） |
| emp_id | varchar(32) | NOT NULL | - | 执行人工号 |
| sql_text | text | NOT NULL | - | SQL 语句（完整文本） |
| remark | varchar(500) | NULL | NULL | 备注（reason，执行原因） |
| row_count | int(11) | NULL | NULL | 影响行数（结果集行数） |
| execution_time_ms | int(11) | NULL | NULL | 执行耗时（毫秒） |
| status | varchar(20) | NULL | NULL | 状态：SUCCESS-成功, FAILED-失败, TIMEOUT-超时 |
| error_msg | text | NULL | NULL | 错误信息（仅失败时填充） |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |

```sql
CREATE TABLE IF NOT EXISTS `sql_probe_history` (
  `id` varchar(32) NOT NULL COMMENT '历史ID（UUID）',
  `emp_id` varchar(32) NOT NULL COMMENT '执行人工号',
  `sql_text` text NOT NULL COMMENT 'SQL语句',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注(reason)',
  `row_count` int(11) DEFAULT NULL COMMENT '影响行数',
  `execution_time_ms` int(11) DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `status` varchar(20) DEFAULT NULL COMMENT '状态：SUCCESS/FAILED/TIMEOUT',
  `error_msg` text COMMENT '错误信息',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_id` (`emp_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_emp_time` (`emp_id`, `created_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SQL探查历史';
```

**设计要点**：

- 表结构服务于两个目的：
  1. 技术运维人员复盘自己的 SQL 探查记录（"我昨天跑过的那条 SQL"）；
  2. 配合 `audit_log` 做安全审计追溯（事故复盘时查询谁跑过什么 SQL）；
- `status` 有 3 种取值：SUCCESS（成功）/ FAILED（语法错误或白名单拒绝）/ TIMEOUT（超过 30 秒超时）；
- `error_msg` 仅在 `status != SUCCESS` 时填充，成功记录保持 NULL；
- 执行开始时 INSERT 一条 `status=RUNNING` 的占位记录（获取 id），完成时 UPDATE 最终状态，以便失联场景也能查到；
- `remark` 字段对应接口层的 `reason`，V1 强制要求非空（由应用层保证）；
- `idx_emp_time` 组合索引用于"我的探查记录"分页查询；
- 数据保留 3 个月，到期由 `sys_job_conf` 调度清理任务删除。

---

### 2.3 rpt_snapshot_task -- 快照任务配置（V1 预留）

| 字段名 | 类型 | NULL | 默认值 | 注释 |
|:---|:---|:---:|:---|:---|
| id | varchar(32) | NOT NULL | - | 任务 ID（UUID 主键） |
| task_name | varchar(200) | NOT NULL | - | 任务名称 |
| snapshot_type | varchar(50) | NOT NULL | - | 快照类型（DAILY/MONTHLY 等，V2 扩展） |
| cron_expr | varchar(100) | NOT NULL | - | Cron 表达式 |
| status | varchar(20) | NULL | 'ACTIVE' | 状态：ACTIVE-启用, DISABLED-禁用 |
| last_run_time | datetime | NULL | NULL | 最近执行时间 |
| next_run_time | datetime | NULL | NULL | 下次执行时间 |
| created_time | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_time | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

```sql
CREATE TABLE IF NOT EXISTS `rpt_snapshot_task` (
  `id` varchar(32) NOT NULL COMMENT '任务ID',
  `task_name` varchar(200) NOT NULL COMMENT '任务名称',
  `snapshot_type` varchar(50) NOT NULL COMMENT '快照类型',
  `cron_expr` varchar(100) NOT NULL COMMENT 'Cron表达式',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态',
  `last_run_time` datetime DEFAULT NULL COMMENT '最近执行时间',
  `next_run_time` datetime DEFAULT NULL COMMENT '下次执行时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_next_run_time` (`next_run_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='快照任务配置（V1预留）';
```

**V1 状态说明**：

- V1 **仅建表**，不写入任何数据，不启动任务；
- V1 所有报表数据通过 `MetricApi` 实时查询 `performance-engine-center`；
- V2 如需引入"报表本地快照加速"（如预计算日汇总减少实时查询压力），再启用该表及对应的 `SnapshotTaskService`；
- 本表不参与 V1 的审计和监控告警。

---

## 3. 索引说明

### 3.1 rpt_saved_query

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---:|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| idx_emp_id_time | (emp_id, created_time) | 普通 | "我的方案列表"分页查询（倒序） |
| idx_emp_id_name | (emp_id, name) | 普通 | 重名校验（同一用户下方案名唯一性应用层保证） |

**查询场景**：

- `SELECT * FROM rpt_saved_query WHERE emp_id = ? ORDER BY created_time DESC LIMIT 10;` 走 `idx_emp_id_time`
- `SELECT COUNT(*) FROM rpt_saved_query WHERE emp_id = ? AND name = ?;` 走 `idx_emp_id_name`

### 3.2 sql_probe_history

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---:|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| idx_emp_id | (emp_id) | 普通 | 按执行人筛选 |
| idx_created_time | (created_time) | 普通 | 按时间范围清理（归档任务） |
| idx_emp_time | (emp_id, created_time) | 普通 | "我的探查记录"分页 |
| idx_status | (status) | 普通 | 按状态统计（成功率、失败率监控） |

**查询场景**：

- `SELECT * FROM sql_probe_history WHERE emp_id = ? ORDER BY created_time DESC LIMIT 20;` 走 `idx_emp_time`
- `DELETE FROM sql_probe_history WHERE created_time < ?;` 走 `idx_created_time`
- `SELECT status, COUNT(*) FROM sql_probe_history WHERE created_time >= ? GROUP BY status;` 走 `idx_status`

### 3.3 rpt_snapshot_task

| 索引名 | 字段 | 类型 | 用途 |
|:---|:---|:---:|:---|
| PRIMARY | id | 主键 | 唯一标识 |
| idx_next_run_time | (next_run_time) | 普通 | 调度器扫描待执行任务（V2） |
| idx_status | (status) | 普通 | 按状态筛选 |

---

## 4. 逻辑外键

本模块不使用物理外键约束（避免跨库约束和性能影响），仅维护逻辑外键关系：

| 表 | 字段 | 关联表 | 关联字段 | 说明 | 孤儿处理 |
|:---|:---|:---|:---|:---|:---|
| rpt_saved_query | emp_id | PT_USER | user_id | 方案所属用户 | 用户注销时不级联，方案由用户自行删除 |
| sql_probe_history | emp_id | PT_USER | user_id | SQL 执行人 | 用户注销时不级联（审计证据保留） |

**注意事项**：

- `rpt_saved_query` 不依赖 `performance-engine-center` 的 `metric_def` 表，`metric_codes` 字段存储的是指标编码快照；
- 若指标元数据发生变更（如 `metric_code` 重命名），查询时通过 `MetricApi.getMetricDef` 返回 `Optional.empty()`，应用层容错处理；
- `emp_id` 在报表模块内不做跨表 JOIN，所有用户信息通过 `auth-permission-center` 的 `CurrentUserApi` 或 `UserQueryApi` 获取。

---

## 5. JSON 字段说明

### 5.1 rpt_saved_query.subject_ids

- **类型**：JSON 数组（字符串存储于 text 字段，应用层用 Jackson 序列化/反序列化）
- **示例（EMP 维度）**：`["E10001","E10002","E10003"]`
- **示例（ORG 维度）**：`["ORG_001","ORG_002"]`
- **示例（CUST 维度）**：`["C20260001","C20260002","C20260003"]`
- **约束**：
  - 数组长度 ≥ 1 且 ≤ 100（由 `report.dynamic-query.max.subjects` 控制）；
  - 元素为非空字符串，长度 ≤ 32；
  - 与 `dim` 字段匹配（dim=EMP 时只能存员工工号，以此类推，应用层校验）；
- **查询方式**：不在 SQL 中使用 `JSON_CONTAINS`，读取后在 Java 层反序列化。

### 5.2 rpt_saved_query.metric_codes

- **类型**：JSON 数组（字符串存储于 text 字段）
- **示例**：`["DEP_BAL_EMP","LOAN_BAL_EMP","INT_INCOME_EMP"]`
- **约束**：
  - 数组长度 ≥ 1 且 ≤ 20（由 `report.dynamic-query.max.metrics` 控制）；
  - 元素为非空字符串，需符合 `metric_def.metric_code` 规范（大写 + 下划线）；
  - 不校验指标是否存在（允许指标被下线后方案仍可打开，UI 友好提示）；
- **查询方式**：读取后在 Java 层反序列化为 `List<String>`，再批量调用 `MetricApi.getMetricDef`。

---

## 6. 归档策略

### 6.1 sql_probe_history 归档

| 维度 | 策略 |
|:---|:---|
| 保留时长 | **3 个月**（约 90 天） |
| 归档方式 | 物理删除 |
| 执行频率 | 每日 02:30（避开业务高峰） |
| 执行机制 | `system-governance-center` 的 `sys_job_conf` 调度 `SqlProbeHistoryCleanJob` |
| SQL 示例 | `DELETE FROM sql_probe_history WHERE created_time < DATE_SUB(NOW(), INTERVAL 3 MONTH) LIMIT 10000;` |
| 分批控制 | 每次最多删除 10000 行，循环执行直到无待删数据，避免长事务 |
| 备份要求 | 删除前**不需要**单独备份（已有 `audit_log` 保留原始审计，`sql_probe_history` 仅为便捷查询视图） |

**建议**：如数据量大（月增 > 500 万行），V2 可考虑按月分区：

```sql
-- V2 建议方案（V1 不启用）
ALTER TABLE sql_probe_history PARTITION BY RANGE (TO_DAYS(created_time)) (
  PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
  PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
  -- ...
);
```

### 6.2 rpt_saved_query 归档

| 维度 | 策略 |
|:---|:---|
| 每用户上限 | **10 条** |
| 超限处理 | 保存时检查数量，超出则**删除创建时间最旧的一条**（同事务） |
| 归档方式 | 应用层逻辑（非定时任务） |
| 用户注销处理 | 本模块不主动清理，由 `auth-permission-center` 注销流程调用 `SavedQueryApi.deleteByEmpId` |

**实现代码片段（Service 层伪代码）**：

```java
public SavedQueryRespDTO save(SavedQuerySaveCmd cmd) {
    String empId = CurrentUserContext.get().getEmpId();
    int count = savedQueryMapper.countByEmpId(empId);
    if (count >= MAX_PER_USER) {
        // 删除最旧的一条（创建时间升序第一条）
        savedQueryMapper.deleteOldestByEmpId(empId);
    }
    // INSERT 新方案
    ...
}
```

### 6.3 rpt_snapshot_task 归档

V1 不写入数据，不存在归档需求。

---

## 7. V1 不维护汇总快照表的说明

报表分析中心 V1 的核心设计原则是 **"零业务数据持久化"**：

| 数据类型 | V1 存储位置 | 访问方式 |
|:---|:---|:---|
| 员工指标值 | `performance-engine-center.emp_index_result` | `MetricApi.getEmpMetricValues` |
| 机构指标值 | `performance-engine-center.org_index_result` | `MetricApi.getOrgMetricValues` |
| 客户指标值 | `performance-engine-center.cust_index_result` | `MetricApi.getCustMetricValues` |
| KPI 结果 | `performance-engine-center.kpi_result` | `KpiApi.getCurrentKpiTotal` |
| 客户信息 | `customer-marketing-center.cust_master` | `CustomerQueryApi.getCustomer` |
| 指标元数据 | `performance-engine-center.metric_def` | `MetricApi.listMetrics` |
| 数据版本控制 | `performance-engine-center.sys_control` | 由 performance 模块自行管理 |

**不维护汇总快照表的原因**：

1. **数据一致性优先**：报表模块不落地业务数据，避免"报表数据与源系统不一致"的经典坑；
2. **架构简单性**：没有 ETL 任务、没有 Merge 逻辑、没有幂等设计、没有补数流程；
3. **职责清晰**：`performance-engine-center` 是唯一的"指标计算与存储"权威；
4. **V1 规模适配**：V1 日活跃用户 < 200，实时查询性能可接受（< 500ms），无需预计算；
5. **缓存已足够**：对热点查询（如分行行长仪表盘），通过 Redis 缓存 TTL 5 分钟即可满足性能要求。

**V2 演进方向**：

- 如果 V2 日活 > 1000 或仪表盘并发 > 500 QPS，再考虑引入本地快照表；
- 引入时以 `rpt_snapshot_task` 为调度入口，`performance.kpi-calc.completed.v1` 事件为触发信号；
- V2 快照表命名规范：`rpt_snapshot_<dim>_<freq>`，如 `rpt_snapshot_emp_daily`、`rpt_snapshot_org_monthly`。

---

## 8. 数据库访问约束

### 8.1 读写权限

| 操作 | 允许的表 | 说明 |
|:---|:---|:---|
| 读（SELECT） | `rpt_*` + `sql_probe_history` | 仅自有表 |
| 写（INSERT/UPDATE/DELETE） | `rpt_saved_query`、`sql_probe_history` | 仅自有表 |
| 读（通过 Api） | 任意其他模块 | 必须通过 `*Api` 接口 |
| 写（任何方式） | 其他模块的表 | **严格禁止** |

### 8.2 禁止的操作

```java
// ❌ 禁止：直接 JOIN 其他模块的业务私有表
@Select("SELECT * FROM rpt_saved_query q "
      + "JOIN cust_lead l ON l.owner_emp_id = q.emp_id "
      + "JOIN kpi_result k ON k.emp_id = q.emp_id "
      + "WHERE q.id = #{id}")
SavedQueryDetail getDetail(String id);

// ✅ 正确：只读自有表，关联数据通过 Api 获取
// Step 1: 读自有表
SavedQueryEntity entity = savedQueryMapper.selectById(id);
// Step 2: 通过 Api 获取关联数据
List<String> metricCodes = JsonUtil.parseList(entity.getMetricCodes(), String.class);
List<MetricDefDTO> metrics = metricApi.listMetricsByCode(metricCodes);
```

### 8.3 禁止跨模块访问的业务表清单

报表模块**绝对不能**直接 SQL 访问以下表（一律通过 Api）：

| 模块 | 禁止直连的表 | 必须使用的 Api |
|:---|:---|:---|
| customer-marketing-center | `cust_master`, `cust_lead`, `touch_record`, `cust_tag` | `CustomerQueryApi`, `LeadQueryApi` |
| performance-engine-center | `metric_def`, `emp_index_result`, `org_index_result`, `cust_index_result`, `kpi_result`, `sys_control` | `MetricApi`, `KpiApi` |
| auth-permission-center | `PT_USER`, `PT_ROLE`, `PT_USER_ROLE`, `PT_RESOURCE` | `CurrentUserApi`, `UserQueryApi`, `OrgApi` |
| system-governance-center | `audit_log`, `sys_dict`, `sys_config_kv`, `file_object` | `AuditApi`, `DictApi`, `ConfigApi`, `FileApi` |
| business-application-center | `loan_apply`, `card_apply`, `account_apply`, `biz_process_map` | （V2 预留）`ApplicationQueryApi` |
| workflow-center | `ACT_RU_*`, `ACT_HI_*`, `task_ext` | `WorkflowQueryApi`（V2 需要时） |

### 8.4 SQL 探查白名单的特殊例外

`POST /api/reports/sql-probe/execute` 允许管理员在**只读会话**中查询任意白名单内的表，但：

- 仅限 `R_ADMIN` / `R_BACK_TECH` 角色；
- 仅允许 `SELECT` 语句（由 SQL 解析器白名单校验）；
- 必须在 `sql.probe.schema.whitelist` 配置的表白名单内；
- 不走 MyBatis，走独立的只读数据源（`dataSourceReadOnly`）；
- 每次执行必须记录完整 SQL 和 `reason` 到 `sql_probe_history` 与 `audit_log`；
- 详见 `07-审计要求.md` 第 3 节。

---

## 9. 字符集与排序规则

- **字符集**：`utf8mb4`（支持 Emoji 和生僻字）
- **排序规则**：`utf8mb4_general_ci`（与其他模块保持一致）
- **存储引擎**：`InnoDB`（支持事务、行锁、外键）

---

## 10. 表容量预估（V1）

| 表名 | 日增行数 | 月增行数 | 年增行数 | 3 年总量 | 单行平均大小 | 3 年空间占用 |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| rpt_saved_query | ~50 | ~1500 | ~18000 | ~54000 | ~800 B | ~43 MB |
| sql_probe_history | ~200 | ~6000 | ~72000 | (保留 3 月) ~18000 | ~1.5 KB | ~27 MB |
| rpt_snapshot_task | 0 | 0 | 0 | 0 | - | 0 |

**总结**：本模块数据量极小（< 100 MB / 年），无分库分表压力，V1 标准 MySQL 部署足够。

---

## 11. 相关文件索引

- DDL 源文件：`docs/schema/ddl-report.sql`（待生成）
- 种子配置：`docs/schema/seed-v1.sql` 第 10 节（SQL 探查白名单等）
- 功能规格：`docs/modules/report-analytics-center/01-功能规格.md`
- 后端架构：`docs/modules/report-analytics-center/02-后端架构.md`
- 接口报文：`docs/modules/report-analytics-center/03-接口设计与报文.md`
- 对外契约：`docs/modules/report-analytics-center/04-对外API契约.md`
- 并发事务：`docs/modules/report-analytics-center/06-并发与事务策略.md`
- 审计要求：`docs/modules/report-analytics-center/07-审计要求.md`
- 初始数据：`docs/modules/report-analytics-center/08-初始化数据清单.md`
- 依赖契约：`docs/modules/report-analytics-center/09-依赖契约摘要.md`
