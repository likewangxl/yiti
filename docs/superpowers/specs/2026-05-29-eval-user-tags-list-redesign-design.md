# 人员标签页面改造设计（列表化 + 行内编辑）

- **日期**: 2026-05-29
- **模块**: performance-engine-center（eval 子域）+ auth-permission-center（UserApi 补充）+ xanzc_frontend
- **状态**: 设计已确认，待写实现计划

## 1. 背景与目标

现有"人员标签"页（`xanzc_frontend/src/views/eval/UserTags.vue`）是**主从模式**：左侧搜索一个员工，右侧给他绑/解绑标签，标签可多选任意类型。

业务希望改成**列表化 + 行内编辑**：

1. 进入页面即展示**全量人员列表**，每行显示该人的 部门 / 岗位 / 角色。
2. 新增两个特殊列：
   - **被评价人角色**：每人**只能一个**。
   - **评价人角色**：每人**可多个**，列表中用逗号分隔展示。
3. 每行有「编辑」，弹窗中可修改该人的被评价人角色（单选）与评价人角色（多选）。
4. 被评价人角色、评价人角色的可选项**只来自"内部评价 → 标签管理"页**（`EVAL_TAG`，tagType=1/2）。

业务示例：一个支行行长**作为被评价人**只能是"支行行长"一个角色；但**作为评价人**可以同时是多个角色（如既能评价副行长，又能评价其他人）。

## 2. 现状（事实基础）

- 标签字典表 `EVAL_TAG(TAG_ID, TAG_NAME, TAG_TYPE, STATUS, ...)`，`TAG_TYPE` 1=被评价人 / 2=评价人。
- 人员标签关联表 `EVAL_USER_TAG(ID, USER_ID, TAG_ID)`，唯一键 `UK_USER_TAG(USER_ID, TAG_ID)`，**当前允许一个用户绑多个任意类型标签**。
- `USER_ID` 关联 `PT_USER.USER_ID`（即工号）。
- 现有接口（performance，`EvalUserTagController`）：
  - `GET /api/admin/eval/user-tags?userId=` 查某人已绑标签
  - `POST /api/admin/eval/user-tags`（bind）、`DELETE /api/admin/eval/user-tags`（unbind）
- 关联能力（已验证可行）：
  - `PT_USER.USER_ID == ADDRBOOK_EMPLOYEE.emp_id`（工号）。
  - portal `AddressBookApi.getEmployees(List<String> empIds)` → `EmployeeDTO{orgName, position, ...}`（批量，上限 200）。
  - auth `UserService.pageUsers(UserQueryReqDTO)` 已实现分页+搜索（仅在 auth 内部，未走 Api）。
  - auth `UserRoleMapper.selectRolesByUserIds(List<String>)` 已有批量角色查询（返回 userId/roleId/roleCode/roleChName），但未在 `UserApi` 暴露。
  - `RoleSimpleDTO{roleId, roleCode, roleChName}`。
  - performance 的 pom **已依赖** auth-permission-center 与 portal-content-center，可直接调用 `UserApi` / `AddressBookApi`，无循环依赖。

## 3. 方案（选定：后端聚合接口）

前端只调一个聚合分页接口，后端编排，避免 N+1。被否决的备选：前端分页后逐行调角色/标签接口（N+1、慢、违反"列表批量查询"性能规范）。

### 3.1 数据层

**无需改表**。`EVAL_TAG` / `EVAL_USER_TAG` 现有结构足够。

"被评价人每人只能一个"由 **Service 层强制**，不加 DB 唯一约束（MySQL 难以表达"同一 user 下 tagType=1 至多一行"的部分唯一）。

### 3.2 后端接口（performance-engine-center, eval 子域）

#### (1) 分页聚合查询

```
GET /api/admin/eval/user-tags/page?keyword=&page=&pageSize=
@BizAuth(bizType = EVAL, action = LIST)
```

返回 `PageResult<EvalUserRoleRow>`，每行字段：

| 字段 | 来源 | 说明 |
|------|------|------|
| `userId` | PT_USER.USER_ID | 工号 |
| `userName` | userchnname ?: username | 显示名 |
| `orgName` | AddressBookApi → EmployeeDTO.orgName | 部门，缺失留空 |
| `position` | AddressBookApi → EmployeeDTO.position | 岗位，缺失留空 |
| `roles` | 批量 RBAC 角色 | `List<String>`（roleChName），只读 |
| `beEvalTag` | EVAL_USER_TAG ∩ tagType=1 | 单个 `{tagId, tagName}`，可空 |
| `evalTags` | EVAL_USER_TAG ∩ tagType=2 | `List<{tagId, tagName}>` |

编排步骤：
1. 调 auth 分页查 PT_USER（keyword/page/pageSize）→ 得到本页用户 + 总数。
2. 收集本页 `userIds`（约 pageSize 个）。
3. `AddressBookApi.getEmployees(userIds)` → Map<empId, EmployeeDTO>（取 orgName/position）。
4. 批量查角色 → Map<userId, List<RoleSimpleDTO>>。
5. 批量查 `EVAL_USER_TAG` JOIN `EVAL_TAG` WHERE user_id IN (...) → 按 user 分组，按 tagType 拆成 beEvalTag / evalTags。
6. 拼装每行返回。

> 防御：若某行存在历史脏数据（同一 user 多个 tagType=1），`beEvalTag` 取其一并不报错（展示降级），编辑保存后会被覆盖纠正。

