# 人员标签新增"是否启用评价"字段设计

- **日期**: 2026-05-29
- **模块**: performance-engine-center（eval 子域）+ xanzc_frontend
- **状态**: 设计已确认，待写实现计划
- **前置**: 在 `2026-05-29-eval-user-tags-list-redesign-design.md`（列表化）与 `2026-05-29-eval-user-tags-import-export-design.md`（导入/导出/模板）已交付的基础上增量扩展

## 1. 背景与目标

"人员标签"页（`xanzc_frontend/src/views/eval/UserTags.vue`）已实现：列表化、被评价/评价角色行内编辑、查询按钮、导入（含模板）、导出。

新增需求：

1. 每个人增加**「是否启用评价」**布尔位（按人，per-person）。
2. 该字段用**是/否**形态表达（前端是/否下拉，后端 1/0）。
3. **列表默认不展示"否"的数据**；同时提供「是否启用评价」查询条件（是/否/全部）。
4. **导入、导入模板、校验逻辑**同步加该列。
5. **导出**同步加该列。

## 2. 现状（事实基础）

- 列表分页**由 auth `UserApi.pageUsers(keyword,page,pageSize)`（全量 PT_USER 员工）驱动**，eval 侧数据（被评价/评价角色）作为 overlay 叠加（见 `EvalUserTagService.pageUserRoles` / `assembleRows`）。
- eval 子域当前**没有**任何"按人启用评价"的存储；`EVAL_TAG.status` 是标签字典的启停，与"按人启用"无关。
- 覆盖式保存接口 `PUT /api/admin/eval/user-tags/{userId}/roles`，body `{beEvalTagId, evalTagIds}`，Service `EvalUserTagService.saveUserRoles(userId, beEvalTagId, evalTagIds)` 删-插覆盖。
- 导入服务 `EvalUserTagImportService`：解析 `EvalUserTagImportRow`（工号/被评价角色/评价角色）→ 全部行校验通过才逐行调 `saveUserRoles` 入库（原子）。
- 导出 `EvalUserRoleExportRow`：姓名/工号/部门/岗位/角色/被评价角色/评价角色。
- 工号为 String 类型（已治理），EVAL_USER_TAG.USER_ID 为 VARCHAR(50)。

## 3. 方案

### 3.1 数据层：新建 `EVAL_USER_SETTING` 表

eval 子域自有的"按人评价设置"表，**不碰 auth PT_USER**（遵守"perf 不改 auth schema"边界）。

```sql
CREATE TABLE EVAL_USER_SETTING (
    USER_ID       VARCHAR(50) NOT NULL COMMENT '工号，关联 PT_USER.USER_ID',
    EVAL_ENABLED  TINYINT     NOT NULL DEFAULT 0 COMMENT '是否启用评价：1=是 0=否',
    CREATED_TIME  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UPDATED_TIME  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    PRIMARY KEY (USER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员评价设置（是否启用评价）';
```

- **语义**：表中无该工号记录 = "否"（未启用）。`EVAL_ENABLED=1` 才视为"是"。
- DDL 落 `docs/superpowers/sql/2026-05-29-eval-user-setting.sql`（INFORMATION_SCHEMA 预检幂等），同步更新 `docs/schema/ddl-eval.sql` 基线。
- 项目已废弃 Flyway，DDL 由开发/DBA 手工在目标库（yiti/onepl/onepl_test_bootstrap）执行。

实体 `EvalUserSetting`（贫血）：`userId` / `evalEnabled` / `createdTime` / `updatedTime`。
Mapper `EvalUserSettingMapper`：
- `selectEnabledUserIds()` → `List<String>`：取 `EVAL_ENABLED=1` 的全部工号（用于"是"过滤的 eval 驱动）。
- `selectEnabledMap(List<String> userIds)` → 用于 overlay：批量查给定工号的启用位（`Map<String,Integer>`，缺失视为 0）。
- `upsert(String userId, int evalEnabled)`：`INSERT ... ON DUPLICATE KEY UPDATE eval_enabled=#{evalEnabled}, updated_time=NOW()`。

