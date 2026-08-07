# red-engine-center/ CLAUDE.md

本文件为 `red-engine-center` 模块提供上下文说明。

## 模块概述

**red-engine-center** 是红色引擎党建管理中心，为银行分行提供党组织树管理、四大维度材料上报、两级审核评分、逾期扣分、驾驶舱/红黄牌预警、年度归档、数据导出等党建业务能力。

本模块由独立的 `redengine` 系统（Spring Boot 2.7 + Java 8 + Vue2，源码只读参照目录 `redengine/`，不进 git）于 **2026-07-18** 整体移植合并入平台（任务台账与权威 spec 见文末「开发参考」）。

**基础包名**: `com.bank.branch.platform.redengine`
**Maven 坐标**: `com.bank.branch.platform:red-engine-center`

**定位**: 核心域（党建垂直业务），审核状态机自管，**不接 Flowable**，不持有 `business_key`/`BIZ_PROCESS_MAP`。当前**无对外 `*Api`/`*QueryApi` 接口**（`api/` 包下只有 `dto/`，尚无跨模块被依赖需求）——若未来其他模块需要查询党建数据，须按平台红线新增 `*QueryApi`，禁止直接依赖本模块 `mapper`/`entity`。

## 依赖关系

- **依赖**: `common-web`/`common-trace`/`common-security`/`common-aop`/`common-db`、`auth-permission-center`（`CurrentUserApi`）、`system-governance-center`（`FileApi` 附件绑定）
- **不依赖**: `workflow-center`（两级审核状态机自管，不接 Flowable）、`customer-marketing-center`、`business-application-center`、`performance-engine-center`
- **被依赖**: 无（暂无对外 `*Api`，不被其他业务模块依赖）

## 包结构

```
src/main/java/com/bank/branch/platform/redengine/
├── api/dto/           # 跨端点共用 DTO（无 *Api/*QueryApi 接口，见「模块概述」）
│   ├── RePartyOrgTreeDTO / ReUserPartyMapDTO
│   ├── ReSubmitCreateReqDTO
│   ├── ReReviewApproveReqDTO / ReReviewRejectReqDTO
│   ├── ReCockpitOverviewDTO / ReRankingItemDTO / ReOverdueItemDTO / ReWarningItemDTO / ReOverdueExecuteReqDTO
│   └── ReSubmitExportRow / ReScoreExportRow（EasyExcel 行模型）
├── config/
│   └── RedEngineMyBatisConfig.java   # Mapper 扫描
├── controller/        # REST 端点清单见下方「REST 端点与 PT_RESOURCE 契约」
│   ├── ReOrgController          # 党组织管理
│   ├── ReUserPartyMapController  # 用户党组织映射
│   ├── ReSubmitController       # 材料上报
│   ├── ReReviewController       # 两级审核
│   ├── ReCockpitController      # 驾驶舱/预警/归档
│   └── ReExportController       # 数据导出
├── entity/            # 均 @Data + @TableName + @TableId(IdType.AUTO)
├── mapper/            # 全部 extends BaseMapper<T>，无 XML
└── service/           # 贫血模型，无 facade 层——本模块无对外 Api
    ├── RePartyOrgService / ReUserPartyMapService / ReSubmitService
    ├── ReReviewService / ReCockpitService / ReExportService

src/test/java/com/bank/branch/platform/redengine/
├── support/           # RedEngineTestApp + RedEngineMapperTestBase（同构复制 performance-engine-center 的隔离测试基座模式）
├── mapper/            # RePartyOrgMapperIT（failsafe，连 onepl_test_bootstrap）
└── service/           # 6 个纯 Mockito *Test.java（surefire），合计 50 case 全绿
```

> bootstrap 侧另有 `bootstrap/src/test/java/com/bank/branch/platform/it/RedEngineSmokeIT.java`（`redengine-smoke` profile，激活真实 RBAC 鉴权链路，见「测试」节）。

## 数据库表（`RE_` 前缀，DDL：`docs/superpowers/sql/2026-07-18-redengine-tables.sql`）

