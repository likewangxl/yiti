# 报表分析中心 — 数据模型

> 模块：report-analytics-center
> 说明：本文用于说明当前使用的表和关系，不提供可执行 DDL。字段类型、索引和约束以目标库实际 schema 及实体/Mapper 为准。

## 1. 模块自有表

### 查询、探查和导出

| 表 | 用途 | 关键关系或状态 |
|---|---|---|
| RPT_SAVED_QUERY | 用户动态查询方案 | 以 emp_id 隔离本人方案，version 用于更新并发控制 |
| SQL_PROBE_HISTORY | SQL 探查执行历史 | 记录本人 SQL、remark、状态、行数和耗时 |
| SQL_PROBE_EXPORT_TASK | SQL 探查导出任务 | 记录本人任务、状态、文件名、文件引用和错误信息 |
| RPT_EXPORT_TASK | 通用报表导出任务 | 记录 export_type、参数、操作人、状态、文件引用和结果信息 |

### 自由报表

| 表 | 用途 | 关键关系或状态 |
|---|---|---|
| RPT_FREE_REPORT_BATCH | Excel 导入批次、报表名称、操作人和批次状态 | 同一操作人和报表名称的当前批次由服务事务替换 |
| RPT_FREE_REPORT_ROW | 批次行数据和动态列值 | 通过 batch_id 关联批次，查询时按 REPORT 数据范围过滤 |

### 大屏配置

| 表 | 用途 | 关键关系或状态 |
|---|---|---|
| RPT_SCREEN | 屏元数据、草稿/发布画布和版本 | 软删除、画布版本 CAS、发布状态和不可变发布包 |
| RPT_SCREEN_BLOCK | 草稿画布区块 | 按 screen_id 关联，发布包保存可信绑定快照 |
| RPT_SCREEN_DATASOURCE | 大屏数据源配置 | 被区块引用时不可删除；保存和运行均做 SQL/范围校验 |
| RPT_SCREEN_ACCESS_ROLE | 屏查看角色白名单 | 与屏和角色编码关联，覆盖保存受权限及审计保护 |
| RPT_SCREEN_MAP_POINT | 地图点位 | 地图运行时按点位和屏范围读取 |
| RPT_SCREEN_PUBLISH_LOG | 发布归档和快照 | 保存发布渲染包、版本、操作人和审计关联 |

报表模块不把缓存、对象存储或上游业务状态复制为本地事实表。当前没有 REST 或 Service 消费的快照表不属于本模型。

## 2. 只读上游和外部数据

### 业务与审批查询

- AMAS_APPR_RECORD：AMAS 审批流程记录。
- AMAS_PERF_ADJUST_APPROVAL：业绩调整审批主记录。
- AMAS_PERFORMANCE_ALLOCATION：AMAS 业绩分配明细。
- AMAS_PRICE_APPROVAL：价格审批记录。
- amas_dt_import_sup、amas_dt_import_details：数据导入批次和明细。
- sys_notice：公告和附件标识。
- PERF_ALLOC_ADJUST_APPLY、PERF_ALLOC_ADJUST_ITEM：分配调整申请及明细；当前为报表历史查询的直接读取例外，报表不写入。

### 数据湖查询实体

当前源码映射的只读数据湖表包括：

- DATALAKE_XAN_PDL_C03_B_CORP_DEPOSIT_ACCT
- DATALAKE_XAN_CRM_C06_CORP_ASSET_LIAB_ALLOT
- DATALAKE_XAN_PDL_C03_B_CORP_LOAN_ACCT
- DATALAKE_XAN_C03_B_INDIV_DEPOSIT_ACCT
- DATALAKE_XAN_CRM_C06_INDIV_ALLOT_RELA

数据湖表仅供已有查询实现使用；没有 Controller 的实体不构成 REST 接口。

### 其他跨模块数据

指标、KPI、客户、触达、组织、用户、数据范围、字典和文件内容由相应公开 API 或 FileApi 提供。报表模块不得通过复制表结构或直接写入替代这些契约。

## 3. 关系和一致性要求

1. 自有表的写入由对应 Service 事务完成；Controller 不直接操作 Mapper。
2. RPT_SAVED_QUERY 更新必须带本人条件和版本条件。
3. RPT_SCREEN 的发布包与 RPT_SCREEN_PUBLISH_LOG 快照必须能独立重建运行时绑定；运行时不得使用当前可变草稿行替代发布快照。
4. RPT_SCREEN_DATASOURCE 删除前检查草稿和发布包引用，无法确认引用关系时拒绝删除。
5. 自由报表替换批次时，批次和行数据一致处理；共享 FileApi 对象的生命周期不由批次删除动作擅自决定。
6. SQL 探查和导出任务保留失败、超时或取消等可查询状态，文件引用写入成功后才允许下载。
