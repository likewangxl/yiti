# 红色引擎（党建管理）— 对外 API 契约

> 版本：v1.0
> 最后更新：2026-07-19
> 模块编码：red-engine-center
> 本文以代码为准核实：本模块**是否**对外暴露 `*Api`/`*QueryApi`、本模块消费上游哪些 `*Api`、字典/事件约定
> 结论已 grep `red-engine-center/src/main/java` 全量核实，无凭空补写

---

## 0. 契约原则（承袭平台通用规则）

- **唯一入口**：跨模块访问只能通过 `com.bank.branch.platform.<module>.api.*` 包下的接口，禁止直接依赖 `mapper`/`entity`/`service`/`serviceImpl`
- **只读为主**：查询类 API 用 `*QueryApi` 命名；写入类 API 用 `*Api` 命名
- **DTO 传输**：跨模块返回对象必须是 `api/dto` 下的 DTO，禁止暴露 Entity（**本模块内部 REST 端点未完全遵守此规则**，见 §3 备注）

---

## 1. 结论：本模块对外暴露的 `*Api`

**无。** 本模块 `api/` 包下**只有 `dto/` 子包，没有任何 `*Api`/`*QueryApi` 接口文件**：

```
$ find red-engine-center/src/main/java/com/bank/branch/platform/redengine/api -type f
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/RePartyOrgTreeDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReUserPartyMapDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReSubmitCreateReqDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReReviewApproveReqDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReReviewRejectReqDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReCockpitOverviewDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReRankingItemDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReOverdueItemDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReWarningItemDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReOverdueExecuteReqDTO.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReSubmitExportRow.java
red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReScoreExportRow.java
```

这些 DTO 都只是 REST 端点的请求/响应报文载体（供 `03-接口设计与报文.md` 描述的 REST Controller 使用），**不构成跨模块 Java 契约**——没有任何其他业务模块 `import` 本模块的 `api.dto.*` 或依赖本模块的 `mapper`/`entity`（`red-engine-center/CLAUDE.md`「模块概述」「依赖关系」节已如此定性，本文再次以 grep 独立核实一致）。

**原因**：本模块是党建垂直业务的核心域，当前无其他模块需要查询党建数据的产品需求（YAGNI）。红黄牌预警、驾驶舱统计、上报/评分等能力均只服务本模块自有的前端页面（`xanzc_frontend/src/views/redengine/**`）。

**若未来其他模块需要查询党建数据**（例如 `report-analytics-center` 需要在通用报表里聚合党建考核结果），须按平台红线新增 `*QueryApi` 接口 + DTO，**禁止**直接依赖本模块 `mapper`/`entity`。新增时的候选查询能力（供未来参考，当前均未实现）：
- 按党组织 ID 查询年度归档结果（`RE_ANNUAL_RESULT`）
- 按用户工号查询归属党组织（`RE_USER_PARTY_MAP`，当前仅 `ReUserPartyMapService.getRequiredPartyOrgId` 模块内私有方法）

---

## 2. 本模块消费的上游 `*Api`

以下结论均由 `grep -rn "^import com\.bank\.branch\.platform\..*\.api\." red-engine-center/src/main/java` 核实（排除本模块自身 `redengine.api` 包）：

### 2.1 `CurrentUserApi`（`auth-permission-center`）

```java
import com.bank.branch.platform.auth.api.CurrentUserApi;
```

**使用位置**：
- `ReSubmitController`（`private final CurrentUserApi currentUserApi;`）——`createSubmit`/`getMySubmits` 取 `currentUserApi.getCurrentEmpId()` 作为提交人/查询过滤主体
- `ReReviewController`（同上）——`approve`/`reject` 取当前登录审核人工号回填 `RE_SUBMIT.reviewerId`

**用到的方法**：仅 `getCurrentEmpId()`（`CurrentUserApi` 接口另有 `getCurrentUserContext`/`getCurrentOrgCode`/`getCurrentRoleIds`/`getCurrentRoleCodes`/`getCurrentCandidateGroupKeys`/`isSystemAdmin`，本模块均未调用）。

**未使用 `BizScopeApi`**：本模块数据范围统一走 `PT_ROLE_BIZ_SCOPE.BIZ_TYPE='RED_ENGINE'` 且 `DATA_SCOPE='ALL'`（5 角色恒全量），组织维度隔离靠模块内 `RE_USER_PARTY_MAP` 自行实现（`ReUserPartyMapService.getRequiredPartyOrgId`），未接入平台 `BizScopeApi`/`DataScopeContext` 的机构子树等通用数据范围能力（`grep BizScopeApi red-engine-center/src/main/java` 结果为空）。

### 2.2 `FileApi`（`system-governance-center`）

```java
import com.bank.branch.platform.governance.api.FileApi;
```

**使用位置**：`ReSubmitService`（`private final FileApi fileApi;`）

**用到的方法**：`bindFile(String bizType, String bizId, String fileObjectId, String fileRole)`——`createSubmit` 内对 `req.getFileObjectIds()` 逐个调用，把材料上报的附件（前端已通过 governance `POST /api/files/upload` 直传拿到 `fileObjectId`）与业务对象（上报记录）建立关联登记，同时写本模块自有的 `RE_SUBMIT_FILE` 表落业务侧元数据。**未调用** `FileApi.upload`/`getFileContent`/`getDownloadUrl`/`listBizFiles`/`deleteFile`/`getFileName(s)`/`getFileSizes` 等其余方法——上传动作完全由前端直连 governance 端点完成，本模块服务端只做"绑定登记"这一步。