| 表 | 实体 | 说明 |
|----|------|------|
| `RE_PARTY_ORG` | `RePartyOrg` | 党组织树（`org_level` 1=分行党委 2=党支部；`secretary_id VARCHAR(50)` 书记工号；无唯一键，靠 `parent_id` 递归组装） |
| `RE_USER_PARTY_MAP` | `ReUserPartyMap` | 用户 ↔ 党组织 + 党内角色映射（`user_id`/`party_org_id`/`party_role`，`uk_user` 唯一键，`bind()` upsert） |
| `RE_SUBMIT` | `ReSubmit` | 四大维度材料上报（`dimension`(dim1~4)/`item_code`/`max_score`/`status`，见「RE_SUBMIT 状态机」） |
| `RE_SUBMIT_FILE` | `ReSubmitFile` | 上报附件业务关联（`file_object_id` 对接 governance `FileApi`；**唯一无 `update_time` 列**的实体） |
| `RE_SCORE` | `ReScore` | 支部评分（`org_id`+`submit_id`+`item_code`+`score_year`，`base_score`/`deduction_score`/`final_score` 均 `DECIMAL(10,2)`） |
| `RE_MEMBER_SCORE` | `ReMemberScore` | 党员个人评分（`user_id String`，当前无 Controller/Service 引用，仅建表未落地） |
| `RE_OVERDUE_DEDUCTION` | `ReOverdueDeduction` | 逾期扣分记录（`executeOverdue` 落库，默认 5 分） |
| `RE_ANNUAL_RESULT` | `ReAnnualResult` | 年度考核归档（`dim1~4_score`+`total_score`+`final_score`(=total*0.4)+`is_qualified`，`uk_org_year(org_id,eval_year)` 唯一键；**唯一无 `deleted` 列**的实体，不标 `@TableLogic`） |

用户主键字段（`secretary_id`/`submitter_id`/`reviewer_id`/`user_id`）全部为 `VARCHAR(50)` 工号，对齐 `PT_USER.USER_ID`——与源系统 `BIGINT` 自增主键的**有意差异**。

## REST 端点与 PT_RESOURCE 契约（`P_RE_*` 资源，权威清单/种子：`docs/superpowers/sql/2026-07-18-redengine-seed.sql`）