### 3.2 列表 / 过滤（核心）

接口扩展 `GET /api/admin/eval/user-tags/page` 增加查询参数 `evalEnabled`（可空），取值约定：

| 参数值 | 含义 | 默认 |
|---|---|---|
| `1` | 只看"启用=是" | **缺省即按 1 处理** |
| `0` | 只看"否"（含无记录） | |
| 空串 / `all` | 全部 | |

> 默认（前端不传 / 传 1）= 只列启用人员。前端下拉默认值=「是」。

三条取数路径（`EvalUserTagService.pageUserRoles` 按 `evalEnabled` 分派）：

1. **是（`evalEnabled=1`，默认）— eval 侧驱动**
   - `EvalUserSettingMapper.selectEnabledUserIds()` 取全部启用工号（**上限 cap=5000**，超出 `log.warn` 截断）。
   - `UserApi.getUserByEmpIds(enabledIds)` 批量解析用户基本信息（已有方法）。
   - 关键词在内存按 姓名/工号 contains 过滤。
   - 内存分页（page/pageSize）后，对当前页调用 `assembleRows`（部门/岗位/角色/标签）。
   - total = 关键词过滤后的总数（准确）。
   - 理由：启用集合是被评价的小众子集，量可控；keyword 跨 auth/eval 两模块无法下推到单条 SQL，内存过滤是最简且正确的选择。

2. **全部（`evalEnabled` 空/`all`）— PT_USER 驱动（保持现状）**
   - 沿用 `userApi.pageUsers(keyword,page,pageSize)` 分页 + `assembleRows`。
   - 每行 overlay `evalEnabled` 标志位（`selectEnabledMap` 批量查本页工号）。

3. **否（`evalEnabled=0`）— PT_USER 驱动 + 近似分页**
   - 同"全部"路径取本页，overlay 后**内存剔除**本页中已启用者。
   - total 以 PT_USER 总数近似返回（轻微高估）。**已确认采用近似分页**（"否"是浏览/找人启用用途，非精确报表；enabled 为极小子集，高估可忽略）。
   - 文档与代码注释标注该近似性。

`assembleRows` 装配的每行 `EvalUserRoleRowDTO` **新增字段 `evalEnabled: Integer`**（0/1）：
- 路径 1：恒为 1（来源即启用集合）。
- 路径 2/3：来自 `selectEnabledMap`，缺失=0。

### 3.3 保存：复用 `saveRoles`（已确认）

扩展 `PUT /api/admin/eval/user-tags/{userId}/roles`：

- `SaveRolesReq` body 增字段 `evalEnabled: Integer`（0/1；null 兜底为 0）。
- `EvalUserTagService.saveUserRoles(userId, beEvalTagId, evalTagIds, evalEnabled)` **同一 `@Transactional`** 内：覆盖角色（删-插，现状）+ `EvalUserSettingMapper.upsert(userId, evalEnabled)`。
- enabled 与角色**正交**：现有"被评价/评价角色不能同时为空"规则不变。
- 编辑弹窗增「是否启用评价」**是/否单选下拉**（el-select，1=是 0=否），与角色一起「保存」。

> 不新增 toggle 端点；沿用 EVAL/WRITE，无新 PT_RESOURCE 登记。

### 3.4 导入 / 模板 / 校验

- `EvalUserTagImportRow` 增列 **「是否启用评价」**（`@ExcelProperty("是否启用评价")` String，填 `是`/`否`）。
- 模板样例行（`importTemplate`）补该列示例值 `是`。
- 校验（在 `EvalUserTagImportService.importRows` 逐行）：
  - 该列**非空**，且**必须是 `是` 或 `否`**（trim 后比较），否则该行报错 `是否启用评价只能填"是"或"否"`。
  - 解析：`是`→1，`否`→0。
  - 仍维持整体 **全部校验通过才入库**（任一行错→一条不写）。
- 入库：`ParsedRow` 增 `evalEnabled`，调 `saveUserRoles(empId, beEvalTagId, evalTagIds, evalEnabled)` 一并写。
- "被评价/评价角色不能同时为空"规则**保持不变**（与 enabled 正交）。

