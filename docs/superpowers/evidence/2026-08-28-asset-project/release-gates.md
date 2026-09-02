# 资产立项运行切换发布门禁

> 日期：2026-08-28。本文是静态审查与待执行核验清单，不代表数据库、权限或真实业务流程已实施。
> 本轮不执行下列 SQL；目标库、账号和工单必须由 DBA/权限管理员在执行前确认。

## 1. 新接口资源覆盖

| HTTP | 运行路径 | 资源 ID |
|---|---|---|
| GET | `/api/marketing/asset-projects` | `C_ASSET_LIST` |
| GET | `/api/marketing/asset-projects/*` | `C_ASSET_READ` |
| POST | `/api/marketing/asset-projects` | `C_ASSET_CREATE` |
| PUT | `/api/marketing/asset-projects/*` | `C_ASSET_UPDATE` |
| POST | `/api/marketing/asset-projects/*/submit` | `C_ASSET_SUBMIT` |
| DELETE | `/api/marketing/asset-projects/*` | `C_ASSET_DELETE` |
| POST | `/api/marketing/asset-projects/*/cancel` | `C_ASSET_CANCEL` |
| GET | `/api/marketing/asset-projects/*/urgent-context` | `C_ASSET_URG_CTX` |
| POST | `/api/marketing/asset-projects/*/urgent-applies` | `C_ASSET_URG_NEW` |
| GET | `/api/marketing/asset-projects/*/urgent-applies` | `C_ASSET_URG_LIST` |

菜单资源为 `M_MKT_ASSET_PROJECT`，前端路由为 `/marketing/asset-projects`。旧 Loan 的导出和节点表单
接口没有新实现，因此不得迁移成虚假的新资源。

## 2. 可执行脚本静态边界

候选脚本：`docs/superpowers/sql/2026-08-28-marketing-asset-project-runtime-cutover.sql`。

- 只包含 `START TRANSACTION`、`INSERT`、`UPDATE`、`COMMIT`。
- 不包含 `DELETE`、DDL、独立 `SELECT/SHOW/DESCRIBE/EXPLAIN/CHECKSUM`、临时表、存储对象或
  `INFORMATION_SCHEMA`。
- `SELECT` 仅作为 `INSERT ... SELECT` 的组成部分。
- 旧 `M_BIZ_LOAN/B_LOAN_*` 资源通过 `STATUS=1` 停用；旧 `LOAN` 数据范围通过
  `RECORD_STATUS=1` 停用。历史 `PT_ROLE_RESOURCE` 关系保留作审计追溯，但停用资源不再参与匹配。
- 旧角色资源先映射到新资源；新资源兜底授予 `SYS_ADMIN`。其他角色授权是否沿用旧粒度仍需权限管理员批准。

## 3. DBA/权限管理员执行前只读盘点

以下命令必须由获准人员针对明确的隔离 `yiti_test` 执行并归档原始输出；不得塞回可执行 SQL 文件：

```sql
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, STATUS, SYS_CODE
FROM PT_RESOURCE
WHERE RESOURCE_ID IN (
  'M_BIZ_LOAN','B_LOAN_LIST','B_LOAN_READ','B_LOAN_CREATE','B_LOAN_UPDATE',
  'B_LOAN_SUBMIT','B_LOAN_DELETE','B_LOAN_CANCEL','B_LOAN_EXPORT','B_LOAN_FORM',
  'M_MKT_ASSET_PROJECT','C_ASSET_LIST','C_ASSET_READ','C_ASSET_CREATE','C_ASSET_UPDATE',
  'C_ASSET_SUBMIT','C_ASSET_DELETE','C_ASSET_CANCEL','C_ASSET_URG_CTX','C_ASSET_URG_NEW','C_ASSET_URG_LIST'
)
ORDER BY RESOURCE_ID;

SELECT ROLE_ID, RESOURCE_ID, SYS_CODE
FROM PT_ROLE_RESOURCE
WHERE RESOURCE_ID IN (
  'M_BIZ_LOAN','B_LOAN_LIST','B_LOAN_READ','B_LOAN_CREATE','B_LOAN_UPDATE',
  'B_LOAN_SUBMIT','B_LOAN_DELETE','B_LOAN_CANCEL','B_LOAN_EXPORT','B_LOAN_FORM'
)
ORDER BY ROLE_ID, RESOURCE_ID;

SELECT ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS
FROM PT_ROLE_BIZ_SCOPE
WHERE BIZ_TYPE IN ('LOAN', 'ASSET_PROJECT')
ORDER BY ROLE_ID, BIZ_TYPE;
```