| # | RESOURCE_ID | METHOD | URL | 说明 | 授权角色（党建 4 角色 + SYS_ADMIN 恒全通） | 备注 |
|---|---|---|---|---|---|---|
| 1 | `P_RE_ORG_TREE` | GET | `/api/re/orgs/tree` | 党组织树 | 全部 4 角色 | |
| 2 | `P_RE_ORG_GET` | GET | `/api/re/orgs/*` | 党组织详情 | 全部 4 角色 | `MENU_RANK_NO=10`（其余 15 条=0），防止与 #1 同 METHOD 通配符抢先误匹配导致越权（`ResourceMatcher` 按 `MENU_RANK_NO ASC` 排序取首条） |
| 3 | `P_RE_ORG_ADD` | POST | `/api/re/orgs` | 新增党组织 | 仅 SYS_ADMIN | |
| 4 | `P_RE_ORG_UPD` | PUT | `/api/re/orgs/*` | 修改党组织 | 仅 SYS_ADMIN | |
| 5 | `P_RE_ORG_DEL` | DELETE | `/api/re/orgs/*` | 删除党组织（高危） | 仅 SYS_ADMIN | `reasonRequired=true`；存在子党组织抛 `RE-40002` |
| 6 | `P_RE_MAP_LIST` | GET | `/api/re/user-party-maps` | 映射列表 | 仅 SYS_ADMIN | |
| 7 | `P_RE_MAP_BIND` | POST | `/api/re/user-party-maps` | 绑定用户党组织（种子注释标"高危"） | 仅 SYS_ADMIN | `reasonRequired=false`（Task 6 评审裁决维持简报口径，非遗漏） |
| 8 | `P_RE_SUBMIT_ADD` | POST | `/api/re/submits` | 新建上报 | R_RE_REPORT, R_RE_SECR | 创建即 `status=1` |
| 9 | `P_RE_SUBMIT_MY` | GET | `/api/re/submits/my` | 我的上报分页 | R_RE_REPORT, R_RE_SECR, R_RE_BRREV | 不含 R_RE_ORGREV |
| 10 | `P_RE_SUBMIT_GET` | GET | `/api/re/submits/*` | 上报详情 | 全部 4 角色 | `MENU_RANK_NO=10`，同 #2 理由（与 #9 同 METHOD） |
| 11 | `P_RE_REVIEW_Q` | GET | `/api/re/reviews/**` | 待审队列 + 审核预览 | R_RE_BRREV, R_RE_ORGREV | Task 9 由字面量 `/api/re/reviews/queue` 放宽为通配符，复用覆盖 `GET .../queue` 与 `GET .../{id}/preview` 两端点，未新增资源 |
| 12 | `P_RE_REVIEW_APPR` | POST | `/api/re/reviews/*/approve` | 审核通过 + 评分 | R_RE_BRREV, R_RE_ORGREV | 超上限抛 `RE-40004` |
| 13 | `P_RE_REVIEW_REJ` | POST | `/api/re/reviews/*/reject` | 审核驳回 | R_RE_BRREV, R_RE_ORGREV | 无前置状态校验（源系统同款保真） |
| 14 | `P_RE_CKPT_VIEW` | GET | `/api/re/cockpit/**` | 驾驶舱只读（overview/ranking/overdue/warning/settlement，6 端点复用） | R_RE_ORGREV, R_RE_SECR | |
| 15 | `P_RE_CKPT_EXEC` | POST | `/api/re/cockpit/overdue/execute` | 执行逾期扣分（高危） | 仅 R_RE_ORGREV | `reasonRequired=true`；上报不存在抛 `RE-40005` |
| 16 | `P_RE_CKPT_ANNUAL` | POST | `/api/re/cockpit/archive/generate/*` | 生成年度归档（高危） | 仅 R_RE_ORGREV | `reasonRequired=true`（2026-07-19 修复，此前遗漏，见「技术债」④） |
| 17 | `P_RE_EXPORT` | GET | `/api/re/export/*` | 数据导出 | R_RE_ORGREV, R_RE_SECR | 仅 submit/score 两类，超上限抛 `RE-40007` |

上述 17 条均为 `SYS_CODE='RE'`、`ISMENU=0` 的 API 资源。2026-07-29 新增平台菜单资源
`M_RE_ENGINE`（`/redengine/dashboard`、`ISMENU=1`、顶层叶子），并把 17 条 `P_RE_*`
的 `PARENT_RESOURCE_ID` 对齐为该菜单；入口变更脚本为
`docs/superpowers/sql/2026-07-29-redengine-platform-menu-align.sql`。角色-资源绑定行数见下方「党建角色权限矩阵」。

## 4 党建角色权限矩阵

| ROLE_ID | ROLE_CODE | 中文名 | 资源数 |
|---|---|---|---|
| `RE_ROLE_1` | `R_RE_ORGREV` | 党建组织审核员 | 11（10 API + 1 菜单） |
| `RE_ROLE_2` | `R_RE_BRREV` | 党建支部审核员 | 8（7 API + 1 菜单） |
| `RE_ROLE_3` | `R_RE_SECR` | 党建支部书记 | 8（7 API + 1 菜单） |
| `RE_ROLE_4` | `R_RE_REPORT` | 党建报送员 | 6（5 API + 1 菜单） |

| 能力 | R_RE_REPORT 报送员 | R_RE_SECR 支部书记 | R_RE_BRREV 支部审核员 | R_RE_ORGREV 组织审核员 | SYS_ADMIN |
|---|---|---|---|---|---|
| 党组织树/详情查看 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 新增/修改/删除党组织 | ❌ | ❌ | ❌ | ❌ | ✅ |
| 用户-党组织映射管理 | ❌ | ❌ | ❌ | ❌ | ✅ |
| 新建上报 | ✅ | ✅ | ❌ | ❌ | ✅ |
| 我的上报分页 | ✅ | ✅ | ✅ | ❌ | ✅ |
| 上报详情 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 待审队列 + 预览 | ❌ | ❌ | ✅ | ✅ | ✅ |
| 审核通过/驳回 | ❌ | ❌ | ✅ | ✅ | ✅ |
| 驾驶舱只读 | ❌ | ✅ | ❌ | ✅ | ✅ |
| 执行逾期扣分（高危） | ❌ | ❌ | ❌ | ✅ | ✅ |
| 生成年度归档（高危） | ❌ | ❌ | ❌ | ✅ | ✅ |
| 数据导出 | ❌ | ✅ | ❌ | ✅ | ✅ |

