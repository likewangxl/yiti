# 红色引擎（党建管理）— 对外 API 契约

> 版本：v1.1
> 最后更新：2026-08-31
> 模块编码：red-engine-center
> 本文以代码为准核实：本模块**是否**对外暴露 `*Api`/`*QueryApi`、本模块消费上游哪些 `*Api`、字典/事件约定；任务域 REST/RBAC 对照见 `03-接口设计与报文.md` 第 12 节
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

这些 DTO 都只是 REST 端点的请求/响应报文载体（供 `03-接口设计与报文.md` 描述的 REST Controller 使用），**不构成跨模块 Java 契约**——没有任何其他业务模块 `import` 本模块的 `api.dto.*` 或依赖本模块的 `mapper`/`entity`（`red-engine-center/AGENTS.md`「模块定位」已如此定性，本文再次以 grep 独立核实一致）。

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
- `ReReviewController`（同上）——`approve`/`reject` 取当前登录审核人工号；`ReReviewService`
  除回填 `RE_SUBMIT.reviewerId` 外，还会与持久化 `submitterId` 比较并以 `RE-40008` 拒绝本人自审
- 任务管理、任务处理、附件、异步导出和首页服务——取当前操作人、角色编码和系统管理员标识，
  再由红色引擎 Service 依据任务/分配/组织实体执行二次数据范围校验；首页逾期扣分的
  `SYS_ADMIN` 专属守卫不依赖前端菜单隐藏

**用到的方法**：仅 `getCurrentEmpId()`（`CurrentUserApi` 接口另有 `getCurrentUserContext`/`getCurrentOrgCode`/`getCurrentRoleIds`/`getCurrentRoleCodes`/`getCurrentCandidateGroupKeys`/`isSystemAdmin`，本模块均未调用）。

角色授权由平台拦截链按用户全部启用角色的资源并集完成；兼容期
`POST /api/auth/switch-role` 只是 no-op，本模块不调用也不依赖该接口。
权限并集只授予端点调用资格：同时具备报送和审核角色的用户仍受 `ReReviewService` 实体级
职责分离约束，不能 approve/reject 自己创建的记录。

**未使用 `BizScopeApi`**：本模块一期数据范围仍统一走
`PT_ROLE_BIZ_SCOPE.BIZ_TYPE='RED_ENGINE'`、`DATA_SCOPE='ALL'`（4 党建角色 + SYS_ADMIN）。
`RE_USER_PARTY_MAP` 只在创建/“我的上报”场景派生党组织，并非通用数据范围实现；模块未接入
`BizScopeApi`/`DataScopeContext` 的机构子树或复合范围能力。复合 DataScope 的精确 OR 并集及
原 redengine 的党组织审核隔离延期为独立安全改造。

### 2.2 `UserApi`（`auth-permission-center`）

```java
import com.bank.branch.platform.auth.api.UserApi;
```

**使用位置**：`ReUserPartyMapService`（`private final UserApi userApi;`）。

**用到的方法**：
- `getUserByEmpId(userId)`：绑定前确认请求中的值是真实 `PT_USER.USER_ID`；不存在时抛
  `RE-40009`，不写 `RE_USER_PARTY_MAP`。
- `findUsersByUsernameAndDisplayName(username, displayName)`：映射查询分页前，按
  `PT_USER.USERNAME`/工号和 `PT_USER.USERCHNNAME`/姓名做包含式模糊查询，两条件同时传入时为
  AND；两者均空返回空列表，防止误触发无界全量查询。返回的 `empId` 作为本域分页的候选
  `USER_ID`。
- `getUserByEmpIds(userIds)`：对映射当页的 `PT_USER.USER_ID` 批量回填 `PT_USER.USERNAME`
  与 `PT_USER.USERCHNNAME`，避免逐行调用单用户 API。
- `mapEmpIdsToUsername(userIds)`：保留给未分页的内部兼容列表辅助方法，不是当前 REST 分页端点的
  回填路径。

红色引擎只保存 `USER_ID`，`USERNAME`/`USERCHNNAME` 仅作为
`ReUserPartyMapDTO.username`/`displayName` 响应字段展示；前端 auth 用户下拉同时展示工号与姓名，
仅提交 `USER_ID`。

任务域还使用 `UserApi.getEmpIdsByRoleCode` 解析报送员和组织审核员候选，使用
`getUserByEmpId`/`getUserByEmpIds` 回填任务分配中的提交人信息；这些调用只返回 auth 公开 DTO，
不直接读取 `PT_USER`。

### 2.3 `FileApi`（`system-governance-center`）

```java
import com.bank.branch.platform.governance.api.FileApi;
```

**使用位置**：`ReSubmitService`、`ReTaskFileServiceImpl`、`ReTaskExportServiceImpl`。

**用到的方法**：
- `ReSubmitService` 在材料上报创建时调用 `getFileName` 与 `bindFile`，并把业务侧文件元数据写入
  `RE_SUBMIT_FILE`；前端先经治理中心上传端点取得 `fileObjectId`。
- `ReTaskFileServiceImpl` 在红色引擎完成 assignment/提交版本/文件归属校验后调用
  `getFileContent` 与 `getFileName`；无归属时不触发治理文件读取。
- `ReTaskExportServiceImpl` 使用字节上传、`getFileContent` 读取导出 ZIP 和任务附件；最终产物
  仍是治理中心文件对象，不将对象 ID作为前端下载口令。

