# 人员标签页改造：查询按钮 + Excel 导入 + 模板下载 + 导出设计

- **日期**: 2026-05-29
- **模块**: performance-engine-center（eval 子域）+ auth-permission-center（UserApi 补充）+ xanzc_frontend
- **状态**: 设计已确认，待写实现计划
- **前置**: 承接 `2026-05-29-eval-user-tags-list-redesign-design.md`（列表化 + 行内编辑已交付，含 `pageUserRoles` / `saveUserRoles`）

## 1. 背景与目标

人员标签页（`xanzc_frontend/src/views/eval/UserTags.vue`）已完成列表化改造（每行展示 部门/岗位/角色 + 被评价人角色单选 + 评价人角色多选 + 行内编辑）。

本次新增 4 项能力：

1. **查询按钮**：工具栏由"输入即搜"改为显式 `查询` + `重置` 按钮（保留回车触发）。
2. **导入**：上传 Excel 批量设置人员的被评价/评价角色。
3. **导入模板下载**：提供可下载的 `.xlsx` 模板。
4. **导出（下载）**：将当前查询条件匹配到的人员列表生成 `.xlsx`，便于离线核对。

## 2. 现状（事实基础）

- `EVAL_TAG(TAG_ID, TAG_NAME, TAG_TYPE, STATUS, ...)`，`TAG_TYPE` 1=被评价人 / 2=评价人，`STATUS` 1=启用。
- `EVAL_USER_TAG(ID, USER_ID, TAG_ID)`，`USER_ID` 为 BIGINT，关联 `PT_USER.USER_ID`（工号，varchar）；String/Long 边界已用 `toLongOrNull` 处理（非数字工号无 eval 标签，不报错）。
- 已有 service 能力（复用）：
  - `EvalUserTagService.saveUserRoles(Long userId, Long beEvalTagId, List<Long> evalTagIds)` —— `@Transactional` 覆盖式保存（删全部 + 批量插入），含被评价/评价类型校验（PERF-40059）与标签存在校验（PERF-40058）。
  - `EvalUserTagService.pageUserRoles(String keyword, int page, int pageSize)` —— 分页聚合（user 分页 + 通讯录 + 角色 + eval 标签），返回 `EvalUserRoleRowDTO`。
- 已有跨模块能力：
  - auth `UserApi.pageUsers(keyword,page,pageSize)`、`getRolesByUserIds(List<String>)`（本次再补 `getUsersByIds`）。
  - portal `AddressBookApi.getEmployees(List<String>)` → `EmployeeDTO{orgName, position}`（批量上限 200）。
- 既有同类基建（对齐复用）：
  - `easyexcel` 已是 performance-engine-center 依赖；写出实例见 `KpiExportStrategy`/`AllocExportStrategy`。
  - 同步流式下载实例见 `CustomerExportController`（`HttpServletResponse` + `Content-Disposition`）。
  - 前端下载模板/导出实例见 `perf/Import.vue` 的 `downloadTpl` 与 `report` 导出。
- **不采用** customer `LeadImportService` 的异步批次（preview+execute+MinIO+审批流）模式——过重；本场景人员量小，采用同步原子导入。

## 3. 方案

### 3.0 工号字符型治理（前置基础，2026-05-29 追加）

**事实**：`PT_USER.USER_ID`（工号）是 `varchar(50)`，但 eval 模块把工号存成 `BIGINT`，共 4 处：
`EVAL_USER_TAG.USER_ID`、`EVAL_TASK_TARGET.BE_EVAL_USER_ID`、`EVAL_SCORE.EVAL_USER_ID`、`EVAL_TASK.CREATE_BY`。
现网样例工号恰为数字字符串（如 `2280`/`3431`），所以"数字假设"未暴露问题；但工号**本质是字符型**，且不能假设永远是数字。已交付的列表化/编辑用 `toLongOrNull` 兜底（非数字工号丢标签），是治标。

**决策（用户确认）**：把 eval 模块全部 4 处 USER_ID 列改为 `VARCHAR(50)`，代码贯通 `Long → String`，删除 `toLongOrNull`/`Long.parseLong` 等转换。范围覆盖人员标签 + 评价任务/打分子系统。