### 3.5 导出

- `EvalUserRoleExportRow` 增列 **「是否启用评价」**（String，`是`/`否`）。
- `EvalUserTagController.export` 装配时 `evalEnabled==1 ? "是" : "否"`。
- 导出取数与列表一致：`export` 增 `evalEnabled` 参数，透传到 `listForExport(keyword, evalEnabled, cap)`，复用 3.2 的三路径逻辑（导出全量翻页累积版本）。

### 3.6 前端

`src/views/eval/UserTags.vue`：
- 工具栏关键词输入框旁增「是否启用评价」下拉：`是`(默认,值1) / `否`(值0) / `全部`(值''或'all')；改变即 `doSearch`。
- 列表新增列「是否启用评价」：`evalEnabled===1 ? el-tag success "是" : "否"`。
- 编辑弹窗增「是否启用评价」是/否下拉，`form.evalEnabled`（默认取行 `row.evalEnabled ?? 0`）。
- `saveUserRoles` 调用增传 `evalEnabled`。
- 导出 `exportUserRoles(keyword, evalEnabled)` 透传过滤态。

`src/api/eval.js`：
- `pageUserRoles(params)` 增 `evalEnabled` 参数透传。
- `saveUserRoles(userId, beEvalTagId, evalTagIds, evalEnabled)` 增参。
- `exportUserRoles(keyword, evalEnabled)` 增参。

## 4. 测试（TDD：先红后绿后重构）

后端（surefire `*Test` / failsafe `*IT`）：
- `EvalUserSettingMapper` IT：upsert（首插=1、再 upsert 翻 0）、selectEnabledUserIds、selectEnabledMap。
- `EvalUserTagService.saveUserRoles` 单测：传 evalEnabled=1 → setting 表 upsert 1；=0 → 0；角色覆盖与 enabled 同事务。
- `pageUserRoles` 三路径单测（mock UserApi / AddressBookApi / mapper）：
  - evalEnabled=1：只返回启用集合内的人，keyword 内存过滤，total 准确。
  - evalEnabled 空：全部员工 + overlay enabled。
  - evalEnabled=0：剔除已启用者（近似 total）。
- `EvalUserTagImportServiceTest`：新列校验（空/非是否值报错；是→1/否→0；全部通过才入库）。
- `EvalUserTagExportTest`：导出含「是否启用评价」列且值正确。

前端：沿用现有 eval 页面实践，手动验证为主。

## 5. 权限登记

**无新增端点**（仅扩展 `page`/`saveRoles`/`import`/`export` 的入参与列）。沿用既有 EVAL/LIST·WRITE·IMPORT·EXPORT 资源与授权，无需新增 PT_RESOURCE 行。

## 6. 不做（YAGNI）

- 不做列表行内"快速 Switch 即点即切"（如需另开 toggle 端点，本期不做）。
- 不做"否"过滤的完全精确分页（采用近似分页）。
- 不改标签管理页（`Tags.vue`）/评价规则/评价任务。
- 不给 auth 加"按工号集合分页"能力。

## 7. 影响面与风险

- **新表上线**：`EVAL_USER_SETTING` 须在 yiti（开发）/ onepl（生产）/ onepl_test_bootstrap（测试）三库手工执行 DDL，否则 page/save/import 报"表不存在"。
- **默认列表语义变化**：上线后"人员标签"页默认只显示"启用=是"的人。历史数据全部无 setting 记录=否，**默认页将为空**，需通过「全部」过滤或按工号搜索找到人并启用。该行为变化需向业务方说明。
- **"否"近似分页**：total 高估，仅影响"否"视图的分页计数，不影响数据正确性。
- **导入破坏性**：模板新增必填列「是否启用评价」，旧模板（无该列）导入将因该列空而**整批报错**；需同步发布新模板给业务方。
- **跨模块**：仍仅 performance → auth(`UserApi`) / portal(`AddressBookApi`)，无新增依赖。