还须确认 `M_GROUP_MARKETING` 存在且是正确父菜单，`PT_RESOURCE` 的 URL+Method+SYS_CODE 唯一约束不会
与人工建立的资源冲突，并确认目标角色集合、数据范围口径和回滚负责人。

## 4. 执行后只读验收

```sql
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, STATUS, PARENT_RESOURCE_ID
FROM PT_RESOURCE
WHERE RESOURCE_ID='M_MKT_ASSET_PROJECT' OR RESOURCE_ID LIKE 'C_ASSET_%'
ORDER BY RESOURCE_ID;

SELECT RESOURCE_ID, STATUS
FROM PT_RESOURCE
WHERE RESOURCE_ID='M_BIZ_LOAN' OR RESOURCE_ID LIKE 'B_LOAN_%'
ORDER BY RESOURCE_ID;

SELECT ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS
FROM PT_ROLE_BIZ_SCOPE
WHERE BIZ_TYPE IN ('LOAN', 'ASSET_PROJECT')
ORDER BY ROLE_ID, BIZ_TYPE;
```

期望：11 条新资源均为 `STATUS=0`；旧资源均为 `STATUS=1`；获准角色存在有效 `ASSET_PROJECT` 数据范围，
旧 `LOAN` 数据范围为失效。随后必须以非管理员和管理员账号分别验证菜单可见性、列表范围及写操作拒绝边界。

## 5. 仍未解除的发布门禁

1. DBA 尚未在隔离库实施并核实 `MARKETING_ASSET_PROJECT_APPLY`、
   `MARKETING_ASSET_PROJECT_URGENT_APPLY` 结构，业务 DDL 不在本仓库执行。
2. 权限管理员尚未批准旧角色到新资源和 `ASSET_PROJECT` 数据范围的迁移结果。
3. 当前页面证据使用浏览器 route mock，不是资产立项真实后端联调。
4. 触达页面真实请求停在未登录 401，任务详情、工作日志写入、取消和完成仍未联调。
5. 资产立项提交/加急启动流程后本地写入失败时，Flowable 与业务表的共享事务整体回滚尚未用集成测试
   做故障注入验证。
6. 需要真实账号覆盖草稿创建/修改/删除、提交、撤回、主流程完成、加急通过/驳回、附件替换及数据范围；
   非管理员至少覆盖主办人、本人有效认领、机构范围授权和无权限拒绝四种客户写权限分支。

## 6. 本轮隔离验证记录

- 真实 `yiti` 仅做只读盘点：资产立项和新触达运行表已存在且当前业务行数为 0；新资产立项权限资源尚未落库，旧 Loan 权限资源仍存在。本轮未写 `yiti`。
- 候选切换 SQL 在隔离 `yiti_test` 中把最终 `COMMIT` 替换为 `ROLLBACK` 后完整执行成功；回滚后新资源和 `ASSET_PROJECT` 数据范围均为 0，未留下数据。
- 同一事务内连续执行两遍脚本主体后再 `ROLLBACK` 成功，验证重复执行不会因角色资源重复映射触发主键冲突。角色资源迁移使用 `SELECT DISTINCT` 消除旧菜单和旧列表同时映射到新菜单产生的重复行。
- 上述结果只证明语法、约束和幂等性，不授权在 `yiti` 执行；生产/开发库实施仍需 DBA 和权限管理员确认目标角色、数据范围及回滚负责人。