#### (2) 覆盖式保存

```
PUT /api/admin/eval/user-tags/{userId}/roles
body: { "beEvalTagId": Long | null, "evalTagIds": [Long, ...] }
@BizAuth(bizType = EVAL, action = WRITE)
```

Service 逻辑（`@Transactional`）：
1. 校验：`beEvalTagId`（若非 null）必须存在且 `tagType=1`；`evalTagIds` 每个必须存在且 `tagType=2`；否则抛业务异常（沿用 eval 错误码体系）。
2. 删除该 `userId` 在 `EVAL_USER_TAG` 的**全部**记录。
3. 写入新记录：被评价标签（若 `beEvalTagId != null`）1 条 + 评价人标签 N 条。
4. `beEvalTagId=null` 即表示清空被评价人角色；`evalTagIds=[]` 即清空评价人角色。

> 结构上 body 只接受单个 `beEvalTagId`，**天然保证被评价单选**；Service 删-插实现"编辑即覆盖"语义。

#### (3) 旧接口处理

`POST` / `DELETE /api/admin/eval/user-tags`（bind/unbind）**前端不再调用**。接口保留以防其他调用方依赖；确认无其他调用方后可在后续清理。

### 3.3 auth-permission-center：UserApi 补 2 个方法

跨模块只能走 `*Api`，故在 `UserApi` 新增（实现委托已有 Service/Mapper）：

```java
/** 分页+关键词搜索用户（委托 UserService.pageUsers） */
PageResult<UserDTO> pageUsers(String keyword, int pageNo, int pageSize);

/** 批量查用户角色，避免 N+1（委托 UserRoleMapper.selectRolesByUserIds 分组） */
Map<String, List<RoleSimpleDTO>> getRolesByUserIds(List<String> userIds);
```

`UserDTO` 需含 userId/username/userchnname（确认现有字段，缺则补）。

### 3.4 前端（xanzc_frontend）

`src/views/eval/UserTags.vue` 重写为表格页：

- 顶部：搜索框（姓名/工号，防抖 300ms）+ `el-pagination`。
- 表格列：`姓名` | `工号` | `部门` | `岗位` | `角色(RBAC，逗号)` | `被评价人角色`(单个 tag，无则"—") | `评价人角色`(多个 tag，逗号分隔) | `操作(编辑)`。
- 编辑弹窗（`el-dialog`）：
  - 顶部只读展示 姓名/工号/部门/岗位/角色。
  - **被评价人角色**：`el-select` 单选 + 可清空，选项 = `listAllTags({tagType:1, status:1})`。
  - **评价人角色**：`el-select multiple`，选项 = tagType=2 启用标签。
  - 保存 → `saveUserRoles(userId, beEvalTagId, evalTagIds)` → 成功后刷新当前页。

`src/api/eval.js` 新增：

```js
pageUserRoles(params)                      // GET  /admin/eval/user-tags/page
saveUserRoles(userId, beEvalTagId, evalTagIds) // PUT /admin/eval/user-tags/{userId}/roles
```

`listAllTags` 复用现有实现。

## 4. 测试（TDD：先红后绿后重构）

后端：
- `EvalUserTagService` 覆盖式保存单测：
  - 已有「2 评价人 + 1 被评价」→ 保存新组合 → 旧记录全清、只剩新组合。
  - `beEvalTagId` 传入 tagType=2 标签 → 报错。
  - `evalTagIds` 含 tagType=1 标签 → 报错。
  - `beEvalTagId=null` → 清空被评价；`evalTagIds=[]` → 清空评价人。
- 聚合分页测试：给定用户/标签 fixture，验证每行 `beEvalTag` 单个、`evalTags` 列表、部门/岗位/角色拼装正确（`AddressBookApi`/`UserApi` 可 mock）。
- 命名遵循 CLAUDE.md：纯 mock service → `*Test.java`（surefire）；Spring 集成 → `*IT.java`（failsafe）。

前端：沿用现有 eval 页面实践，以手动验证为主，无强制单测。

## 5. 权限登记

新增 2 个 performance 接口登记 `PT_RESOURCE` + `@BizAuth`：
- `GET .../user-tags/page` → EVAL / LIST
- `PUT .../user-tags/{userId}/roles` → EVAL / WRITE

按 CLAUDE.md「鉴权失败优先排查 PT_* 表数据」SOP，确认新资源行 + R_ADMIN 授权到位。

## 6. 不做（YAGNI）

- 不做按部门/角色的高级筛选。
- 不做批量编辑。
- 不改 `EVAL_RULE` / 标签管理页（`Tags.vue`）。
- 不加 DB 唯一约束（单选由 Service 保证）。
- 不删旧 bind/unbind 接口（仅前端弃用）。

## 7. 影响面与风险

- **历史脏数据**：若某用户历史上绑了多个 tagType=1，聚合查询展示降级取其一，编辑保存即纠正。无需数据迁移脚本（可选：上线后跑一次性核对 SQL）。
- **跨模块调用**：performance → auth(`UserApi`) / portal(`AddressBookApi`)，依赖已存在，无循环依赖。
- **性能**：聚合接口对每页（≤100 行）做 4 次批量查询（用户分页 / 通讯录 / 角色 / eval 标签），满足 <500ms 目标；通讯录批量上限 200，pageSize≤100 安全。
