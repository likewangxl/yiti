# red-engine-center 模块开发指导

本文件只记录红色引擎领域特有的架构、安全和兼容约束；仓库通用红线遵循根 `AGENTS.md`。

## 模块定位

`red-engine-center` 提供党组织树、材料上报、两级审核评分、逾期扣分、驾驶舱、预警、年度归档和数据导出能力，基础包为 `com.bank.branch.platform.redengine`。

本模块是独立核心业务域，审核状态机由自身维护，**不接入 Flowable**，不写 `business_key` 或 `BIZ_PROCESS_MAP`。当前没有对外 `*Api`/`*QueryApi`；其他模块未来需要党建数据时，必须新增明确的查询接口，禁止直接依赖本模块 service、mapper 或 entity。

允许的跨模块依赖只有平台基础模块，以及：

- `auth-permission-center` 的 `CurrentUserApi`，用于取得当前登录人工号；
- `system-governance-center` 的 `FileApi`，用于附件绑定。

## 代码与持久化

- `controller/`：组织、用户映射、上报、审核、驾驶舱和导出 REST 入口。
- `service/`：领域规则和实体级二次权限校验；本模块没有 facade，因为尚无跨模块 API。
- `entity/`、`mapper/`：`RE_` 表实体和 MyBatis-Plus Mapper。Mapper 均继承 `BaseMapper<T>`，简单 CRUD 与条件查询继续使用 MyBatis-Plus，不新增重复 XML。
- `api/dto/`：REST 及未来跨模块契约使用的强类型 DTO；不得用裸实体接收外部写请求。

表结构以目标库当前 schema 为真相。结构变化遵循根目录的 Flyway/DDL 禁令，权限与字典 DML 必须遵循根目录可执行 SQL 白名单。

## 权限与数据边界

- 认证统一使用平台 `PT_USER`、Spring Session 和 RBAC；不得恢复独立账号、JWT 或旧系统权限表。
- REST 端点必须使用 `@BizAuth` 并登记 `PT_RESOURCE`。资源种子仅是交付载体，运行时授权事实必须从数据库与真实请求验证，不能依赖本文维护端点数量或角色行数。
- 多角色权限取平台角色资源并集。`RE_USER_PARTY_MAP.party_role` 只是兼容描述字段，不能参与鉴权，也不能表达多角色并集。
- 创建上报时，`orgId` 和 `submitterId` 必须由服务端根据当前用户及 `RE_USER_PARTY_MAP` 派生，不能信任前端传值。
- `approve`/`reject` 必须基于持久化 `submitterId` 拒绝本人自审，命中时以 `RE-40008` fail-close，且不得写评分或改变上报状态。
- 删除组织、执行逾期扣分、生成年度归档等高危操作继续使用独立 URL、必填原因和审计；Controller 鉴权不能代替 Service 实体校验。
- 附件先经 governance 上传取得 `fileObjectId`，创建上报后由 `FileApi.bindFile` 建立关联；本模块不自建文件上传或存储实现。

## 自管审核状态机

```text
创建 -> status=1（已提交）
status=1 --approve--> status=2（已通过，并可写 RE_SCORE）
status=1 --reject----> status=3（已驳回）
```

- `status=0` 是数据库遗留草稿值，当前创建路径不会产生它；不要未经产品决策恢复两段式草稿流程。
- 当前 `approve`/`reject` 没有“只能审核 status=1”的前置条件，允许改判并覆盖状态。这是已知兼容行为，不得在普通修复中擅自收紧。
- 审核人、审核时间和意见由服务端回填；前端不得指定审核人。

## 当前业务口径

- 红牌：`finalScore < 60`；黄牌：`60 <= finalScore < 80`。调整阈值必须同步驾驶舱、预警和测试。
- 逾期候选：`status in (0, 1)` 且 `submitDate + 7 天` 早于当前日期；扣分由预警池人工逐条执行，默认扣 5 分。
- 导出只支持 submit/score 两类；具体执行模式和容量限制见下方“已知有效限制”第 3 项。
- `RE_USER_PARTY_MAP` 只用于创建和“我的上报”等组织派生，不等价于平台通用 DataScope。

## 已知有效限制

以下是当前代码仍存在的边界，修改前需要单独的产品或安全决策，不能把它们误写成已解决：

1. `generateAnnualResult(year)` 用 `year` 决定归档键，但聚合 `RE_SCORE` 时未按 `scoreYear` 过滤，跨年度数据可能混算。
2. 重复 approve/reject 没有状态前置校验；反复改判可能产生重复或滞留的 `RE_SCORE`。评分上限采用“查询后内存求和再插入”，并发下存在 TOCTOU 风险。
3. 同步导出上限 10000 行，高于平台“5000 行以上异步”的通用线，且 `ResponseEntity<byte[]>` 会全量物化；扩大数据量前必须改为异步或流式。
4. 暂无按 `submitId` 查询评分明细和附件列表的端点；部分排行/逾期 DTO 不含组织名，前端需用组织树补齐。
5. `RE-4xxxx` 仍是裸字符串错误码，尚未收敛为统一枚举。
6. 驾驶舱与导出当前按全局口径查询；既有角色业务范围为 `DATA_SCOPE='ALL'`。如果改为按党组织隔离，必须同时改 Service、角色范围、资源授权和真实 RBAC 验证。

## 测试

- Service/Controller 的 `*Test.java` 是 Surefire 单元测试；保持 Mockito/standalone MockMvc 的快速反馈方式。
- Mapper 的 `*IT.java` 由 Failsafe 执行，使用事务回滚的隔离测试基座。
- 完整鉴权链路由 bootstrap 的 `RedEngineSmokeIT` 与 `redengine-smoke` Profile 验证；不要用 `test` Profile 替代真实 RBAC。
- 前端变更还必须遵循根 `AGENTS.md` 的官方 `playwright-cli` 真实页面验收门禁。
