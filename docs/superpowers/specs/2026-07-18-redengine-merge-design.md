# 红色引擎（redengine）并入 Branch Platform 设计规格

- **日期**: 2026-07-18
- **分支**: `feature/redengine-merge`（本次合并全部改动所在分支，不动 master）
- **状态**: 设计已获用户批准（含"首期不接 Flowable"决策点）

> **2026-07-29 后续变更**：原设计中“保留红色引擎登录页、通过独立入口进入”的要求已被取消。
> 当前方案以本文 §10 为准：红色引擎作为 Branch Platform 动态菜单进入，复用平台登录态，
> 业务页面继续使用原 `RedEngineLayout` 与红色主题。
>
> **2026-07-29 数据库上线补记**：用户已另行明确授权将相关数据库调整同步到 `yiti`。
> 原文“仅在 `yiti_test` 执行”的限制仍是 2026-07-18 初次合并阶段的历史约束；本次正式同步
> 通过 `2026-07-29-redengine-sync-yiti.sql` 编排执行，明确排除 `yiti_test` 专用演示业务数据。

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

## 实现勘误与决策记录（Task 18）

Task 0-17（`.superpowers/sdd/task-0-brief.md` ~ `task-17d-report.md`）实施完成后，以下内容与本设计规格原文存在偏差或补充决策，以 **Java 源码 / 已落库种子为权威口径**，本节记录偏差并声明本规格对应条款视为已被下述内容更新。模块级权威详情见 `red-engine-center/CLAUDE.md`（8 表清单、17 条 `PT_RESOURCE` 契约、角色矩阵、状态机、错误码、差异清单、技术债，本节不重复列出全部细节，仅摘要勘误点与索引）。

### 9.1 业务口径勘误（§5.4 弃用，以代码为准）

- **红黄牌阈值**：本规格 §5.4 原文"总分 <60 红牌；60 ≤ 总分 <75 黄牌"中的 **75 已弃用**。`ReCockpitService`（移植自 `BizCockpitServiceImpl.getYellowWarning`）实际代码阈值为 `final_score<60` 红牌、`60≤final_score<80` 黄牌，按代码原样移植，未按本规格文档口径实现。
- **逾期扣分规则**：本规格 §5.4 原文"迟 1 天 −1 分；≥3 天该项清零"的**自动累进扣分口径已弃用**。`BizCockpitServiceImpl` 实际代码是"逾期列表 = `status∈{0,1}` 且 `submitDate+7 天`早于今天的记录，由人工在预警池逐条调用 `executeOverdue` 执行扣分，默认扣 5 分"，本次移植原样保真代码行为，不实现文档描述的自动累进逻辑。

### 9.2 与源系统的实现差异（§4.2/§5.1/§5.2/§6 补充）

| 条款 | 规格原文 | 实际实现 |
|---|---|---|
| 用户主键（§4.2） | 未明确类型 | `secretary_id`/`submitter_id`/`reviewer_id`/`user_id` 一律 `VARCHAR(50)` 工号（非源系统 `BIGINT`），对齐 `PT_USER.USER_ID` |
| 附件存储（§4.2） | "改用平台 MinIO" | 实际改走 `system-governance-center` 既有 `FileApi`（底层华为云 OBS，非 MinIO），`RE_SUBMIT_FILE` 仅存 `file_object_id` 业务关联；源 `uploadFile` 端点已裁剪，前端直传获取 `fileObjectId` 后随创建请求体提交 |
| 草稿态（§5.1 移植范围） | 未明确处置 | 明确废弃：`createSubmit` 创建即 `status=1`（已提交），不提供两段式草稿接口（源前端本就从未真正使用草稿态，YAGNI 裁剪） |
| `orgId`/`submitterId`（§6 认证权限） | 未提及越权风险 | 服务端从登录人 `empId` 经 `RE_USER_PARTY_MAP` 强制派生，不再由前端 DTO 传入——修复源系统"前端可伪造挂靠/冒名提交"的越权隐患（正向改进） |
| `reviewerId` | 源系统无此字段 | 补齐，`approve`/`reject` 均回填当前审核人工号 |
| `generateAnnualResult` 维度聚合 | 未提及源码缺陷 | 源码 `putIfAbsent` 死代码只建键从未写入维度分值；本次补全为 `RE_SCORE` 关联 `RE_SUBMIT.dimension` 内存 join 分组求和 |
| `executeOverdue` 上报不存在 | 未提及 | 源码静默 `return false`；本次改抛 `RE-40005`（平台惯例加固） |
| 数据导出（§5.1） | 未提及技术选型 | hutool-poi → EasyExcel；仅 `submit`/`score` 两类；`RE-40006`（type 非法）/`RE-40007`（超上限，初拟 `RE-40005` 因与 Task10 冲突纠偏） |
| `OrgController.getChildren`/`generateReport`（§5.1） | 隐含全量移植 | 未移植（无 `PT_RESOURCE` 注册 + 任务简报未列端点，YAGNI 裁剪） |
| `secretaryName` 回填 | 未提及 | 未移植（依赖边界只声明 `RePartyOrgMapper`，未含 `UserApi` 跨模块查询），`RePartyOrgTreeDTO` 仅带 `secretaryId` 工号 |

