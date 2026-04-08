# AUTH 接口一致性修复设计方案

**日期**: 2026-04-08
**状态**: 待批准
**范围**: auth-permission-center 模块 — 接口设计文档与代码一致性

---

## 1. 背景

对 auth-permission-center 下全部 6 个 Controller（AuthController / RoleController / ResourceController / OrgController / BizScopeController / UserRoleController）与接口设计文档 `docs/modules/auth-permission-center/03-接口设计与报文.md` 进行了逐端点对比（共 24 个端点）。

结果发现 **3 处不一致**，均为文档描述与实际代码行为差异。

---

## 2. 接口一致性分析结果

### 2.1 统计概览

| 分类 | 数量 |
|------|------|
| 完全一致的端点 | 21 |
| 存在不一致的端点 | 3 |
| **合计** | **24** |

### 2.2 完全一致的端点（21 个）

| 分组 | 端点 | Controller |
|------|------|-----------|
| A | A.1 POST /api/auth/login | AuthController |
| A | A.2 POST /api/auth/logout | AuthController |
| A | H.1 GET /api/auth/permissions | AuthController |
| A | H.2 POST /api/auth/check-permission | AuthController |
| B | B.1 GET /api/admin/roles | RoleController |
| B | B.2 POST /api/admin/roles | RoleController |
| B | B.3 PUT /api/admin/roles/{roleId} | RoleController |
| B | B.4 DELETE /api/admin/roles/{roleId} | RoleController |
| B | B.5 GET /api/admin/roles/{roleId}/users | RoleController |
| C | C.1 GET /api/admin/users/{userId}/roles | UserRoleController |
| C | C.2 POST /api/admin/users/{userId}/roles | UserRoleController |
| C | C.3 DELETE /api/admin/users/{userId}/roles/{roleId} | UserRoleController |
| D | D.1 GET /api/admin/resources/tree | ResourceController |
| D | D.2 POST /api/admin/resources | ResourceController |
| D | D.3 PUT /api/admin/resources/{resourceId} | ResourceController |
| D | D.4 DELETE /api/admin/resources/{resourceId} | ResourceController |
| D | D.5 GET /api/admin/roles/{roleId}/resources | ResourceController |
| E | E.1 POST /api/admin/roles/{roleId}/resources | ResourceController |
| E | E.2 PUT /api/admin/roles/{roleId}/resources | ResourceController |
| F | F.1 GET /api/admin/biz-scopes | BizScopeController |
| F | F.2 GET /api/admin/biz-scopes/matrix | BizScopeController |
| F | F.3 POST /api/admin/biz-scopes | BizScopeController |
| F | F.4 DELETE /api/admin/biz-scopes/{id} | BizScopeController |
| G | G.1 GET /api/orgs/tree | OrgController |
| G | G.3 GET /api/orgs/subtree | OrgController |

以上端点的路径、HTTP 方法、@BizAuth 注解、请求/响应类型均与文档完全一致。

---

## 3. 修复方案（仅修文档，代码不变）

**选择理由**：
- `current-user` 保持轻量（不加载 roles/permissions/bizScopes）是合理的性能设计，前端已有 `/permissions` 等专属接口
- `getOrgUsers` 使用 `OrgUserDTO` 包含 `userId`/`userChnName` 向后兼容字段，是有意的扩展设计
- 仅修文档零风险，不影响现有功能

---

### 3.1 修复 A.3 — GET /api/auth/current-user 权限描述

**位置**: `docs/modules/auth-permission-center/03-接口设计与报文.md` 第 86-88 行

**当前文档**:
```
- **权限**：需认证（已登录用户）
- **说明**：从Session中获取当前登录用户的完整信息（含角色、权限、BizScope）
```

**修改为**:
```
- **权限**：需认证（已登录用户），且需 `SYS_CONFIG` + `READ` 权限
- **说明**：从Session中获取当前登录用户的身份与机构信息
```

> **说明**：原文档无权限描述，但代码有 `@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)` 注解。文档应与代码一致。

---

### 3.2 修复 G.2 — GET /api/orgs/{orgCode}/users BizType 和 DTO 类型

**位置**: `docs/modules/auth-permission-center/03-接口设计与报文.md` 第 596-621 行

**修改 1 — @BizAuth 注解（第 599 行）**:

**当前文档**:
```
- **权限**：`@BizAuth(bizType = SYS_CONFIG, action = READ)`
```

**修改为**:
```
- **权限**：`@BizAuth(bizType = ORG, action = READ)`
```

> **说明**：`BizType.ORG` 已存在于枚举中（描述为"组织机构"），用于机构下用户查询比 `SYS_CONFIG` 更语义准确。

**响应体现状**:
```
ResponseWrapper<OrgUserDTO>（items 在 page.records 中）
```

> **说明**：`OrgService.getOrgUsers()` 返回 `PageResult<OrgUserDTO>`，`ResponseWrapper.page(result)` 将其放入 `page` 字段，items 在 `page.records` 中。接口设计文档已更新为完整的 JSON 示例结构。`OrgUserRespDTO.java` 已删除（从未被代码使用）。

---

## 4. 不修改的项（设计决策说明）

以下项不在此次修复范围内，保持现状：

| 项 | 决策 | 理由 |
|----|------|------|
| A.3 返回 `roles`/`permissions`/`bizScopes` 为 null | 保持代码不变 | 前端已有 `/permissions` 和 `/api/admin/users/{userId}/roles` 专属接口，一次加载完整数据会增加响应延迟 |
| A.3 不在 WHITELIST 中 | 保持文档（更新后）与代码一致 | 需要认证是合理的安全要求，文档已修正 |
| `OrgUserDTO` 含 `userId`/`userChnName` | 保持向后兼容 | 向后兼容字段不破坏 API 契约，新增字段（empId/displayName/email/roles）与文档一致 |

---

## 5. 涉及文件

### 5.1 需要修改的文件（仅文档）

| 文件 | 修改内容 |
|------|---------|
| `docs/modules/auth-permission-center/03-接口设计与报文.md` | A.3 权限描述更新 + G.2 BizType 更新 + G.2 DTO 类型修正 |

### 5.2 无需修改的文件

- 全部 Java 代码（controller / service / mapper / DTO 等）
- 全部 XML mapper 文件
- 全部测试代码

---

## 6. 测试策略

无需测试用例修改（仅文档变更）。

---

## 7. 风险评估

| 风险 | 等级 | 说明 |
|------|------|------|
| 文档修改影响运行时 | 无 | 仅修改 Markdown 文档，不影响任何代码 |
| 文档修改影响前端集成 | 低 | 文档修正确认了实际 API 行为，前端无需调整 |
| 遗漏其他不一致 | 低 | 已对全部 24 个端点逐一对比确认 |
