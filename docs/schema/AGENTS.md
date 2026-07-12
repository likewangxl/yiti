<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-03 | Updated: 2026-07-12 -->

# docs/schema/AGENTS.md

本文件为 `docs/schema/` 目录提供上下文说明。这里存放了项目的所有数据库 DDL、种子数据和 schema 可视化产物。

## SQL 文件清单

### DDL 建表脚本

| 文件 | 对应模块 | 核心表 |
|------|----------|--------|
| `ddl-auth.sql` | auth-permission-center | `PT_USER`, `PT_ROLE`, `PT_RESOURCE`, `PT_USER_ROLE`, `PT_ROLE_RESOURCE`, `PT_ROLE_BIZ_SCOPE`, `EXT_ORG_INFO`, `EXT_USER_ORG` |
| `ddl-governance.sql` | system-governance-center | `sys_dict`, `sys_config_kv`, `sys_calendar_day`, `audit_log`, `user_notification`, `file_object`, `biz_file_rel`, `sys_job_conf`, `sys_job_run_log` |
| `ddl-quartz.sql` | system-governance-center（V1.6 引入） | 11 张 `QRTZ_*` 表（`QRTZ_JOB_DETAILS`/`QRTZ_TRIGGERS`/`QRTZ_CRON_TRIGGERS`/`QRTZ_LOCKS` 等），由 `spring.quartz.jdbc.initialize-schema=never` 触发手动初始化 |
| `ddl-workflow.sql` | workflow-center | `biz_process_map`, `wf_node_candidate_conf`, `wf_node_form_conf`, `wf_timeout_rule` |
| `ddl-customer.sql` | customer-marketing-center | `cust_tag`, `cust_tag_rel`, `cust_lead` 等 (客户主数据、标签、线索、触达等) |
| `ddl-portal.sql` | portal-content-center | URL 导航、工作台快捷入口、通讯录、产品、文档等表 |
| `ddl-bizapp.sql` | business-application-center | `support_request`, `loan_apply` |
| `ddl-report.sql` | report-analytics-center | 动态查询保存计划等报表配置表 |
| `ddl-performance.sql` | performance-engine-center | 数据版本、指标定义、KPI 方案、目标方案、绩效分配及调整等大量表 |
| `ddl-eval.sql` | performance-engine-center / eval 子域（2026-05-27 引入） | `EVAL_TAG`, `EVAL_USER_TAG`, `EVAL_RULE`, `EVAL_RULE_GROUP` 等内部相互评价 + 奖励分配（REWARD）表；`05-表结构DDL.md` 尚未同步收录，以此文件为准 |
| `ddl-yiti-prod-golive.sql` | 全量（生产上线基线） | 从权威源 `yiti` 库导出的全表建库建表脚本（排除 `*_BAK_*` 备份表），由 `gen-yiti-prod-golive.sh` 生成 |

> **注意**：历史文档中提到的 `v1-additions.sql` 已不存在于当前目录，如仍在其他文档中被引用应视为过期记录。

### 种子数据脚本

| 文件 | 用途 |
|------|------|
| `seed-v1.sql` | 全模块初始种子数据 (字典、角色、资源、角色-资源绑定、角色-BizType-数据范围绑定)，供 DEV/TEST 环境使用；`seed-v1.sql.bak` 为其历史备份快照，不作为当前基线 |
| `workflow-seed-v1.sql` | 工作流中心种子数据 (`wf_node_candidate_conf`, `wf_timeout_rule`, `wf_node_form_conf` 初始化配置)；`workflow-seed-v1.sql.bak` 为历史备份快照 |
| `seed-yiti-prod-golive.sql` | 生产上线数据初始化（基础参数/RBAC组织/审批流程/指标数据），业务数据表不导出数据；与 `ddl-yiti-prod-golive.sql` 成对，由 `gen-yiti-prod-golive.sh` 生成 |

### 生成脚本与 schema 可视化产物

| 文件/目录 | 用途 |
|------|------|
| `gen-yiti-prod-golive.sh` | 从实际 `yiti` 库 mysqldump 导出生产上线 DDL + 种子基线的生成器（需 TCP 连接 `-h127.0.0.1`） |
| `_gen_schema_doc.py` | 从 `ddl-*.sql`（跳过 Quartz）摘取结构化元数据，生成 `yiti-schema.xlsx` + `yiti-er-diagram.md` + `yiti-er-diagram.dot` |
| `_render_er_image.py` | 依赖 `_gen_schema_doc.py` 的模块级常量，用 matplotlib+networkx 渲染 `yiti-er-*.png`/`.svg`（跨模块总览 + 每模块视图） |
| `yiti-schema.xlsx` / `yiti-er-diagram.md` / `yiti-er-diagram.dot` / `yiti-er-*.png` / `yiti-er-*.svg` | 上述两个脚本的生成产物，按模块切分的表结构与 ER 关系图 |
| `migrations/` | 历史增量迁移脚本归档（非 Flyway，纯手工执行记录），如 `2026-04-25-quartz-integration.sql`（`sys_job_conf`/`sys_job_run_log` 字段扩展，前置依赖 `ddl-quartz.sql`） |

> 项目根 `docs/exports/` 目录另有一套独立的 PlantUML ER 图产物链（`build_yiti_schema_doc.py` + `plantuml.jar` + `yiti_er_*.puml/.png/.svg`），与本目录的 `_gen_schema_doc.py`/`_render_er_image.py` 链路是两套并存的可视化产物，互不依赖，维护时不要混淆。

## 数据库约定

- **数据库**: MySQL 8.0
- **字符集**: utf8mb4
- **引擎**: InnoDB
- **Flowable 表**: `ACT_*` 系列表由 Flowable 引擎自动创建，不在 DDL 脚本中定义

## 开发指引

- 新增表时，需在 `docs/schema/` 下创建对应的 DDL 文件，并在模块文档 `05-表结构DDL.md` 中记录（eval 子域的 `ddl-eval.sql` 目前是例外，`05-表结构DDL.md` 尚未同步，引用时需说明）
- 种子数据变更需同步更新 `seed-v1.sql` 或对应模块的专用 seed 文件，`.bak` 文件仅作历史存档，不要在其上继续修改
- 跨模块查询不能直接 JOIN 其他模块的表，需通过 `*QueryApi` 接口间接查询
- `PT_ROLE_BIZ_SCOPE` 表定义了角色与 BizType 的数据范围映射，是鉴权系统的核心表
- 已有库的增量对齐/测试种子重建，走 `docs/superpowers/sql/` 下带日期前缀的脚本（详见 `../AGENTS.md`），不要在本目录直接改动基线 DDL/种子文件
