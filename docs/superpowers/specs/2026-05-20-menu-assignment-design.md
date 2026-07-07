# 菜单分配（Menu Assignment）设计

**日期**：2026-05-20  
**模块**：auth-permission-center / xanzc_frontend  
**关联记忆**：[[2026-launch-context]] 6-10 上线、W0 启动周

---

## 1. 背景与现状

### 1.1 现状事实

`PT_RESOURCE` 表当前 **303 条记录**，全部是 API 接口/操作型资源（A_/P_/R_/S_… 等前缀）。其中：

- IS_MENU=0 共 294 条
- IS_MENU=1 共 9 条（脏数据，实际仍是接口型，如「工作流任务审批通过」）
- **parent_resource_id 全部为 NULL，menu_endflag 全部为 '0'** → 表内**菜单树从未建立**

前端 `AppSidebar.vue` 当前从 `router.options.routes` 静态读取菜单，跟后端 PT_RESOURCE 无任何关联。

`Permission.vue` 现在按 URL 前缀映射 18 个业务模块、GET=R / 非GET=W 双勾选，**完全不区分 IS_MENU 维度**。

### 1.2 用户需求

> 「权限这里好像少了个菜单分配」

经澄清确认：希望**给角色配菜单（控制 sidebar 显示）**。完整版包含两层：

- **A**：管理员能在 Permission 页按菜单树勾选 → 写入 PT_ROLE_RESOURCE
- **B**：sidebar 按当前用户的允许菜单白名单动态显示

### 1.3 硬约束（用户明示）

- **严禁动表结构**：零 ALTER TABLE / 零 CREATE INDEX / 零新增字段 / 零新表
- 仅允许 INSERT/UPDATE 数据
- 提交按 OMC commit 协议拆分原子 commit

---

## 2. 目标 / 非目标

### 目标

1. PT_RESOURCE 沉淀 25 条菜单种子（3 个分组 + 22 个叶子菜单），形成正确的 parent 树
2. 后端新增 `GET /api/auth/menus`，返回当前登录用户的 menuPaths 白名单
3. Permission 页中栏加 tab，让管理员按菜单树勾选角色权限
4. AppSidebar 接入 menuPaths 白名单，按角色动态显示
5. SYS_ADMIN 跳过过滤（全开 sidebar）
6. 测试：3 个后端单元测试类（先红再绿）+ 前端手工验证清单

### 非目标（YAGNI，明确不做）

- 菜单 CRUD 后台 UI（管理员从 UI 加菜单）—— 菜单种子手工 SQL，业务稳定后再考虑
- 二级菜单嵌套（三层及以上）—— 当前 router 只两层
- 菜单图标 picker / 拖拽排序 —— 直接吃 router meta.icon
- 给用户直接配菜单（绕过角色）—— 仍走 role→resource
- 菜单内按钮级权限 —— 已有 resourceUrls + RBAC 拦截，不重做

---

## 3. 数据模型

### 3.1 PT_RESOURCE 字段复用（不动表结构）

| 字段 | 分组节点 | 叶子菜单 | 根级菜单 |
|---|---|---|---|
| RESOURCE_ID | `M_GROUP_PERF` | `M_PERF_METRICS` | `M_ROOT_WORKSPACE` |
| RESOURCE_URL | NULL | `/perf/metrics` | `/workspace` |
| RESOURCE_METHOD | `MENU`（约定占位） | `MENU` | `MENU` |
| MENU_NAME | 绩效与考核 | 指标库 | 工作台 |
| MENU_ICON_URL | 📈 | NULL | 🏠 |
| MENU_RANK_NO | 1,2,3 | 1,2,3... | 0 |
| **IS_MENU** | **1** | **1** | **1** |
| MENU_ENDFLAG | `'0'` 非叶 | `'1'` 叶子 | `'1'` 叶子 |
| PARENT_RESOURCE_ID | NULL | M_GROUP_PERF | NULL |
| STATUS | 0 | 0 | 0 |
| SYS_CODE | YITI | YITI | YITI |

