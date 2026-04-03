# docs/schema/ CLAUDE.md

本文件为 `docs/schema/` 目录提供上下文说明。这里存放了项目的所有数据库 DDL 和种子数据。

## SQL 文件清单

### DDL 建表脚本

| 文件 | 对应模块 | 核心表 |
|------|----------|--------|
| `ddl-auth.sql` | auth-permission-center | `PT_USER`, `PT_ROLE`, `PT_RESOURCE`, `PT_USER_ROLE`, `PT_ROLE_RESOURCE`, `PT_ROLE_BIZ_SCOPE`, `EXT_ORG_INFO`, `EXT_USER_ORG` |
| `ddl-governance.sql` | system-governance-center | `sys_dict`, `sys_config_kv`, `sys_calendar_day`, `audit_log`, `user_notification`, `file_object`, `biz_file_rel`, `sys_job_conf`, `sys_job_run_log` |
| `ddl-workflow.sql` | workflow-center | `biz_process_map`, `wf_node_candidate_conf`, `wf_node_form_conf`, `wf_timeout_rule` |
| `ddl-customer.sql` | customer-marketing-center | `cust_tag`, `cust_tag_rel`, `cust_lead` 等 (客户主数据、标签、线索、触达等) |
| `ddl-portal.sql` | portal-content-center | URL 导航、工作台快捷入口、通讯录、产品、文档等表 |
| `ddl-bizapp.sql` | business-application-center | `support_request`, `loan_apply` |
| `ddl-report.sql` | report-analytics-center | 动态查询保存计划等报表配置表 |
| `ddl-performance.sql` | performance-engine-center | 数据版本、指标定义、KPI 方案、目标方案、绩效分配及调整等大量表 |

### 种子数据脚本

| 文件 | 用途 |
|------|------|
| `seed-v1.sql` | 全模块初始种子数据 (字典、角色、资源、角色-资源绑定、角色-BizType-数据范围绑定)，供 DEV/TEST 环境使用 |
| `v1-additions.sql` | V1 版本的数据调整与增补 (2026-03-17 生成) |
| `workflow-seed-v1.sql` | 工作流中心种子数据 (`wf_node_candidate_conf`, `wf_timeout_rule`, `wf_node_form_conf` 初始化配置) |

## 数据库约定

- **数据库**: MySQL 8.0
- **字符集**: utf8mb4
- **引擎**: InnoDB
- **Flowable 表**: `ACT_*` 系列表由 Flowable 引擎自动创建，不在 DDL 脚本中定义

## 开发指引

- 新增表时，需在 `docs/schema/` 下创建对应的 DDL 文件，并在模块文档 `05-表结构DDL.md` 中记录
- 种子数据变更需同步更新 `seed-v1.sql` 或对应模块的专用 seed 文件
- 跨模块查询不能直接 JOIN 其他模块的表，需通过 `*QueryApi` 接口间接查询
- `PT_ROLE_BIZ_SCOPE` 表定义了角色与 BizType 的数据范围映射，是鉴权系统的核心表
