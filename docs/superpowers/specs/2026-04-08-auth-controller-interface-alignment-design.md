# 接口设计与实现一致性修复方案

> **日期**: 2026-04-08
> **目标**: 将 auth-permission-center 模块下的各 Controller 实现与 `docs/modules/auth-permission-center/03-接口设计与报文.md` 进行严格对齐
> **策略**: 严格对齐文档（用户确认）

---

## 1. 背景

`auth-permission-center` 模块包含 6 个 Controller，共计约 26 个 REST 接口。与接口设计文档对比后，发现 5 处不一致，涵盖：返回类型错误、路径格式不一致、参数缺失、功能缺失。

---

## 2. 需要修复的不一致

### 2.1 OrgController — G.2 机构用户接口缺少分页

**问题**: 文档 G.2 节要求 `GET /api/orgs/{orgCode}/users` 支持 `keyword/pageNo/pageSize` 分页参数，当前实现无分页能力。

**修改文件**:
- `OrgController.java` — 添加分页参数，返回 `PageResult<OrgUserDTO>`
- `OrgService.java` — 新增分页查询方法

**修改内容**:
```
@GetMapping("/{orgCode}/users")
@BizAuth(bizType = BizType.ORG, action = BizAction.READ)
public ResponseWrapper<List<OrgUserDTO>> getOrgUsers(...)
  ↓
@GetMapping("/{orgCode}/users")
@BizAuth(bizType = BizType.ORG, action = BizAction.READ)
public ResponseWrapper<PageResult<OrgUserDTO>> getOrgUsers(
        @PathVariable String orgCode,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize)
```

**验收标准**: 接口参数和响应格式与文档 G.2 节完全一致。

---

### 2.2 OrgController — G.3 子树接口返回类型错误

**问题**: 文档 G.3 节要求 `GET /api/orgs/subtree` 返回 `List<OrgTreeNodeDTO>`（含 children 树形结构），当前返回 `List<OrgDTO>`（扁平结构）。

**修改文件**:
- `OrgController.java` — 方法返回类型从 `List<OrgDTO>` 改为 `List<OrgTreeNodeDTO>`
- `OrgService.java` — `getOrgSubtree()` 确保返回含 children 的树形结构

**验收标准**: 返回类型为 `List<OrgTreeNodeDTO>`，包含 `children` 字段。

---

### 2.3 BizScopeController — F.1/F.3 路径缺尾部斜杠

**问题**: 文档 F.1/F.3 节要求路径为 `/api/admin/biz-scopes`（无尾部斜杠），当前实现为 `/api/admin/biz-scopes/`（有尾部斜杠）。

**修改文件**:
- `BizScopeController.java` — 两处 `@RequestMapping` 和 `@GetMapping/@PostMapping` 路径去掉尾部 `/`

**修改内容**:
```
@RestController
@RequestMapping("/api/admin/biz-scopes")     // 去掉尾部斜杠
public class BizScopeController {
    @GetMapping("/")     →  @GetMapping("")
    @PostMapping("/")    →  @PostMapping("")
}
```

**注意**: F.4 删除接口 `/api/admin/biz-scopes/{id}` 路径正确（带 path variable），无需修改。

**验收标准**: F.1 和 F.3 接口路径严格为 `/api/admin/biz-scopes`，无尾部斜杠。

---

### 2.4 UserRoleController — C.1/C.2 路径缺尾部斜杠

**问题**: 文档 C.1/C.2 节要求路径为 `/api/admin/users/{userId}/roles`（无尾部斜杠），当前实现为 `/api/admin/users/{userId}/roles/`（有尾部斜杠）。

**修改文件**:
- `UserRoleController.java` — `@GetMapping` 和 `@PostMapping` 路径去掉尾部 `/`

**修改内容**:
```
@GetMapping("/")     →  @GetMapping("")
@PostMapping("/")    →  @PostMapping("")
```

**注意**: C.3 解绑接口 `/api/admin/users/{userId}/roles/{roleId}` 路径正确，无需修改。

**验收标准**: C.1 和 C.2 接口路径严格为 `/api/admin/users/{userId}/roles`，无尾部斜杠。

---

### 2.5 AuthController — A.3 current-user 字段不完整

**问题**: 文档 A.3 节要求 `GET /api/auth/current-user` 返回完整字段（username、displayName、mainOrgName、orgLevel、roles、permissions、bizScopes），当前实现只返回 empId、mainOrgCode、isSystemAdmin，其他为 null。

**修改文件**:
- `AuthController.java` — `getCurrentUser()` 补全所有字段
- `AuthService.java` — 确保 Session 中存储的 `CurrentUserContext` 包含 displayName、mainOrgName、orgLevel
- `CurrentUserRespDTO.java` — 确认包含所有字段（empId、username、displayName、mainOrgCode、mainOrgName、orgLevel、roles、permissions、bizScopes、isSystemAdmin）

**修改内容**:
```java
// 原来只返回部分字段
dto.setEmpId(ctx.empId());
dto.setMainOrgCode(ctx.mainOrgCode());
dto.setIsSystemAdmin(ctx.systemAdmin());
dto.setRoles(null);       // 应填充
dto.setPermissions(null); // 应填充
dto.setBizScopes(null);   // 应填充
// 缺少: username, displayName, mainOrgName, orgLevel

// 修改后：完整填充所有字段
dto.setEmpId(ctx.empId());
dto.setUsername(ctx.username());
dto.setDisplayName(ctx.displayName());
dto.setMainOrgCode(ctx.mainOrgCode());
dto.setMainOrgName(ctx.mainOrgName());
dto.setOrgLevel(ctx.orgLevel());
dto.setIsSystemAdmin(ctx.systemAdmin());
dto.setRoles(authService.getUserRoles(ctx.empId()));          // 从缓存获取
dto.setPermissions(authService.getUserResourceUrls(ctx.empId())); // 从缓存获取
dto.setBizScopes(authService.getUserBizScopes(ctx.empId()));   // 从缓存获取
```

**验收标准**: 响应体包含文档 A.3 节定义的所有 10 个字段，且值非空（roles/permissions/bizScopes 可为空列表但不为 null）。

---

## 3. 涉及文件清单

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `OrgController.java` | 修改 | G.2 加分页，G.3 改返回类型 |
| `OrgService.java` | 修改 | 新增分页方法，getOrgSubtree 返回树形 |
| `BizScopeController.java` | 修改 | F.1/F.3 路径去掉尾部斜杠 |
| `UserRoleController.java` | 修改 | C.1/C.2 路径去掉尾部斜杠 |
| `AuthController.java` | 修改 | A.3 补全所有字段 |
| `AuthService.java` | 修改 | 补全 CurrentUserContext 字段 |
| `CurrentUserRespDTO.java` | 修改 | 确认/补全字段定义 |

---

## 4. 测试验收

每个修复的接口需按以下清单验证：

1. **接口路径** — 与文档完全一致（区分有无尾部斜杠）
2. **请求参数** — 文档要求的必填/可选参数均正确实现
3. **响应字段** — 文档定义的每个字段均返回，类型正确
4. **@BizAuth 注解** — 权限类型和 action 与文档一致
5. **分页响应** — PageResult 格式正确（records/total/pageNo/pageSize）

---

## 5. 风险评估

- **风险等级**: 低
- **原因**: 纯接口对齐修复，不涉及业务逻辑变更
- **影响**: 前端调用方需适配路径变更（F.1/F.3/C.1/C.2）
- **回滚方案**: 如有问题，可通过 git revert 快速回滚