**改动清单（17 文件）**：
- DB：`ALTER TABLE` 4 列 `BIGINT → VARCHAR(50)`（现网 EVAL_USER_TAG 仅 3 行 USER_ID=3431，数字字符串平滑转换）；同步更新 `docs/schema/ddl-eval.sql`。
- 实体（4）：`EvalUserTag.userId` / `EvalTaskTarget.beEvalUserId` / `EvalScore.evalUserId` / `EvalTask.createBy` → `String`。
- DTO（1）：`EvalUserTagRow.userId` → `String`（`EvalUserRoleRowDTO.userId` 本就是 String）。
- Mapper（2）：`EvalUserTagMapper`（5 方法 userId 参数 + `selectUserIdsByTagId` 返回 `List<String>` + `selectUserTagsByUserIds` 参数 `List<String>`；xml `selectUserIdsByTagId` resultType 改 `java.lang.String`）、`EvalScoreMapper`（2 方法 evalUserId 参数）。
- Service（3）：`EvalUserTagService`（5 公开方法签名 + `tagMap` 改 `Map<String,...>` + 删 `toLongOrNull`/numericIds 链）、`EvalTaskService`（`createTask` 参数 + 循环变量）、`EvalScoreService`（5 方法 + 删 `String.valueOf` 包装）。
- Controller（3）：`EvalUserTagController`（`list`/`saveRoles`/`BindReq.userId`）、`EvalTaskController`（`CreateTaskReq.beEvalUserIds` + 删 `Long.parseLong`）、`EvalScoreController`（两处删 `Long.parseLong`）。
- 测试 fixture（3）：`EvalUserRoleServiceTest` / `EvalScoreServiceTest` / `EvalTaskServiceTest` 里 Long 字面量改 String。
- 不动：TAG_ID/TASK_ID/RULE_ID/GROUP_ID/SCORE_ID/TARGET_ID 等主键/外键仍 BIGINT；report 等其它模块无引用。

> 此治理必须先做（导入/导出建立在 `saveUserRoles(String userId, …)` 之上）。

### 3.1 数据层

人员标签关联表 `EVAL_USER_TAG` / 字典表 `EVAL_TAG` 结构不新增字段；仅按 §3.0 把 `USER_ID` 改为 `VARCHAR(50)`。

### 3.2 Excel 模板（3 列，填角色名称）

| 工号 | 被评价角色 | 评价角色 |
|------|-----------|---------|
| E001 | 支行行长 | 副行长,客户经理 |

- 表头固定 3 列：`工号` / `被评价角色` / `评价角色`。
- **评价角色**：同一人在单元格内用**逗号分隔**（中英文逗号均兼容），单元格内去重。
- 模板下载内容 = 表头 + 1 行示例数据。

### 3.3 导入校验规则（全部满足才入库，原子）

逐行解析后做整文件校验，**任一行失败则整体不导入**，返回全部错误明细：

1. **工号** 必须存在于 `PT_USER`（批量查 `UserApi.getUsersByIds`）；不存在 → 行错。
2. **被评价角色**：可空；若填写，必须匹配 `EVAL_TAG` 中 `STATUS=1` 且 `TAG_TYPE=1` 的名称；**只能一个**（填了逗号分隔的多个 → 行错）。
3. **评价角色**：可空；拆分后每个名称必须匹配 `STATUS=1` 且 `TAG_TYPE=2`。
4. **被评价、评价两列不可同时为空**（避免一行什么都没填却把此人角色清空）→ 行错。
5. **文件内工号不可重复**（同一工号出现多行 → 行错，语义二义）。
6. 名称匹配不到 / 类型不符 / 工号不存在 → 该行记录错误（行号 + 工号 + 原因）。

校验全部通过后，在一个 `@Transactional` 内逐行调用既有 `saveUserRoles(String empId, beEvalTagId, evalTagIds)`，**整体覆盖**该工号的现有角色（与页面"编辑"覆盖语义一致）。

> §3.0 治理后工号为字符型，导入**不再要求工号为数字**；工号有效性由"是否存在于 `PT_USER`"判定（见规则 1）。

> 名称→tagId 解析：一次性查 `EvalTagMapper` 取全部启用标签，按 `TAG_TYPE` 建两个 `name→tagId` Map（performance 模块内直连本模块 mapper，合规）。

### 3.4 后端接口（performance-engine-center, eval 子域）

`EvalUserTagController` 新增 3 个端点：

```
POST /api/admin/eval/user-tags/import          (multipart file)
  @BizAuth(bizType = EVAL, action = IMPORT)
  → ResponseWrapper<EvalUserTagImportResultDTO>
     { boolean success; int importedCount; List<RowError> errors; }
     RowError { int row; String empId; String message; }

GET  /api/admin/eval/user-tags/import-template
  @BizAuth(bizType = EVAL, action = IMPORT)
  → 流式 .xlsx（表头 + 示例行）

GET  /api/admin/eval/user-tags/export?keyword=
  @BizAuth(bizType = EVAL, action = EXPORT)
  → 流式 .xlsx（姓名/工号/部门/岗位/角色/被评价人角色/评价人角色）
```

新增 `EvalUserTagImportService`：
- `importExcel(MultipartFile)` → 解析 + 校验 + 原子入库，返回结果 DTO。
- 文件保护：仅 `.xlsx`；空文件报错；行数上限（如 5000）。
- 导出复用聚合逻辑：在 `EvalUserTagService` 抽取无分页的 `listForExport(String keyword, int cap)`（上限 10000），导出 service/控制器调用后用 EasyExcel 流式写出。

