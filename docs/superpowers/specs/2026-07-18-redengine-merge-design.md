# 红色引擎（redengine）并入 Branch Platform 设计规格

- **日期**: 2026-07-18
- **分支**: `feature/redengine-merge`（本次合并全部改动所在分支，不动 master）
- **状态**: 设计已获用户批准（含"首期不接 Flowable"决策点）

## 1. 背景与目标

`redengine/` 是独立开发的**党建工程管理系统**（红色引擎，西安分行党建管理）：

- 后端 `red-engine-server/`：Spring Boot 2.7 + Java 8，包名 `com.redengine`，JWT + Spring Security，MyBatis-Plus，MySQL（本地实际以 SQLite mock 运行），76 个 Java 类、11 个 Controller
- 前端 `red-engine-web/`：Vue 3 + Vite 独立工程，18 个视图，JWT localStorage 认证
- 业务：四大维度材料上报 → 支部审核 → 组织终审 → 评分（35/50/10/5 四维度、逾期扣分、红黄牌预警）→ 驾驶舱/归档/导出

**目标**：将红色引擎并入 Branch Platform（yiti）作为第 10 个业务模块：

1. 保留红色引擎的**登录页面外观**与**前端页面风格**
2. 系统设置、权限管理**改用平台**（PT_* RBAC + SYS_DICT 等），废弃红色引擎自建的用户/角色/菜单/字典体系
3. 执行前备份 yiti 库为 .sql；本次合并所有数据库变更**只在测试库 `yiti_test` 执行**

## 2. 已确认的关键决策

| 决策点 | 结论 |
|--------|------|
| 前端形态 | 并入 `xanzc_frontend`，挂 `/redengine/**` 路由区，保留原风格与登录页 |
| 后端形态 | 升级移植为新 Maven 模块 `red-engine-center`（JDK17 / Boot 3.2.3） |
| 组织映射 | 党组织树 `RE_PARTY_ORG` 模块私有；账号统一平台 `PT_USER`；新增用户↔党组织映射表 |
| 库策略 | 双保险：mysqldump 备份 .sql + 新建 `yiti_test` 全量导入，DDL/DML 只进测试库 |
| 存量数据 | 党组织树/字典作种子 SQL 迁移；演示业务数据（16 条上报等）仅导入 `yiti_test` 作联调验收 |
| 审核流 | **首期不接 Flowable**，保留红色引擎自有两级审核状态机（用户已确认） |
| 分支 | 全部改动在 `feature/redengine-merge`；`redengine/` 原工程目录不纳入 git |

## 3. 总体架构

```
xanzc_frontend (8090)
  ├── 平台既有页面（不变）
  └── /redengine/** 路由区（RedEngineLayout 独立布局 + 红色主题 + 原登录页外观）
        │  /api 代理（同 session cookie）
        ▼
bootstrap (18081, 单 JVM)
  ├── 既有 9 业务模块（不变）
  └── red-engine-center（新，com.bank.branch.platform.redengine）
        ├── 依赖 common + auth-permission-center + system-governance-center
        ├── 认证：平台 Spring Session JDBC（废弃 JWT/Spring Security/Redis）
        ├── 权限：PT_RESOURCE + @BizAuth + PT_ROLE（新增 4 个党建角色）
        └── 数据：yiti_test 中的 RE_* 表（开发期 remerge profile）
```

模块依赖规则遵守平台红线：只通过 `*Api`/`*QueryApi` 跨模块交互；`report-analytics-center` 不依赖本模块（首期无报表诉求）；本模块不被其他业务模块依赖。

## 4. 数据库设计

### 4.1 双保险流程