### 9.3 权限种子实现细节（§6 补充，非规格原文层面决策）

- `P_RE_REVIEW_Q` 的 `RESOURCE_URL` 由 Task 4 初版字面量 `/api/re/reviews/queue` 于 Task 9 放宽为通配符 `/api/re/reviews/**`，复用覆盖"待审队列"与"审核预览"两个 GET 端点，17 条资源总数不变。
- `P_RE_ORG_GET`/`P_RE_SUBMIT_GET` 两条通配符资源设 `MENU_RANK_NO=10`（其余 15 条为默认 0），修复 `ResourceMatcher` 按 `MENU_RANK_NO ASC, RESOURCE_ID ASC` 排序时通配符资源字母序抢先于同 METHOD 字面量资源（`P_RE_ORG_TREE`/`P_RE_SUBMIT_MY`）被误匹配、导致 `R_RE_ORGREV` 越权访问"我的上报分页"的 Critical 缺陷（Task 4 审查发现并修复，详见 `task-4-review.md`）。

### 9.4 技术债与已知限制（索引，详情见 `red-engine-center/CLAUDE.md` §技术债）

1. `generateAnnualResult` 不按 `year` 过滤聚合范围（源系统同款），多年数据混算风险，待产品决策。
2. 重复审核（通过→驳回→再通过）会产生 `RE_SCORE` 重复/滞留记录（源系统同款保真），禁止重复审核会破坏改判能力，留产品决策。
3. 导出 10000 行同步上限偏离平台"5000 行必须异步"MUST 线（迁移期过渡决策，见 `docs/export-spec-coverage.md`）。
4. 能力缺口：`RE_SCORE` 无按 `submitId` 明细查询端点；`ReRankingItemDTO`/`ReOverdueItemDTO` 无组织名字段；无按 `submitId` 查附件端点；`deleteOrg` 无 `reason` 入参通道。
5. `RE-4xxxx` 为裸字符串错误码，`ReErrorCode` 枚举未建，`common-dev-guide.md` 附录 B 错误码前缀表未收录 `RE-` 前缀。
6. 前端 `canSee` 通配符前缀匹配是近似算法（当前角色绑定巧合正确，非真 AntPath 反向匹配）。
7. OBS 域名在开发沙箱 DNS 不可达（`GOV-50001`），附件上传联调不可用，生产内网可用。
8. `RestEndpointInventoryIT` 只静态比对 `docs/schema/seed-v1.sql` 基线且不做 AntPath 通配符展开，本模块 17 条资源已正确注册但被误报为 23 条差值（BASE=195，当前=218），属该审计工具既有方法论盲区。
9. `SYS_DICT_ITEM` 平台无代码读取通道，本模块字典按平台拍平惯例落 `SYS_DICT`（`RE_` 前缀，4 类 18 项），未使用 DDL 语义预留的两级设计。

## 10. 2026-07-29 平台菜单入口变更

本节覆盖本文 §1 目标 1、§2“前端形态”、§3 前端架构图、§7“登录页/平台门户入口”及
§8 Playwright 登录入口的旧设计，其他后端业务、权限、数据与页面风格要求不变。

- 平台 `PT_RESOURCE` 新增顶层叶子菜单 `M_RE_ENGINE`，URL 为 `/redengine/dashboard`；
  四个党建角色与 `SYS_ADMIN` 均绑定该菜单。
- 17 条 `P_RE_*` API 资源的 `PARENT_RESOURCE_ID` 对齐为 `M_RE_ENGINE`，使平台权限配置分配
  菜单时自动联动接口权限。
- 删除 `/redengine/login` 路由及 `LoginView.vue`；未登录访问 `/redengine/**` 统一由全局
  路由守卫跳转平台 `/login`，不再存在红色引擎独立账号入口。
- `/redengine/**` 仍使用顶层 `RedEngineLayout`，不嵌入 `DefaultLayout`，避免平台侧栏与红色
  引擎侧栏叠加；原红色主题、页面结构、内部菜单和业务视图样式不变。
- 红色引擎顶栏“退出”销毁平台 Session 并整页跳转 `/login`，与平台 `AppHeader` 行为一致。
- 数据库增量对齐脚本为
  `docs/superpowers/sql/2026-07-29-redengine-platform-menu-align.sql`；历史
  `2026-07-18-redengine-seed.sql` 保持原貌，不原地改写。
- `yiti` 正式同步统一通过
  `docs/superpowers/sql/2026-07-29-redengine-sync-yiti.sql` 执行，顺序为“8 张 `RE_*`
  基础表 → 权限/字典/党组织正式种子 → 平台菜单对齐”。2026-07-29 已在完整备份后执行并
  重复执行验证幂等：落库 17 条 API 资源、1 条菜单、4 个党建角色、5 条业务范围、18 条
  字典、10 条党组织；`RE_SUBMIT`/`RE_SCORE`/`RE_ANNUAL_RESULT` 均为 0，未同步演示业务数据。