数据范围：`PT_ROLE_BIZ_SCOPE.BIZ_TYPE='RED_ENGINE'`，4 党建角色 + SYS_ADMIN 共 5 行，`DATA_SCOPE='ALL'`（党组织维度隔离在模块内经 `RE_USER_PARTY_MAP` 自行实现，不复用平台行政组织 `DATA_SCOPE`）。

`ReUserPartyMap.partyRole` 四个字符串字面量（无字典/枚举校验）：`ORG_REVIEWER` / `BRANCH_REVIEWER` / `SECRETARY` / `REPORTER`，与上表角色概念一一对应但命名空间独立。

## RE_SUBMIT 状态机（四态）

```
创建 ──────────────────────────────▶ status=1（已提交，创建即此态，无草稿态入口）
                                        │
                        ┌───────────────┼───────────────┐
                        │ approve                        │ reject
                        ▼                                 ▼
              status=2（已通过）                 status=3（已驳回）
              + 落 RE_SCORE（超上限抛 RE-40004）    + 无前置状态校验（源系统同款，
              + reviewerId/reviewDate 回填           可对 2/3 态重复调用并覆盖，见技术债②）
```

- `status=0`（草稿）为 DDL 遗留默认值，**当前无任何代码路径可产生该状态**——`createSubmit` 落库直接写 `status=1`，源系统"先存草稿后提交"两段式语义已废弃（YAGNI，简报授权）。
- `approve`/`reject` 均**不校验**"仅 status=1 才可审核"（源系统同款保真，非遗漏，见技术债②）。

## 错误码（RE-40001..40007）

| 错误码 | 语义 | 抛出方 |
|---|---|---|
| `RE-40001` | 当前用户未绑定党组织，请联系管理员 | `ReUserPartyMapService.getRequiredPartyOrgId` / `ReSubmitService.createSubmit` |
| `RE-40002` | 存在下级党组织不可删除 | `RePartyOrgService.delete`（新增防呆，源系统无此校验） |
| `RE-40003` | 提交记录不存在 | `ReReviewService.approve` |
| `RE-40004` | 考核项累计得分已达上限（消息含具体已达分/上限/本次最多可计） | `ReReviewService.approve` |
| `RE-40005` | 上报记录不存在 | `ReCockpitService.executeOverdue`（源系统静默 `return false`，本次改抛异常加固） |
| `RE-40006` | 导出类型非法，仅支持 submit/score | `ReExportService.exportData` |
| `RE-40007` | 导出数据超过上限，请缩小范围 | `ReExportService.exportData`（简报初拟 `RE-40005`，因与 Task 10 冲突已纠偏为 `RE-40007`） |

裸字符串错误码，未建 `ReErrorCode` 枚举，`common-dev-guide.md` 附录 B 错误码前缀表未收录 `RE-` 前缀——见「技术债」⑤。

## 字典

`SYS_DICT`（拍平惯例，非 `SYS_DICT_ITEM` 两级设计，见「技术债」⑨），均 `RE_` 前缀命名空间：`RE_ORG_TYPE`/`RE_DIMENSION`/`RE_SUBMIT_STATUS`/`RE_ITEM_CODE`。前端未走 `useDict()` 的地方（如 `partyRole`）是因为该字段本身**不在**上述字典内，属硬编码字面量（见上文角色矩阵节）。

## 与源系统差异清单