1. `mysqldump` 导出 yiti 全库（结构+数据）→ `backup/yiti_full_20260718.sql`（`backup/` 加入 `.gitignore`，不进 git）
2. 创建 `yiti_test` 库并导入该备份，得到与正式库一致的起点
3. 本次合并的**所有** DDL/DML 只在 `yiti_test` 执行；DDL 脚本落档 `docs/modules/red-engine-center/`（供未来应用回正式库）
4. 新增 `bootstrap/src/main/resources/application-remerge.yml`（datasource 指向 `yiti_test`，其余继承默认配置）；默认 `application.yml` 不改，master 与其他分支零影响
5. 验证通过后，由用户决定何时将 DDL 应用回 yiti 正式库（不在本次范围内）

### 4.2 新表清单（统一 `RE_` 前缀，基准：`red-engine-server/src/main/resources/db/schema.sql`）

| 新表 | 来源表 | 说明 |
|------|--------|------|
| `RE_PARTY_ORG` | `party_org` | 党组织树（党委/党总支/党支部），模块私有 |
| `RE_USER_PARTY_MAP` | （新增） | 平台用户 ↔ 党组织 + 党内角色映射 |
| `RE_SUBMIT` | `biz_submit` | 四大维度材料上报 |
| `RE_SUBMIT_FILE` | `biz_submit_file` | 上报附件业务关联（文件本体走平台 MinIO） |
| `RE_SCORE` | `biz_score` | 支部评分 |
| `RE_MEMBER_SCORE` | `biz_member_score` | 党员评分 |
| `RE_OVERDUE_DEDUCTION` | `biz_overdue_deduction` | 逾期扣分记录 |
| `RE_ANNUAL_RESULT` | `biz_annual_result` | 年度考核归档 |

注：SQLite 中的 `biz_review_queue` 为 mock 产物，Java `schema.sql` 无此表（沉浸式审核队列由 Java 服务动态计算）；实施阶段以 Java 代码为准，仅在确需持久化时才补建 `RE_REVIEW_QUEUE`。

**不迁移**：`sys_user` / `sys_role` / `sys_menu` / `sys_user_role` / `sys_role_menu`（由平台 `PT_*` 取代）；`sys_dict_type` / `sys_dict_data`（4 类 15 项字典迁入平台 `SYS_DICT` / `SYS_DICT_ITEM`）。

**附件存储**：改用平台 MinIO + `FILE_OBJECT` / `BIZ_FILE_REL` 统一文件服务，`RE_SUBMIT_FILE` 仅保留业务侧关联与元数据。

### 4.3 种子与演示数据

- **种子 SQL**（随正式上线脚本）：党组织树 10 个节点、字典 4 类 15 项、`PT_RESOURCE` 资源码、4 个党建角色及授权行
- **演示数据**（仅 `yiti_test`）：16 条上报、16 条评分、4 条逾期扣分，从 SQLite `red_engine.db` 转换导入，用作联调验收基准
- 红色引擎 3 个演示用户**不迁移**；联调时用平台账号绑定党内角色

## 5. 后端模块设计（red-engine-center）

### 5.1 移植范围

| 原 Controller | 处置 |
|---------------|------|
| `SubmitController` / `ReviewController` / `CockpitController` / `ExportController` / `FileController` | 移植并升级（业务核心） |
| `OrgController` | 保留并改造为党组织管理（操作 `RE_PARTY_ORG`）|
| `AuthController` / `UserController` / `RoleController` / `MenuController` / `DictController` | **删除**，由平台既有能力取代 |

### 5.2 升级适配

- Java 8 → 17、Spring Boot 2.7 → 3.2.3（javax → jakarta）
- 数据访问全部走 MyBatis-Plus（Mapper `extends BaseMapper<T>`，实体 `@TableName`/`@TableId`，遵守平台 MP 红线）
- 响应模型：`Result<T>` / `PageResult` 替换为平台统一响应模型（`docs/common-dev-guide.md`）
- 包结构按平台规范：`api/`（`*Api`/`*QueryApi` + dto）、`controller/`、`facade/`、`service/`、`mapper/`、`entity/`、`config/`
- 所有 Service 类与 public 方法补注释；接口记录入参/出参、traceId、耗时