> 校验失败的判定：`success=false` 时 `importedCount=0` 且 `errors` 非空，HTTP 仍为 200（业务层结果），前端据 `success` 展示错误明细表。

### 3.5 auth-permission-center：无需改动

工号有效性校验**直接复用既有** `UserApi.getUserByEmpIds(List<String> empIds)`（返回 `List<UserDTO>`），导入服务据返回的 `empId` 集合判定存在性。**不新增 auth 方法、不改 auth 模块。**

### 3.6 前端（xanzc_frontend）

`UserTags.vue`：
- 工具栏：`查询`（按当前关键词查）、`重置`（清空关键词回到第一页）、`导入`、`下载`；保留输入框回车触发查询；移除/弱化 `@input` 即时搜索。
- **下载**：点击调用导出端点，按当前 `keyword` 以 blob 方式下载 `.xlsx`（沿用 perf `downloadTpl`/report 导出下载模式，带 session）。
- **导入弹窗**（`el-dialog`）：
  - `下载模板` 按钮 → 下载 import-template。
  - `el-upload` 单文件 `.xlsx`，手动上传。
  - 上传成功且 `success=true` → 提示"导入成功 N 条"，关闭弹窗并刷新列表。
  - `success=false` → 弹窗内 `el-table` 展示错误明细（行号 / 工号 / 原因），不刷新、不关闭，供用户改文件后重传。

`eval.js` 新增：
```js
importUserRoles(file)          // POST multipart /admin/eval/user-tags/import
downloadImportTemplate()       // GET (blob) /admin/eval/user-tags/import-template
exportUserRoles(keyword)       // GET (blob) /admin/eval/user-tags/export
```

### 3.7 权限登记

新增 SQL 种子（`docs/superpowers/sql/2026-05-29-eval-user-tags-import-export-seed.sql`）注册 3 条 `PT_RESOURCE`：
- `PERF_EVAL_23` `POST /api/admin/eval/user-tags/import` 导入人员角色
- `PERF_EVAL_24` `GET  /api/admin/eval/user-tags/import-template` 下载导入模板
- `PERF_EVAL_25` `GET  /api/admin/eval/user-tags/export` 导出人员角色

授权 R_ADMIN + R_BACK_TECH。实现时核对 DB 现有最大编号避免冲突（上一批用到 `PERF_EVAL_22`）。上线前需在生产/各环境手工执行，否则 3 个新端点 403。

## 4. 测试（TDD：先红后绿后重构）

后端（`*Test.java` surefire，纯 mock service）：
- `EvalUserTagImportService`：
  - 全部行有效 → 入库成功，`importedCount` 正确，逐行 `saveUserRoles` 被调用。
  - 任一行无效 → `success=false`、`importedCount=0`、`errors` 含该行、**不写库**（验证 saveUserRoles 未被调用 / 事务回滚）。
  - 工号不存在 → 行错。
  - 被评价角色名不存在 / 类型为评价人 → 行错。
  - 被评价角色填多个（逗号）→ 行错。
  - 评价角色名不存在 / 类型为被评价人 → 行错。
  - 被评价、评价两列皆空 → 行错。
  - 文件内工号重复 → 行错。
  - 评价角色单元格内去重。
  - 非数字工号（如 `E001`）只要存在于 PT_USER 即可正常导入（§3.0 治理后不再要求数字）。
- `EvalUserTagService.listForExport`：拼装正确、上限截断。
- §3.0 治理回归：`EvalUserRoleServiceTest` / `EvalScoreServiceTest` / `EvalTaskServiceTest` 改 String fixture 后全绿。

前端：沿用现有 eval 页面实践，手动验证为主。

## 5. 不做（YAGNI）

- 不做异步批次 / MinIO / 审批流（不复用 customer LeadImport 那套）。
- 不做导入历史批次列表、进度条、断点续传。
- 不改标签管理页（`Tags.vue`）/ `EVAL_RULE`。
- 不加 DB 唯一约束（被评价单选由 service 保证）。
- 不删旧 bind/unbind 接口。

## 6. 影响面与风险

- **工号字符型治理（§3.0）**：触及评价任务/打分子系统（已交付），需跑 eval 全量单测回归确保零行为变化。DB ALTER 在现网仅 3 行 EVAL_USER_TAG（数字字符串），转换无损；上线需对 onepl/yiti 各环境执行迁移 SQL。
- **跨模块调用**：performance → auth(`UserApi.getUserByEmpIds`) / portal(`AddressBookApi`)，依赖已存在，无循环依赖，**无需改 auth**。
- **性能**：导入同步原子，行数上限 5000；导出上限 10000，分批聚合（通讯录批量 200 上限需分片调用）。均满足离线操作预期。
- **权限**：3 个新端点必须先登记 `PT_RESOURCE` 并授权，否则 403（按"鉴权失败优先排查 PT_* 表数据"SOP）。
- **历史脏数据**：导入覆盖式写入会顺带纠正该工号历史多 tagType=1 脏数据。