| 项 | 源系统 | 本次平台实现 |
|---|---|---|
| 用户主键 | `BIGINT` 自增 | `VARCHAR(50)` 工号，对齐 `PT_USER.USER_ID` |
| 附件存储 | hutool 本地文件 + `/api/submit/{id}/files` 上传端点 | 平台 governance `FileApi`（华为云 OBS），前端直传拿 `fileObjectId` 后随创建请求体提交；源上传端点已裁剪 |
| 草稿态 | `createSubmit` 硬编码 `status=0`，前端从未真正使用两段式提交 | 废弃草稿语义，创建即 `status=1`（YAGNI） |
| 红黄牌阈值 | 项目文档口径"75 分"（弃用） | **以代码为准**：`final_score<60` 红牌，`60≤final_score<80` 黄牌 |
| 逾期规则 | 项目文档口径"迟 1 天 -1 分、≥3 天清零"（弃用，自动累进） | **以代码为准**：`submitDate+7 天` 且 `status∈{0,1}` 判定逾期，人工在预警池逐条调用执行、默认扣 5 分 |
| `orgId`/`submitterId` | 前端可传，源系统可被伪造越权 | 服务端从登录人 `empId` 经 `RE_USER_PARTY_MAP` 强制派生，修复越权隐患 |
| `reviewerId` | 无此字段 | 补齐，`approve`/`reject` 均回填当前审核人工号 |
| `generateAnnualResult` 维度聚合 | 死代码（`putIfAbsent` 只建键，从未写入维度分值，四维恒为 0） | 补全：`RE_SCORE` 关联 `RE_SUBMIT.dimension` 内存 join 分组求和 |
| `executeOverdue` 上报不存在 | 静默 `return false` | 改抛 `RE-40005`（平台惯例加固） |
| 导出 | hutool-poi，源码未见类型/行数上限 | EasyExcel，仅 submit/score 两类，`RE-40006`/`RE-40007`；表头字段逐字保真 |
| `OrgController.getChildren`/`generateReport` | 存在 | 未移植（无 `PT_RESOURCE` 注册 + 简报未列端点，YAGNI） |
| `secretaryName` 回填 | `PartyOrgServiceImpl` 反查 `SysUserMapper` | 未移植（简报依赖边界只声明 `RePartyOrgMapper`），`RePartyOrgTreeDTO` 仅带 `secretaryId` 工号 |
| 审核流程 | 无独立工作流引擎，Controller 内状态字段流转 | 保持自管两级审核状态机，**不接 Flowable**，不写 `BIZ_PROCESS_MAP` |
| 认证/权限体系 | 独立 `sys_user`/`sys_role`/JWT | 完全由平台 `PT_USER` + Session + RBAC 取代；红色引擎演示账号和独立登录页均不保留 |

## 测试

- **单元测试**：6 个纯 Mockito `*Test.java`（`RePartyOrgServiceTest` 8 / `ReUserPartyMapServiceTest` 5 / `ReSubmitServiceTest` 5 / `ReReviewServiceTest` 8 / `ReCockpitServiceTest` 17 / `ReExportServiceTest` 7），合计 **50 case 全绿**，均不连库
- **Controller 单元测试**（2026-07-19 新增，TDD 覆盖三处审计/校验缺口修复）：`ReOrgControllerTest`（9 case：deleteOrg 带/缺 reason、RE-40002 守卫不回归、addOrg/updateOrg 校验+树查询）/ `ReCockpitControllerTest`（3 case：generateAnnualResult 带/缺 reason）；均 `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())` 纯单元测试（同构 `workflow-center` `ProcessCommandControllerTest`/`auth-permission-center` `OrgControllerTest` 既有惯例），不连库、不起 Spring 容器
- **Mapper 集成测试**：`RePartyOrgMapperIT`（failsafe，`onepl_test_bootstrap`），基座 `RedEngineTestApp` + `RedEngineMapperTestBase` 同构复制 `performance-engine-center` 的隔离测试模式（`@ActiveProfiles("test")` + `@Transactional` + `@Rollback`）
- **bootstrap 冒烟 IT**：`RedEngineSmokeIT`（`redengine-smoke` profile，激活真实鉴权链路 `AuthenticationFilter`+`AuthorizationInterceptor`，而非 `test` profile 下被 `@Profile("!test")` 关闭的 `WebMvcAuthConfig`），3 case：无 session 401 / admin 登录 200+树 10 节点 / 无 `P_RE_*` 绑定角色 403，全部真实 RBAC 日志亲验
- **Playwright 全链路（2026-07-18 历史验收）**：原独立登录页 → 上报 → 审核通过 → 驾驶舱 → 预警池 → 导出 xlsx，权限矩阵三项（报送员 403 / 无映射 `RE-40001` / admin 全通）均实测通过。2026-07-29 起入口改为平台 `/login` → 动态菜单“红色引擎”，独立登录页已删除。
- **演示数据**（仅 `yiti_test`，`docs/superpowers/sql/` 2026-07-18 demo 脚本，显式主键 + `INSERT IGNORE` 幂等）：16 条上报 + 16 条评分 + 4 条逾期扣分，4 支部（org 2-5）各 4 项 × 15 分 = 60 分，`dim_clean`/`CLEAN_PROJECT` 源编码不翻译到平台 `dim1~4`/`RE_ITEM_CODE`（仅用于验证列表/统计出数，年度归档聚合会丢弃这批数据，属预期）