### 5.3 审核流（决策点，已确认）

保留红色引擎自有**两级审核状态机**（报送员提交 → 支部审核员审核 → 组织审核员终审），以 `RE_SUBMIT` 状态字段流转实现，**不接 Flowable / workflow-center**，因此不占用 `business_key` / `BIZ_PROCESS_MAP`。未来如需统一审批中心再演进。

### 5.4 业务规则原样保留

- 评分总分 100：外联共建 35 / 业务提升 50 / 头雁与先锋 10 / 督导与总结 5
- 逾期扣分：迟 1 天 −1 分；≥3 天该项清零（`RE_OVERDUE_DEDUCTION`）
- 预警阈值：总分 <60 红牌；60 ≤ 总分 <75 黄牌

## 6. 认证与权限

- **资源码**：新增 `redengine:*` 族并全量登记 `PT_RESOURCE`，Controller 用 `@BizAuth` 声明。初拟：`redengine:submit:view` / `redengine:submit:add` / `redengine:review:branch` / `redengine:review:org` / `redengine:cockpit:view` / `redengine:export` / `redengine:partyorg:manage`（实施时按接口细化）
- **角色**：`PT_ROLE` 新增 4 个党建角色——党建组织审核员、党建支部审核员、党建支部书记、党建报送员；原"系统管理员"能力并入平台 SYS_ADMIN。**按既有 SOP 为新资源码补 R_ADMIN 授权行**（SYS_ADMIN 不自动绕过 BizScope）
- **数据范围**：党组织维度隔离在模块内实现——经 `RE_USER_PARTY_MAP` 取用户所属党组织，按党组织子树过滤（支部级只见本支部，组织审核员见全行）；不复用平台行政组织 DATA_SCOPE
- **登录**：统一平台 `PT_USER` + session；无红色引擎独立账号

## 7. 前端集成（xanzc_frontend）

- 18 个视图迁入 `src/views/redengine/**`，路由挂 `/redengine/**`
- **登录页**：`LoginView.vue` 迁为 `/redengine/login`，外观样式原样保留，表单提交改调平台 session 登录接口；已登录用户访问 `/redengine/**` 免登录（同一 session）
- **风格隔离**：新增 `RedEngineLayout` 独立布局（红色主题、原侧边栏/顶栏），样式 scoped + `re-` 类名前缀，不污染平台全局样式；平台主布局不包裹红色引擎页面
- **请求/权限**：废弃 Axios JWT 拦截器与 localStorage token，统一用平台 request 封装（cookie session、8090 `/api` 代理）；`v-permission` 与菜单过滤改由平台权限码驱动
- 平台门户加入口（菜单/快捷方式）跳转 `/redengine`
- `red-engine-web/` 原工程随 `redengine/` 目录一并不入 git

## 8. 测试与验收

- **TDD 红线**：每个移植接口先写失败测试再实现；单测 `*Test.java`（surefire），集成 `*IT.java`（failsafe，跑 `remerge` profile / `yiti_test` 库）
- 前端：登录跳转、权限菜单过滤、上报-审核流转补 Vitest 组件测试；Playwright 联调主流程（`login?normal`，admin/123456）
- **验收基准**：用 `yiti_test` 中 16 条演示上报数据回放"报送 → 支部审核 → 组织终审 → 评分 → 驾驶舱/红黄牌"全链路，与原系统行为对照
- **回归**：`mvn clean install` + bootstrap 全量测试，确认既有 9 模块无回归；跨模块改动后先 install 再测（stale jar 规则）

## 9. 范围外（明确不做）

- 不将 DDL 应用到 yiti 正式库（验证通过后由用户另行决定）
- 不迁移红色引擎演示用户账号
- 不接 Flowable / 不生成 `BIZ_PROCESS_MAP` 记录
- 不改平台既有 9 模块的业务逻辑（仅门户加入口、权限表加数据）
- 报表分析中心不为红色引擎新增报表