任务附件下载、导出范围和治理文件访问必须在红色引擎实体授权之后执行。不得依赖前端隐藏、直接
查询治理中心表或直连 OBS。当前治理实现的对象存储以 `ObsStorageClient`/OBS 为准，接口历史注释
中出现的 MinIO 名称不改变跨模块契约。

### 2.4 `JobApi`（`system-governance-center`）

```java
import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
```

**使用位置**：`ReTaskScheduler`。

任务窗口补偿使用固定白名单键 `RED_ENGINE_TASK_WINDOW` 注册/注销治理中心 Quartz Job，周期表达式
与 Job 类名由红色引擎服务端固定，不接受请求参数透传。红色引擎不创建自己的 Scheduler，不直写
`SYS_JOB_CONF` 或 `QRTZ_*`。隔离联调 profile 关闭调度时，不能据此证明生产 Quartz 已接通。

### 2.5 未消费的其他上游 `*Api`

以下平台常见 `*Api` **未被本模块 import/使用**（grep 确认为空）：
- `DictApi`（`system-governance-center`）——当前 Java 代码未调用；`RE_ITEM_CODE` 的红色引擎只读投影
  映射到治理 `SYS_DICT` 的既有分组，字典内容不在本次 RBAC DML 中新增。该映射不应扩展为跨模块
  表写入；若按平台依赖红线收口，后续应改为治理 `DictApi` 查询并保留本次文档的字典快照边界（见
  `09-依赖契约摘要.md`），本阶段不引入新的红色引擎对外 `*Api`。
- `BizScopeApi`（`auth-permission-center`）——见 2.1 说明
- `WorkflowApi`/`WorkflowQueryApi`（`workflow-center`）——本模块**不接 Flowable**，审核流是自管两级状态机（`RE_SUBMIT.status` 字段流转），不依赖 `workflow-center` 模块（模块根 AGENTS.md 已明确该边界）
- `NotifyApi`（`system-governance-center`）——当前代码未发送逾期通知；通知渠道、收件人和幂等键仍待业务确认

---

## 3. 字典约定（`SYS_DICT`，`RE_` 前缀命名空间）

红色引擎字典**全部拍平落 `SYS_DICT` 单表**（非 `SYS_DICT_ITEM` 两级设计），沿用平台既有惯例，与本模块无 `DictApi` 调用不矛盾——字典查询走的是 `system-governance-center` 已有的通用字典查询 REST 端点（前端按 `dict_type` 查询），本模块 Java 代码不需要专门再封装一层调用。

| `dict_type` | 中文名 | 项数 | 说明 |
|---|---|---|---|
| `RE_ORG_TYPE` | 组织类型 | 3 | 经营单位/营销部室/中后台部门 |
| `RE_DIMENSION` | 考核维度 | 4 | dim1(党建联建 35分)/dim2(业务提升 50分)/dim3(头雁与先锋 10分)/dim4(督导与工作总结 5分) |
| `RE_SUBMIT_STATUS` | 上报状态 | 3 | pending/approved/rejected（**注意**：字典项值为英文小写字面量，与 `RE_SUBMIT.status` 实际的整型 `0/1/2/3` 编码不是同一套值域，前端展示需自行映射，非直接可用的 `status` 整型翻译表） |
| `RE_ITEM_CODE` | 考核项编码 | 8 | 1.1/1.2/1.3/2.1/2.2/4.1/4.2/sup，`remark` 字段携带各项满分上限提示（如"最高35分"） |

`ReUserPartyMap.partyRole` 四个字符串字面量（`ORG_REVIEWER`/`BRANCH_REVIEWER`/`SECRETARY`/`REPORTER`）**不在**上述任何字典内，属硬编码兼容描述字段，无字典/枚举校验，也未走前端 `useDict()`。它不是授权字段；多角色权限唯一来源为平台 `PT_ROLE`/`PT_USER_ROLE`/`PT_ROLE_RESOURCE`。

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
| 本模块消费的上游 `*Api` | `auth-permission-center.CurrentUserApi`、`auth-permission-center.UserApi`、`system-governance-center.FileApi`、`system-governance-center.JobApi`；本阶段无新增红色引擎对外 `*Api` |
| 字典读取方式 | `SYS_DICT` 中 `RE_` 前缀命名空间；当前 `RE_ITEM_CODE` 为红色引擎只读投影，Java 代码未调用 `DictApi`，前端可走治理通用字典查询端点；跨模块表投影收口为后续契约项 |
| Spring 领域事件 | 无发布 |
| 被本模块依赖但未使用的常见上游 API | `BizScopeApi`、`DictApi`、`WorkflowApi`/`WorkflowQueryApi`（均未 import） |

---

## 6. 关联文档索引

| 文档 | 说明 |
|---|---|
| `red-engine-center/AGENTS.md` | 模块权威上下文：依赖关系、状态机、安全约束与已知限制 |
| `03-接口设计与报文.md` | REST 接口全量端点契约 |
| `07-审计要求.md` | 任务发布、审核、导出和逾期扣分的审计动作 |
| `08-初始化数据清单.md` | 角色、菜单、API 资源及历史关系处理清单（不替代 DBA 执行审批） |
| `09-依赖契约摘要.md` | 任务域与 auth/governance 的依赖边界及未决项 |
| `AGENTS.md` | 本目录文档维护规则 |
| `auth-permission-center/04-对外API契约.md` | `CurrentUserApi`、`UserApi` 完整接口定义 |
| `system-governance-center/04-对外API契约.md` | `FileApi` 完整接口定义 |
| `docs/superpowers/sql/2026-08-31-redengine-task-rbac-align.sql` | 仅供获授权 `yit_test` 的 RBAC/角色合并 DML |