## 技术债与已知限制

1. **`generateAnnualResult` 不按 `year` 过滤聚合范围**（源系统同款）：`evalYear` 只定落库键，不限定 `RE_SCORE` 聚合范围，长期运行多年数据混算风险，需产品决策补 `scoreYear` 过滤。
2. **重复审核产生 `RE_SCORE` 重复/滞留记录**（源系统同款保真）：`approve`/`reject` 无前置状态校验，对同一 `submitId` 反复"通过→驳回→再通过"会重复插入评分行；禁止重复审核会破坏改判能力，故未加校验，留产品决策。
3. **导出 10000 行同步上限偏离平台"5000 行必须异步"MUST 线**（`docs/export-spec-coverage.md` 全局规则）：党建业务量级小 + 源系统无上限 + 迁移期过渡防呆，`ResponseEntity<byte[]>` 全量物化为全平台唯一此类导出实现，其余模块导出端点均为流式/异步。
4. **能力缺口**（均经审查核实属实，非遗漏）：`RE_SCORE` 无按 `submitId` 的评分明细查询端点（前端得分列显示"-"）；`ReRankingItemDTO`/`ReOverdueItemDTO` 无组织名字段（前端需另拉 `orgTree` 拍平匹配）；无按 `submitId` 查附件列表端点。
   **以下三处审计/校验缺口已于 2026-07-19 修复（TDD，含前端配套）**，不再是遗留缺口，保留记录供追溯：
   - `ReOrgController.deleteOrg`（高危）此前无 `reason` 入参通道（`@AuditLog(reasonRequired=true)` 依赖 DTO `@NotBlank reason` 字段，该端点仅 `@PathVariable id` 无处挂载）——已新增 `ReOrgDeleteReqDTO`（`reason` `@NotBlank`）+ `@Valid @RequestBody`，前端 `OrgManageView.vue` 删除交互改为「删除原因」必填弹窗。
   - `ReCockpitController.generateAnnualResult`（高危）此前 `@AuditLog` 未设 `reasonRequired=true`——已补齐 `reasonRequired=true` + 新增 `ReAnnualGenerateReqDTO`（`reason` `@NotBlank`），前端 `ArchiveView.vue`「生成年度报告」改为「生成原因」必填弹窗。
   - `ReOrgController.addOrg`/`updateOrg` 此前直接接收裸实体 `RePartyOrg` 且无 `@Valid`——已改为独立 `RePartyOrgReqDTO`（`orgName` `@NotBlank`，对齐 DDL `NOT NULL`）+ `@Valid`，Controller 内部转换为实体后再调用 Service，URL/HTTP 方法/字段名均未变。