### 3.2 菜单树结构（按 router/index.js 提取）

```
M_ROOT_WORKSPACE              工作台          /workspace        (无 group)
M_GROUP_PERF                  绩效与考核
  ├─ M_PERF_METRICS           指标库          /perf/metrics
  ├─ M_PERF_KPI_RULES         KPI 规则        /perf/kpi-rules
  ├─ M_PERF_TARGETS           目标管理        /perf/targets
  ├─ M_PERF_IMPORT            数据导入        /perf/import
  ├─ M_PERF_ADJUST            业绩调整        /perf/adjust
  └─ M_PERF_COMPUTE           考核计算        /perf/compute
M_GROUP_REPORT                报表分析
  ├─ M_REPORT_DYNAMIC         动态指标查询    /report/dynamic
  ├─ M_REPORT_DASHBOARD       行长仪表盘      /report/dashboard
  ├─ M_REPORT_PRESETS         预置报表        /report/presets
  └─ M_REPORT_SQL             SQL 探查        /report/sql
M_GROUP_SYSTEM                系统设置
  ├─ M_SYS_USERS              用户管理        /system/users
  ├─ M_SYS_ROLES              角色管理        /system/roles
  ├─ M_SYS_RESOURCES          资源/菜单       /system/resources
  ├─ M_SYS_PERMISSION         权限配置        /system/permission
  ├─ M_SYS_DICT               字典管理        /system/dict
  ├─ M_SYS_CALENDAR           工作日历        /system/calendar
  ├─ M_SYS_JOBS               任务调度        /system/jobs
  ├─ M_SYS_AUDIT              审计日志        /system/audit
  ├─ M_SYS_NOTIFICATIONS      通知消息        /system/notifications
  ├─ M_SYS_CONFIG             系统配置        /system/config
  └─ M_SYS_FILES              文件管理        /system/files
```

共 25 条菜单（1 根级 + 3 分组 + 21 叶子）。

`meta.hidden=true` 的路由（`/workflow/task/:taskId`）**不录入** PT_RESOURCE，不进入菜单管理。

### 3.3 命名规约

- 菜单 ID 一律 `M_` 开头 → 跟现有 303 条接口资源前缀隔离
- RESOURCE_METHOD 用约定值 `MENU` → 不参与 URL 拦截，仅占位
- 菜单与接口共用 PT_ROLE_RESOURCE 关联表（M:N），变更 / 审计 / 缓存逻辑一套

### 3.4 现有 9 条脏数据处理

种子 SQL 第一步：

```sql
UPDATE PT_RESOURCE SET IS_MENU = 0
 WHERE IS_MENU = 1 AND RESOURCE_METHOD <> 'MENU';
-- 9 条接口型脏数据修正回 IS_MENU=0
```

---

## 4. 后端 API 契约

### 4.1 新增端点

**`GET /api/auth/menus`** —— 返回当前登录用户允许显示的菜单路径白名单

- 请求：无参数（从 session 取 currentUser）
- 响应：
  ```json
  {
    "code": "0",
    "data": {
      "menuPaths": ["/workspace", "/perf/metrics", ...],
      "isSystemAdmin": false
    }
  }
  ```

设计理由：
- 返 `menuPaths`（resourceUrl）而非 `menuIds` → 前端零映射，sidebar `allowedPaths.has(routePath)` 即用
- 同时返 `isSystemAdmin` → admin 前端直接全开，跳过过滤
- 走 AuthenticationFilter 白名单（已认证才能调，不是 public）
- 不需要 `@BizAuth` 注解（个人读自己菜单白名单，无业务对象）

### 4.2 复用现有端点（小改）