### 2.3 未消费的其他上游 `*Api`

以下平台常见 `*Api` **未被本模块 import/使用**（grep 确认为空）：
- `DictApi`（`system-governance-center`）——本模块字典（`RE_ORG_TYPE`/`RE_DIMENSION`/`RE_SUBMIT_STATUS`/`RE_ITEM_CODE`）走的是种子 SQL 直接落 `SYS_DICT` 表，前端读取走通用字典查询端点，**Java 代码层面本模块无需也未调用 `DictApi`**（见 §3）
- `BizScopeApi`（`auth-permission-center`）——见 2.1 说明
- `WorkflowApi`/`WorkflowQueryApi`（`workflow-center`）——本模块**不接 Flowable**，审核流是自管两级状态机（`RE_SUBMIT.status` 字段流转），不依赖 `workflow-center` 模块（根 CLAUDE.md 模块依赖图已明确标注"不依赖 workflow-center"）

---

## 3. 字典约定（`SYS_DICT`，`RE_` 前缀命名空间）

红色引擎字典**全部拍平落 `SYS_DICT` 单表**（非 `SYS_DICT_ITEM` 两级设计），沿用平台既有惯例，与本模块无 `DictApi` 调用不矛盾——字典查询走的是 `system-governance-center` 已有的通用字典查询 REST 端点（前端按 `dict_type` 查询），本模块 Java 代码不需要专门再封装一层调用。

| `dict_type` | 中文名 | 项数 | 说明 |
|---|---|---|---|
| `RE_ORG_TYPE` | 组织类型 | 3 | 经营单位/营销部室/中后台部门 |
| `RE_DIMENSION` | 考核维度 | 4 | dim1(党建联建 35分)/dim2(业务提升 50分)/dim3(头雁与先锋 10分)/dim4(督导与工作总结 5分) |
| `RE_SUBMIT_STATUS` | 上报状态 | 3 | pending/approved/rejected（**注意**：字典项值为英文小写字面量，与 `RE_SUBMIT.status` 实际的整型 `0/1/2/3` 编码不是同一套值域，前端展示需自行映射，非直接可用的 `status` 整型翻译表） |
| `RE_ITEM_CODE` | 考核项编码 | 8 | 1.1/1.2/1.3/2.1/2.2/4.1/4.2/sup，`remark` 字段携带各项满分上限提示（如"最高35分"） |

`ReUserPartyMap.partyRole` 四个字符串字面量（`ORG_REVIEWER`/`BRANCH_REVIEWER`/`SECRETARY`/`REPORTER`）**不在**上述任何字典内，属硬编码字面量，无字典/枚举校验，也未走前端 `useDict()`。

字典种子权威来源：`docs/superpowers/sql/2026-07-18-redengine-seed.sql` 第 6 段（`governance` 模块的 `DictApi`/`SysDict` 实体只读写 `SYS_DICT` 单表，`SYS_DICT_ITEM` 全平台未被任何代码读取，故第 6 段已改为拍平写法，不落 `SYS_DICT_ITEM`，详见该脚本文件头「修复记录」）。

---

## 4. 发布的 Spring 领域事件

**无。** `grep -rn "ApplicationEventPublisher\|publishEvent\|DomainEvent" red-engine-center/src/main/java` 结果为空——本模块**未发布任何 Spring 领域事件**，与 `customer-marketing-center` 等模块（`customer.lead.approved.v1` 等 `.v1` 命名的事件族）的事件发布模式不同。

**原因**：本模块所有状态流转（`RE_SUBMIT` 四态机、`RE_SCORE` 落库、`RE_ANNUAL_RESULT` 归档生成）均是模块内自包含的同步业务逻辑，无需通知其他模块或触发异步下游处理；且本模块当前无被其他模块依赖的场景（见 §1），也就没有"事件驱动跨模块联动"的需求来源。

若未来引入党建业务事件（如年度归档结果生成后通知 `report-analytics-center` 更新统计维度），需遵循平台事件规范（`docs/common-dev-guide.md` §7）：命名 `red_engine.<aggregate>.<action>.v1`，继承事件基类，`@TransactionalEventListener(AFTER_COMMIT)` 异步消费。

---

## 5. 小结

| 契约方向 | 结论 |
|---|---|
| 本模块对外暴露的 `*Api`/`*QueryApi` | 无（`api/` 包下只有 `dto/`） |
| 本模块消费的上游 `*Api` | `auth-permission-center.CurrentUserApi`（仅 `getCurrentEmpId()`）、`system-governance-center.FileApi`（仅 `bindFile(...)`） |
| 字典读取方式 | `SYS_DICT`（`RE_` 前缀命名空间，4 类 18 项），Java 代码不调用 `DictApi`，前端走通用字典查询端点 |
| Spring 领域事件 | 无发布 |
| 被本模块依赖但未使用的常见上游 API | `BizScopeApi`、`DictApi`、`WorkflowApi`/`WorkflowQueryApi`（均未 import） |

---

## 6. 关联文档索引

| 文档 | 说明 |
|---|---|
| `red-engine-center/CLAUDE.md` | 模块权威上下文：依赖关系、包结构、数据库表、技术债 |
| `03-接口设计与报文.md` | REST 接口全量端点契约 |
| `AGENTS.md` | 本目录文档维护规则 |
| `auth-permission-center/04-对外API契约.md` | `CurrentUserApi` 完整接口定义 |
| `system-governance-center/04-对外API契约.md` | `FileApi` 完整接口定义 |
