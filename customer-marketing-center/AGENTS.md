<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-04-26 -->

# customer-marketing-center

## Purpose
客户营销中心（核心域），覆盖客户全生命周期管理：标签体系、线索管理（含审批流）、客户主档、客户池与认领、触达任务、触达报表。是平台核心业务模块之一。

**基础包名**: `com.bank.branch.platform.customer`
**Maven 坐标**: `com.bank.branch.platform:customer-marketing-center`
**对外契约**: 5 个 `*Api` 接口 + 36 个 REST 端点。
**当前版本**: V1.0（113 Java + 46 测试，0 UOE）

## Key Files

| File | Description |
|------|-------------|
| `src/main/java/com/bank/branch/platform/customer/api/TagApi.java` | 标签查询 API（7 方法） |
| `src/main/java/com/bank/branch/platform/customer/api/LeadApi.java` | 线索查询 API（5 方法） |
| `src/main/java/com/bank/branch/platform/customer/api/CustomerQueryApi.java` | 客户主档查询 API（9 方法） |
| `src/main/java/com/bank/branch/platform/customer/api/ClaimApi.java` | 认领查询 API（6 方法） |
| `src/main/java/com/bank/branch/platform/customer/api/TouchTaskQueryApi.java` | 触达任务查询 API（8 方法） |
| `src/main/java/com/bank/branch/platform/customer/config/CustomerCacheConfig.java` | 缓存 Key/TTL 常量 + 防雪崩抖动 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `api/` | 对外 API 接口 + 9 个 DTO + 5 个 Converter（跨模块契约） |
| `controller/` | 13 个 REST 控制器（36 端点，含 1 个 admin 子包） |
| `facade/` | 5 个 API 实现（TagApiImpl / LeadApiImpl / CustomerQueryApiImpl / ClaimApiImpl / TouchTaskQueryApiImpl） |
| `service/` | 13 个业务服务（含 TagService / LeadService / CustomerService / ClaimService / TouchTaskService 等） |
| `mapper/` | 9 个 MyBatis Mapper 接口 + 9 个 XML 映射文件 |
| `entity/` | 8 个数据库实体 |
| `enums/` | 10 个枚举（含 CustomerErrorCode 错误码） |
| `event/` | 7 个领域事件（LeadApproved / ClaimCreated / TouchCompleted 等） |
| `listener/` | 5 个事件监听器（WorkflowCallbackListener + LeadApprovedListener + ClaimCreatedListener 等） |
| `dto/req/` | 18 个请求 DTO |
| `dto/resp/` | 5 个响应 VO |

## For AI Agents

### Working In This Directory
- 模块间只通过 `*Api` 接口交互，禁止直接依赖 `mapper`/`entity`/`serviceImpl`
- 所有接口必须注册到 `PT_RESOURCE` 表并使用 `@BizAuth` 注解
- PT_RESOURCE SQL 存放于 `docs/superpowers/sql/2026-04-14-customer-*.sql` 和 `2026-04-21-customer-contract-alignment-pt-resource.sql`
- 错误码前缀 `CUST-{HTTP_STATUS}{SEQ}`

### Testing Requirements
- 单元测试: Mockito（Service 层），每个 Service 对应 `*Test.java`
- 集成测试: MockMvc + H2（Controller 层），基类 `AbstractControllerIntegrationTest`
- 测试配置: `CustomerTestConfiguration.java` + `application-test.yml`（H2 MySQL 兼容模式）
- Mock 用户上下文: `@WithMockEmpContext` 注解 + `MockEmpContextExtension`
- 当前测试数: 46 个测试文件

### Common Patterns
- 线索审批通过 `WorkflowApi.startProcess()` 启动 Flowable 流程，`SELECT FOR UPDATE` 防并发
- 认领通过 `cust_claim (cust_id, org_id)` UK 防并发（DuplicateKeyException → CUST-40904）
- 触达日志通过 `(touch_task_id, client_uuid)` UK 幂等
- 标签客户覆盖式导入：先 `deleteByTagId()` 再 `insertBatch()`
- 所有事件监听器使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`

## Dependencies

### Internal
- `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- `auth-permission-center`（CurrentUserApi / BizScopeApi / OrgApi）
- `workflow-center`（WorkflowApi — 线索/删除审批流程启动）
- `system-governance-center`（DictApi / FileApi / NotifyApi / AuditApi）

### External
- MyBatis 3.0.3 — ORM
- Flowable 7.0.1 — 工作流引擎（通过 workflow-center）
- Redis 6.X — 缓存（Cache-Aside，5 分钟 TTL）

## Database Tables (8 张)

| Table | Entity | Description |
|-------|--------|-------------|
| `cust_tag` | CustTag | 客户标签 |
| `cust_tag_rel` | CustTagRel | 标签-客户关系 |
| `cust_lead` | CustLead | 客户线索（含完整客户快照） |
| `lead_import_batch` | LeadImportBatch | 线索导入批次 |
| `cust_master` | CustMaster | 客户主档 |
| `cust_claim` | CustClaim | 客户认领 |
| `touch_task` | TouchTask | 触达任务 |
| `touch_log` | TouchLog | 触达日志 |

## V1.0 已知技术债（2026-04-25）

| # | Title | Priority |
|---|-------|----------|
| 1 | 错误码 26 vs 设计 35（缺 403 系列 7 条 + 422 系列 8 条） | 中 |
| 2 | 零 ArchUnit 守护（无 arch/ 子目录） | 中 |
| 3 | 线索导入行级校验简化未实现（4 处 TODO） | 低 |
| 4 | CROSS_ORG 审计待 @AuditLog 升级（3 处 TODO） | 低 |
| 5 | WorkflowCallbackListener 未区分 APPROVED/REJECTED | 低 |
| 6 | LeadDeletedListener 线索独立标签清理预留 | 低 |
| 7 | TouchTaskMapper.xml H2/MySQL 函数方言 TODO | 低 |

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