| 端点 | 改动 |
|---|---|
| `GET /api/admin/resources` | 加 `?isMenu=1` query 参数 → 返回菜单树（按 PARENT_RESOURCE_ID 组装） |
| `POST /api/admin/roles/{roleId}/resources` | **不动**，菜单 ID 跟接口 ID 一起塞进 resourceIds 列表（共表共关联） |
| `GET /api/admin/roles/{roleId}/resources` | **不动**，菜单 + 接口 ID 一起返 |

### 4.3 后端改动文件清单

1. `AuthController.java` —— 加 `getMyMenus()` 方法（约 15 行）
2. `AuthService.java` —— 加 `listMenuPathsByEmpId(empId)` 方法（查 role → resource → filter isMenu=1 → map resourceUrl）
3. `ResourceController.java` —— `list` 接受 `isMenu` query 参数（1 行 if 过滤）
4. `ResourceMapper.java` / `.xml` —— `selectByIsMenu(Integer isMenu)` 查询方法（5 行）
5. 菜单种子 SQL：`docs/superpowers/sql/2026-05-20-pt-resource-menu-seed.sql`（25 条 INSERT + 1 条 UPDATE 修正脏数据）

### 4.4 缓存策略

- `GET /menus` 内部走 cache-aside：复用 `auth:user-roles:{empId}` + `auth:role-resource:{roleId}`
- 前端拿到所有 resourceIds 后，**后端 filter 出 IS_MENU=1 的 resourceUrl** 返前端
- 不新增独立缓存 key（YAGNI，25 条数据查询无成本）

权限变更时沿用现有 `PermissionCacheInvalidatedEvent`，下次拉 /menus 重算。

---

## 5. 前端设计

### 5.1 Permission 页 — 中栏内加 tab

```
┌─────────┬──────────────────────────────────────┬─────────────┐
│  角色   │  [接口权限 R/W ●] [菜单权限]         │  数据范围   │
│ (10 个) ├──────────────────────────────────────┤             │
│         │  (左 tab：现有按业务模块 R/W 不变)   │  (现状)     │
│ ▶ admin │                                      │             │
│ ▶ CM    │  (右 tab：菜单树勾选)                │             │
│ ▶ ...   │                                      │             │
│         │  ⚠ 13 条改动未保存  [还原] [保存绑定] │             │
└─────────┴──────────────────────────────────────┴─────────────┘
```

**关键设计**：

1. **两 tab 共享同一个 `checkedIds` Set**
   - 进 Permission 页时一次拉全部 resourceId（菜单 + 接口）
   - 接口 tab 显示 IS_MENU=0 部分，菜单 tab 显示 IS_MENU=1 部分
   - 切换 tab **不丢未保存改动**

2. **菜单树用 `<el-tree>`**
   ```vue
   <el-tree
     :data="menuTree"
     show-checkbox
     node-key="resourceId"
     default-expand-all
     :check-strictly="false"
     :default-checked-keys="checkedMenuIds"
     @check="onTreeCheck"
   />
   ```
   - `check-strictly=false` → 父子联动，勾「绩效与考核」自动勾下面 6 个
   - `@check` 触发时同步到共享 `checkedIds` Set

3. **底栏（dirty + 保存按钮）两 tab 共享**
   - 一次保存（"13 条改动"）同时落两个维度
   - 保存确认对话框沿用现有 `PERMISSION_CHANGE` + 变更原因输入

4. **SYS_ADMIN 角色选中时**
   - 菜单 tab 显示 banner「系统管理员对全部菜单可见，无需配置」

### 5.2 AppSidebar 接入 menuPaths 白名单

`groups` computed 加 1 层过滤：

```js
const groups = computed(() => {
  const store = useUserStore();
  const isAdmin = store.isSystemAdmin || store.user?.roles?.some(r => r.roleCode === 'SYS_ADMIN');
  const allowedPaths = new Set(store.menuPaths || []);

  for (const r of all) {
    if (!r.meta?.title || !r.path) continue;
    if (r.meta?.hidden) continue;
    const path = '/' + r.path;
    if (!isAdmin && !allowedPaths.has(path)) continue;  // 新增过滤
    // ...原分组逻辑不变
  }
});
```

