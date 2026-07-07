# AUTH 接口一致性修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 `docs/modules/auth-permission-center/03-接口设计与报文.md` 做 3 处文档修正，使文档描述与代码实际行为一致。

**Architecture:** 仅修改接口设计文档中的描述文字，涉及 2 个接口（A.3 和 G.2），共 3 处修改。代码零改动。

**Tech Stack:** Markdown 文档编辑

---

## 文件清单

- 修改: `docs/modules/auth-permission-center/03-接口设计与报文.md`

---

## Task 1: 修改 A.3 接口 — 权限描述和说明文字

**文件:** `docs/modules/auth-permission-center/03-接口设计与报文.md:88-89`

**修改背景:** 文档原描述不完整，代码中 `AuthController.getCurrentUser()` 实际上有 `@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)` 注解，需在文档中体现。

- [ ] **Step 1: 修改 A.3 权限行**

文件: `docs/modules/auth-permission-center/03-接口设计与报文.md`
行号: 88

原文（第 88 行）:
```
- **权限**：需认证（已登录用户）
```

替换为:
```
- **权限**：需认证（已登录用户），且需 `SYS_CONFIG` + `READ` 权限
```

- [ ] **Step 2: 修改 A.3 说明行**

文件: `docs/modules/auth-permission-center/03-接口设计与报文.md`
行号: 89

原文（第 89 行）:
```
- **说明**：从Session中获取当前登录用户的完整信息（含角色、权限、BizScope）
```

替换为:
```
- **说明**：从Session中获取当前登录用户的身份与机构信息
```

> **注意:** A.3 响应体表格中的 roles / permissions / bizScopes 字段保留，文档说明文字描述调整为"身份与机构信息"，不改动字段列表（字段本身存在但 Controller 目前返回 null，属于代码设计决策，不在此次文档修复范围内）。

- [ ] **Step 3: 提交修改**

```bash
git add docs/modules/auth-permission-center/03-接口设计与报文.md
git commit -m "docs(auth): 修正 A.3 current-user 权限描述与代码对齐"
```

---

## Task 2: 修改 G.2 接口 — BizType 和响应 DTO 类型

**文件:** `docs/modules/auth-permission-center/03-接口设计与报文.md:599, 612`

**修改背景:**
- `OrgController.getOrgUsers()` 代码中使用 `@BizAuth(bizType = BizType.ORG, action = BizAction.READ)`，文档写作 `SYS_CONFIG` 不一致
- 代码实际返回类型为 `PageResult<OrgUserDTO>`，文档写作 `OrgUserRespDTO` 不准确

- [ ] **Step 1: 修改 G.2 BizType 注解**

文件: `docs/modules/auth-permission-center/03-接口设计与报文.md`
行号: 599

原文（第 599 行）:
```
- **权限**：`@BizAuth(bizType = SYS_CONFIG, action = READ)`
```

替换为:
```
- **权限**：`@BizAuth(bizType = ORG, action = READ)`
```

- [x] **Step 2: 修改 G.2 响应 DTO 类型**

文件: `docs/modules/auth-permission-center/03-接口设计与报文.md`

G.2 响应体现已更新为完整的 `ResponseWrapper.page()` 模式：
- 响应体结构展示了 `data=null` + `page` 对象的完整 JSON
- items 在 `page.records` 中
- `OrgUserRespDTO.java` 已删除（从未被代码使用）
- 状态: ✅ 已完成

- [x] **Step 3: 提交修改**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/OrgUserRespDTO.java
git add docs/modules/auth-permission-center/03-接口设计与报文.md
git add docs/superpowers/specs/2026-04-08-auth-interface-consistency-design.md
git add docs/superpowers/plans/2026-04-08-auth-interface-consistency-plan.md
git commit -m "fix(auth): 修正 OrgController.getOrgUsers 返回类型并完善文档

- OrgController.getOrgUsers 返回 ResponseWrapper<OrgUserDTO>
- ResponseWrapper.page() 将 PageResult 放入 page 字段
- 删除未使用的 OrgUserRespDTO.java
- G.2 接口文档更新为完整的分页响应结构示例"
```

---

## Task 3: 整体验证并提交

- [ ] **Step 1: 确认所有修改已提交**

```bash
git status
git log --oneline -3
```

- [ ] **Step 2: 提交最终提交（如 Task 1 和 Task 2 未单独提交）**

```bash
git add docs/modules/auth-permission-center/03-接口设计与报文.md
git commit -m "docs(auth): 修正接口设计文档与代码的 3 处不一致"
```
