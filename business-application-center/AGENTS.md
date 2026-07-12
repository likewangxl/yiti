<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-07-12 -->

# business-application-center

## Purpose
业务申请中心（核心域），承载两大核心业务域：**资产投放申请 (Loan)** 和 **中场支持申请 (Support)**。是"营销→触达→落地"闭环的落地环节，也是绩效和报表模块最重要的事实源。

**基础包名**: `com.bank.branch.platform.bizapp`
**Maven 坐标**: `com.bank.branch.platform:business-application-center`
**对外契约**: 5 个 `*Api` 接口 + 21 个 REST 端点。
**当前版本**: V1.0（60 Java + 26 测试，0 UOE）

## Key Files

| File | Description |
|------|-------------|
| `src/main/java/com/bank/branch/platform/bizapp/api/LoanApi.java` | 资产投放 API（写操作） |
| `src/main/java/com/bank/branch/platform/bizapp/api/LoanQueryApi.java` | 资产投放查询 API |
| `src/main/java/com/bank/branch/platform/bizapp/api/SupportApi.java` | 中场支持 API（写操作） |
| `src/main/java/com/bank/branch/platform/bizapp/api/SupportQueryApi.java` | 中场支持查询 API |
| `src/main/java/com/bank/branch/platform/bizapp/api/BizApplyQueryApi.java` | 跨域查询聚合 API |
| `src/main/java/com/bank/branch/platform/bizapp/service/BizStateMachine.java` | 统一状态机（Loan + Support） |
| `src/main/java/com/bank/branch/platform/bizapp/service/SupportScenarioRouter.java` | 场景 A/B 路由 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `api/` | 5 个对外 API 接口 + 8 个 DTO + 2 个 Converter |
| `config/` | `BizAppCacheConfig.java` |
| `controller/` | 3 个 REST 控制器（LoanController 9 端点 / SupportController 8 端点 / SupportDeptController 4 端点） |
| `facade/` | 5 个 API 实现 |
| `service/` | 9 个业务服务（LoanService / SupportService / SupportDeptService / BizApplySearchService 等） |
| `mapper/` | 2 个 MyBatis-Plus Mapper 接口（`extends BaseMapper`）+ 2 个 XML（自定义 SQL） |
| `entity/` | 2 个实体（LoanApply / SupportRequest） |
| `enums/` | 5 个枚举（含 BizAppErrorCode 31 条错误码） |
| `event/` | 7 个领域事件（LoanSubmitted / SupportDispatched 等） |
| `listener/` | 2 个工作流事件监听（LoanWorkflowListener / SupportWorkflowListener） |
| `dto/req/` | 6 个请求 DTO |
| `dto/resp/` | 3 个响应 DTO |

## For AI Agents

### Working In This Directory
- 模块间只通过 `*Api` 接口交互，禁止直接依赖 `mapper`/`entity`/`serviceImpl`
- 所有接口必须注册到 `PT_RESOURCE` 表并使用 `@BizAuth` 注解
- PT_RESOURCE SQL: `docs/superpowers/sql/2026-04-14-bizapp-pt-resource.sql`（21 条记录）
- 错误码前缀 `BIZ-{HTTP_STATUS}{SEQ}`

### Testing Requirements
- 168 个测试用例，0 失败
- Service 单元测试: Mockito（`@ExtendWith(MockitoExtension.class)`）
- Controller 集成测试: MockMvc + `AbstractControllerIntegrationTest`
- Facade 单元测试: Mockito
- 测试配置: H2 MySQL 兼容模式

### Common Patterns
- **状态机**: Loan (DRAFT→IN_APPROVAL→COMPLETED/REJECTED/CANCELLED)，Support (+IN_PROGRESS 仅场景 B)
- **场景路由**: 场景 A（产品直达）→ `support_simple_v1`，场景 B（部门承接）→ `support_complex_v1`
- **多产品拆单**: 场景 A 多产品时，每个 productId 生成独立 support_request，共享 submitGroupId
- **双视图权限**: SUPPORT（发起侧按 owner_org_id + created_by），SUPPORT_DEPT（承接侧按 support_dept_id + assigned_emp_id）
- 14 个高危端点均已补 `@AuditLog` 注解
- Listener 使用 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`

## Dependencies

### Internal
- `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- `auth-permission-center`（CurrentUserApi / BizScopeApi / OrgApi / AuthApi）
- `workflow-center`（WorkflowApi — 贷款/支持审批流程启动）
- `system-governance-center`（DictApi / FileApi / NotifyApi / AuditApi）
- `customer-marketing-center`（CustomerQueryApi / LeadApi）
- `portal-content-center`（ProductApi）

### External
- MyBatis-Plus — ORM（2 个 Mapper 均已迁移到 `BaseMapper`，表名全大写）
- Flowable 7.0.1 — 工作流引擎（通过 workflow-center）

## Database Tables (2 张)

| Table | Entity | Description |
|-------|--------|-------------|
| `LOAN_APPLY` | LoanApply | 资产投放申请（applyNo, custId, creditAmount, businessKey=LOAN:{id}） |
| `SUPPORT_REQUEST` | SupportRequest | 中场支持申请（requestNo, submitGroupId, supportDeptId, businessKey=SUPPORT:{id}） |

## V1.0 已知技术债（2026-04-25）

| # | Title | Priority |
|---|-------|----------|
| 1 | 错误码缺 5 条（BIZ-40306 / 42203 / 42204 / 42302 / 50003），近义码语义等价覆盖 | 低 |
| 2 | LoanService V2 集成 workflow 历史查询补 processMap/approvalLogs | 低 |
| 3 | LoanDetailResp V2 通过 ClaimApi 补 ownerEmpId | 低 |
| 4 | LoanDetailResp V2 通过 ClaimApi 补 claimedTime | 低 |

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