- `router.options.routes` 仍是 path/icon/title 真相
- 后端只返「允许显示的 path 列表」
- 空分组自动隐藏（grouped[group.title] 为空时不渲染）

### 5.3 userStore 扩展

```js
const menuPaths = ref(cached?.menuPaths || []);
const isSystemAdmin = ref(cached?.isSystemAdmin || false);

function setMenuPaths(paths) { /* ... */ }
function setIsSystemAdmin(v)  { /* ... */ }
```

`menuPaths` + `isSystemAdmin` 一并写入 sessionStorage，F5 刷新仍可用。

### 5.4 router 守卫 — 拉 menus 时机

```js
router.beforeEach(async (to) => {
  if (to.meta?.public) return true;
  const store = useUserStore();
  if (!store.isLoggedIn) {
    try {
      const user = await http.get('/api/auth/current-user');
      if (user?.empId) {
        store.setUser(user);
        // 紧跟着拉菜单白名单
        const menus = await http.get('/api/auth/menus');
        store.setMenuPaths(menus?.menuPaths || []);
        store.setIsSystemAdmin(menus?.isSystemAdmin || false);
      }
    } catch (_) {}
  }
  if (store.isLoggedIn) return true;
  return { path: '/login', query: { redirect: to.fullPath } };
});
```

确保 router 守卫返 true 时 sidebar 拿到的 store.menuPaths 已就绪。

### 5.5 hidden 路由 + 直接访问无权 URL

- `meta.hidden=true`（审批办理）：sidebar 不显示；用户从工作台「办理」按钮跳，仍走 `/api/auth/permissions` URL 白名单，跟现状一致
- 用户直接在地址栏敲 `/perf/metrics` 但他没权：**sidebar 不显示但 router 不拦**，进去后页面 API 请求被后端 401/403 拦截 —— 菜单只藏不挡，后端是权威

### 5.6 前端文件改动清单

| 文件 | 改动 |
|---|---|
| `views/system/Permission.vue` | 中栏加 `<el-tabs>` + `menuTree` 数据 + `onTreeCheck` 联动 `checkedIds` |
| `api/system.js` | 新增 `listMenuTree()` 调 `GET /admin/resources?isMenu=1` |
| `api/auth.js` | 新增 `getMyMenus()` 调 `GET /api/auth/menus` |
| `components/AppSidebar.vue` | `groups` computed 加 menuPaths 过滤 |
| `stores/user.js` | 加 `menuPaths` + `isSystemAdmin` 字段及 setter |
| `router/index.js` | 守卫里拉 menus 一并写 store |

---

## 6. 测试策略

### 6.1 后端 TDD（红 → 绿 → 重构）

**`AuthControllerTest`**（新增）
- `testGetMyMenus_未登录_返回401`
- `testGetMyMenus_普通用户_返回其角色对应menuPaths子集`
- `testGetMyMenus_系统管理员_isSystemAdmin为true`

**`ResourceControllerTest`**（扩展）
- `testListResources_isMenu1_返回菜单树`
- `testListResources_isMenu0_返回接口列表`
- `testListResources_无isMenu参数_返回全部`

**`RoleResourceServiceTest`**（扩展）
- `testReplaceRoleResources_菜单与接口共存_正确写入PT_ROLE_RESOURCE`
- `testReplaceRoleResources_变更后缓存失效事件正确发布`

### 6.2 前端手工验证清单

- [ ] Permission 页 tab 切换无 dirty 丢失
- [ ] 勾分组 → 子菜单全勾；取消分组 → 子菜单全消
- [ ] 保存后刷新页面，勾选状态正确还原
- [ ] 切角色时 dirty 弹确认对话框
- [ ] SYS_ADMIN 角色选中时菜单 tab 显示 banner
- [ ] 普通用户登录 → sidebar 只显示授权菜单 + 空分组自动隐藏
- [ ] SYS_ADMIN 登录 → sidebar 全开
- [ ] 直接访问无权 URL → 进得去，但页内 API 401（符合预期）
- [ ] F5 刷新后 menuPaths 从 sessionStorage 恢复（或重新 fetch）