5. **`RE-4xxxx` 为裸字符串错误码**：未建 `ReErrorCode` 枚举类，`docs/common-dev-guide.md` 附录 B 模块错误码前缀表未收录 `RE-` 前缀，后续错误码增多时应统一收敛。
6. **前端 `canSee` 通配符前缀匹配是近似算法**：「年度考核归档」菜单项 `res` 复用较宽的 `P_RE_CKPT_VIEW`（而非更窄的 `P_RE_CKPT_ANNUAL`），当前因两资源总绑定同一批角色而"巧合正确"，非真 AntPath 反向匹配；后续角色绑定分化时需换成显式资源映射。
7. **OBS 域名在开发沙箱 DNS 不可达**（`GOV-50001`）：附件上传联调在当前开发环境不可用，生产内网可用；`JointView` 未做"附件失败仍放行提交"的降级（安全默认：附件失败=提交失败），已记录不修。
8. **`RestEndpointInventoryIT` 误报**：该审计工具只静态比对 `docs/schema/seed-v1.sql` 基线且不做 AntPath 通配符展开，本模块 17 条 `P_RE_*` 资源已正确注册但被误计入"未登记"差值（BASE=195，当前=218，差值 23 恰为 RE 端点，含 2 条通配符资源覆盖多端点），属该工具既有方法论盲区，非真实注册缺口。
9. **`SYS_DICT_ITEM` 平台无代码读取通道**：`system-governance-center` 的 `DictApi`/`SysDict` 只读写 `SYS_DICT` 单表，本模块字典按平台拍平惯例落 `SYS_DICT`（`RE_` 前缀命名空间），未使用 DDL 语义上"预留"的两级 `SYS_DICT_ITEM` 设计。
10. **`approve` 评分上限校验并发 TOCTOU 可绕过 `maxScore`**（终审 F-1，`.superpowers/sdd/final-review.md` §3，Minor/非阻断）：`ReReviewService.insertScoreWithLimitCheck`（约 L765-798）走"`selectList` 内存求和 → 校验 → `insert`"，`@Transactional` 不加读锁/唯一约束。两个审核员（或前端重复提交）几乎同时对同一党组织、同一 `itemCode`、同一年度的上报调用 `approve`，各自事务在 `selectList` 阶段读到相同历史累计值，各自判定 `existingSum+score ≤ maxScore` 通过，各自落一行 `RE_SCORE`，最终累计可突破 `maxScore`。终审评估为 Minor：党建业务量级为个位数支部、审核为人工离散动作，真实并发概率极低，且已有 `@AuditLog` 留痕；本质是与技术债②"重复审核"同源的并发变体（技术债②接受"保留改判能力"的产品决策）。若未来需强一致，可对 `(org_id, item_code, score_year)` 加唯一约束或改 `SELECT ... FOR UPDATE` 行锁。
11. **驾驶舱/导出为全局口径（`DATA_SCOPE='ALL'`），支部书记可见并导出全行数据**（终审 F-3，`.superpowers/sdd/final-review.md` §3，Info/设计如此）：`ReCockpitService`（ranking/warning/overview 等）与 `ReExportService`（全量导出）均无 per-org 过滤，模块内 org 隔离仅作用于"我的上报/创建"；`R_RE_SECR`（支部书记）按角色矩阵获授"驾驶舱只读 + 数据导出"，因此可看到并导出全行各支部的评分数据，而非仅本支部。这是 `PT_ROLE_BIZ_SCOPE.BIZ_TYPE='RED_ENGINE'` 统一 `DATA_SCOPE='ALL'` + "全局数据驾驶舱"产品定位的显式决策，非缺陷；若业务上要求收敛到本支部口径，需另立任务改造 `ReCockpitService`/`ReExportService` 按登录人 `RE_USER_PARTY_MAP` 的 `partyOrgId` 过滤。

## 开发参考

- Spec：`docs/superpowers/specs/2026-07-18-redengine-merge-design.md`
- 实施任务台账：`.superpowers/sdd/task-0-brief.md` ~ `task-18-brief.md`（含各任务 report/review）
- DDL：`docs/superpowers/sql/2026-07-18-redengine-tables.sql`
- 权限/字典/党组织种子：`docs/superpowers/sql/2026-07-18-redengine-seed.sql`
- 平台菜单入口对齐：`docs/superpowers/sql/2026-07-29-redengine-platform-menu-align.sql`
- `yiti` 正式同步编排：`docs/superpowers/sql/2026-07-29-redengine-sync-yiti.sql`（先备份；
  不包含 `yiti_test` 专用演示业务数据）
- 共享开发规范：`docs/common-dev-guide.md`
- 前端：`xanzc_frontend/src/views/redengine/**`（平台菜单进入 `/redengine/dashboard`，复用平台登录态；独立 `RedEngineLayout` 红色主题布局，`re-` 类名前缀隔离平台全局样式）