---

## 7. 兼容性 & 风险

### 7.1 兼容性矩阵

| 现有功能 | 影响 |
|---|---|
| 303 条接口资源 + PT_ROLE_RESOURCE 现有绑定 | 0 影响（菜单 `M_` 前缀隔离） |
| AuthorizationInterceptor 接口拦截 | 0 影响（不查 IS_MENU=1） |
| Permission 页接口 R/W 视图 | 0 影响（中栏内新加 tab，原视图作为默认 tab） |
| SYS_ADMIN 跳过权限校验 | 0 影响（sidebar 显式 SYS_ADMIN 全开兜底） |
| F5 刷新 sessionStorage 清空 | router 守卫 fetch current-user + menus 一并恢复 |
| 已有 9 条 IS_MENU=1 脏数据 | 种子 SQL 第一步 UPDATE 修正 |

### 7.2 风险与缓解

| 风险 | 缓解 |
|---|---|
| 菜单种子手工维护，router 改动后忘了同步 SQL | router/index.js 顶部加注释提示；后续可考虑 lint 脚本（不在本期 scope） |
| 25 条 INSERT 在测试库 + 生产库都要执行 | SQL 文件路径明确，手工部署时 DBA 各执行一次（项目已废 Flyway） |
| 菜单变更后用户首次拉的 menuPaths 是旧的 | 沿用 `PermissionCacheInvalidatedEvent` 自动清缓存，下次拉重算 |
| **不动表结构（用户硬约束）** | 方案只 INSERT/UPDATE 数据；后端 mapper 全用现有列；**零 ALTER / 零 CREATE INDEX / 零新字段** |

---

## 8. 提交计划

按 OMC commit 协议拆 4 个原子 commit：

```
commit 1: feat(auth): /api/auth/menus 端点 + ResourceController isMenu 过滤 + 测试
          文件：AuthController.java / AuthService.java / ResourceController.java
                / ResourceMapper.java / ResourceMapper.xml
                / AuthControllerTest.java / ResourceControllerTest.java
                / RoleResourceServiceTest.java

commit 2: data(seed): PT_RESOURCE 菜单种子（25 条 + 修正 9 条脏数据）
          文件：docs/superpowers/sql/2026-05-20-pt-resource-menu-seed.sql

commit 3: feat(frontend): Permission 页加菜单 tab + API client
          文件：xanzc_frontend/src/views/system/Permission.vue
                / src/api/system.js / src/api/auth.js

commit 4: feat(frontend): AppSidebar 接入 menuPaths + userStore 扩展 + router 守卫
          文件：xanzc_frontend/src/components/AppSidebar.vue
                / src/stores/user.js / src/router/index.js
```

每个 commit 末尾带署名标注。

---

## 9. 实施顺序

1. **commit 1（后端）**：先写 3 个测试类（红），再写后端代码让测试通过（绿），重构（refactor）
2. **commit 2（数据）**：执行 SQL 把菜单种子灌入测试库验证
3. **commit 3（前端配置入口）**：Permission 页 tab 实现，连真后端验证「勾选 → 保存 → 刷新仍在」
4. **commit 4（前端 sidebar 联动）**：sidebar 接入，全链路验证「不同角色登录 sidebar 不同」

完整工作量：1 ~ 1.5 天。

---

## 10. 参考

- 后端模块：[auth-permission-center/CLAUDE.md](../../auth-permission-center/CLAUDE.md)
- 前端入口：xanzc_frontend/
- 关键依赖：Spring Boot 3.2.3 / MyBatis 3.0.3 / Vue 3 / Element Plus / Pinia
- 相关记忆：`[[2026-launch-context]]` 上线时间倒排、`[[wangyq-frontend-only-lf-backend-collab]]` 平时只改前端但本期破例改后端
