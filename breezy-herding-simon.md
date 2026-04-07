# 后端模块全面测试计划

## Context

项目已完成 4 个核心模块的开发（common、auth-permission-center、system-governance-center、workflow-center），现有 266 个单元测试全部通过。但测试存在以下系统性缺口：

1. **Controller 层测试浅薄**：全部只测 200 OK，无错误路径、无请求校验测试
2. **Service 层边界/异常场景缺失**：null 输入、状态机违规、并发等未覆盖
3. **无集成测试**：所有测试都是 Mockito 单元测试，MyBatis SQL、事务边界、Spring Context 加载均未验证
4. **缓存链路未端到端验证**：仅测 cache hit，miss->DB->write cache 链路缺失

**目标**：按 common → auth → governance → workflow 顺序，逐模块补全测试，每个模块分 3 层：
- L1: 补全单元测试缺口（边界、异常、状态机）
- L2: 补全 Controller 错误路径测试
- L3: 新增集成测试（Spring Context + H2）

---

## 执行顺序总览

| 阶段 | 模块 | L1 单元补全 | L2 Controller 错误路径 | L3 集成测试 |
|------|------|------------|----------------------|------------|
| 1 | common (5 子模块) | 约 25 个测试方法 | N/A | N/A |
| 2 | auth-permission-center | 约 40 个测试方法 | 约 20 个测试方法 | 约 15 个测试方法 |
| 3 | system-governance-center | 约 35 个测试方法 | 约 18 个测试方法 | 约 12 个测试方法 |
| 4 | workflow-center | 约 30 个测试方法 | 约 12 个测试方法 | 约 10 个测试方法 |
| 5 | bootstrap 集成 | N/A | N/A | 约 5 个测试方法 |

**预估新增测试总量**：约 220 个测试方法

---

## 阶段 1：common 模块测试补全

### 1.1 common-security（优先级最高）

**文件**：`common/common-security/src/test/java/com/bank/branch/platform/common/security/`

| 测试类 | 补全场景 | 类型 |
|--------|---------|------|
| `CurrentUserContextTest` | ThreadLocal 隔离验证、set/get/clear 完整生命周期、空 roleIds vs null 处理 | L1 |
| `DataScopeContextTest` | ThreadLocal 线程隔离、覆盖写入行为、所有字段读取验证 | L1 |
| `ObjectMetaRegistryTest` | null objectKey 注册、supportedScopes 包含/不包含某 scope 的 fail-fast 行为 | L1 |
| `BizTypeTest` | 所有枚举值覆盖、fromValue 无效值处理 | L1 |
| `BizActionTest` | 高危动作标识判定（isHighRisk） | L1 |
| `DataScopeTypeTest` | priority 排序验证、ALL > ORG_SUBTREE > ORG > SELF_CREATED | L1 |

### 1.2 common-web

| 测试类 | 补全场景 | 类型 |
|--------|---------|------|
| `GlobalExceptionHandlerTest` | BizException→错误码映射、AuthException→401、PermissionDeniedException→403、MethodArgumentNotValid→400 | L1 |
| `PageRequestTest` | pageNo=0/负数校正、pageSize>100 校正、sortBy 不在白名单时重置 | L1 |

### 1.3 common-aop

| 测试类 | 补全场景 | 类型 |
|--------|---------|------|
| `AuditLogAspectTest` | reason 必填校验（reasonRequired=true 时无 reason 抛异常）、目标方法异常时审计日志仍记录、事件字段完整性 | L1 |

### 1.4 common-trace

| 测试类 | 补全场景 | 类型 |
|--------|---------|------|
| `TraceIdFilterTest` | 上游传入 X-Trace-Id 时复用、响应头回写 traceId | L1 |

### 1.5 common-db

| 测试类 | 补全场景 | 类型 |
|--------|---------|------|
| `AuditFieldFillerTest` | CurrentUserContext 未设置时的降级行为、created/updated 时间填充 | L1 |
| `SlowSqlInterceptorTest` | 超过阈值的慢 SQL 告警日志验证 | L1 |

---

## 阶段 2：auth-permission-center 测试补全

### 2.1 L1 - Service 层单元测试补全

| 测试类 | 补全场景 |
|--------|---------|
| `AuthServiceTest` | 禁用账户登录抛 AUTH-40103、无机构映射登录异常、无角色用户登录、null/空用户名密码 |
| `PermissionCacheServiceTest` | cache miss→DB→write cache 完整链路（getResourceIdsByRoleId、getBizScopesByRoleId、getOrgSubtreeCodes）、空结果缓存行为 |
| `BizScopeServiceTest` | ORG scope 写权限校验、ORG_SUBTREE 写权限校验（需 orgService mock）、SELF_ASSIGNED 读范围匹配、listByPage 分页 |
| `ResourceServiceTest` | updateResource 正常路径、getById 正常路径、deleteResource 不存在时处理 |
| `RoleServiceTest` | deleteRole 不存在、createRole null/空字段、listByPage 带关键字过滤 |
| `UserRoleServiceTest` | bindRoles 空 roleIds 列表、unbindRole 记录不存在 |
| `RoleResourceServiceTest` | replaceResources 角色不存在 |
| `OrgServiceTest` | getOrgSubtree org 不存在、searchOrgs 空关键字 |
| `ResourceMatcherTest` | 多匹配资源优先级、大小写 URL、尾斜杠处理 |

### 2.2 L2 - Controller 错误路径测试

| 测试类 | 补全场景 |
|--------|---------|
| `AuthControllerTest` | 登录失败→401 响应体验证、空 body→400 |
| `RoleControllerTest` | 重复 roleCode→409、角色不存在→404 |
| `ResourceControllerTest` | 重复 URL+Method→409、有子节点删除→422 |
| `BizScopeControllerTest` | 角色不存在→404、无效 BizType 参数→400 |
| `UserRoleControllerTest` | 用户不存在→404、空 roleIds→400 |
| `OrgControllerTest` | 组织不存在→404 |

### 2.3 L3 - 集成测试（新增）

**新建文件**：`auth-permission-center/src/test/java/.../integration/`
**测试基础设施**：
- `@SpringBootTest` + H2 内存数据库
- `application-test.yml` 配置（H2 + 禁用 Redis mock / 使用 embedded Redis）
- MyBatis Mapper XML SQL 验证

| 测试类 | 场景 |
|--------|------|
| `AuthIntegrationTest` | 完整登录链路：DB 用户→AuthService→session 写入→CurrentUser 建立 |
| `RbacIntegrationTest` | 资源注册→角色绑定→ResourceMatcher 匹配→RbacAuthorizer 授权完整链路 |
| `BizScopeIntegrationTest` | BizScope 配置→多角色并集→DataScope 解析→SQL 过滤条件生成 |

**关键文件**：
- 新建 `auth-permission-center/src/test/resources/application-test.yml`
- 新建 `auth-permission-center/src/test/resources/schema-h2.sql`（auth 模块表 DDL 的 H2 版本）
- 新建 `auth-permission-center/src/test/resources/data-h2.sql`（测试种子数据）

---

## 阶段 3：system-governance-center 测试补全

### 3.1 L1 - Service 层单元测试补全

| 测试类 | 补全场景 |
|--------|---------|
| `DictServiceTest` | updateDict 正常与不存在场景、getDictLabel 不存在 code 返回值、getDictItems DB 返回空列表 |
| `ConfigServiceTest` | JSON 类型转换、getConfigValue 非法类型转换异常、listConfigs 分页 |
| `CalendarServiceTest` | isWorkingDay 日期不在 DB 中、addWorkingDays 跨月跨年、toggleWorkday 0→1 翻转、countWorkingDays 无效日期范围 |
| `AuditLogServiceTest` | null/部分字段 AuditLogCmd、queryLogs 部分过滤条件 |
| `NotificationServiceTest` | markAllAsRead 方法、queryNotifications 带 isRead 过滤 |
| `FileServiceTest` | getDownloadUrl 正常路径、bindFile 正常插入路径、空文件上传、格式白名单边界（.PDF/.DOCX 大写） |
| `JobServiceTest` | pauseJob 已暂停、resumeJob 已启用、completeJobRun 已完成 log |
| `SqlProbeServiceTest` | UPDATE/DROP/ALTER/TRUNCATE 拒绝（@ParameterizedTest）、空 SQL、SQL 注入尝试、大小写关键字 |
| `GovAuditLogHandlerTest` | null 字段事件处理 |

### 3.2 L2 - Controller 错误路径测试

| 测试类 | 补全场景 |
|--------|---------|
| `DictControllerTest` | 重复 code→409、必填字段缺失→400 |
| `CalendarControllerTest` | 过去日期翻转→422 |
| `FileControllerTest` | 文件不存在→404、格式不允许→422 |
| `JobControllerTest` | 任务运行中重复触发→409 |
| `SqlProbeControllerTest` | 非 SELECT→422、并发超限→429 |
| `ConfigControllerTest` | key 不存在→404 |
| `NotificationControllerTest` | 通知不存在→404 |

### 3.3 L3 - 集成测试

| 测试类 | 场景 |
|--------|------|
| `DictIntegrationTest` | 字典 CRUD 完整链路 + 缓存命中/失效验证 |
| `AuditLogIntegrationTest` | 审计日志写入→分页查询→时间范围过滤完整链路 |
| `CalendarIntegrationTest` | 工作日历初始化→工作日计算→addWorkingDays 完整链路 |

---

## 阶段 4：workflow-center 测试补全

### 4.1 L1 - Service 层单元测试补全

| 测试类 | 补全场景 |
|--------|---------|
| `ProcessStartServiceTest` | Flowable RuntimeService 异常处理、startProcess 无首个任务、null/空 variables |
| `TaskOperationServiceTest` | approveTask 任务不存在、rejectTask 非 assignee、transferTask 转给自己、claimTask BizProcessMap 未找到 |
| `SlaCalculationServiceTest` | 精确边界值（exactlyAtWarning、exactlyAtTimeout）、零工作日、CalendarApi 异常 |
| `TodoQueryServiceTest` | 空结果、无 form config、BizProcessMap 未找到 |
| `CandidateResolverServiceTest` | 无效 candidateType、空数组 candidateValue、null 字段 |
| `TaskAssignmentListenerTest` | CandidateResolverService 异常、USER 类型候选人处理 |
| `ProcessCompletedListenerTest` | mapper 更新返回 0、已完成流程重复完成 |

### 4.2 L2 - Controller 错误路径测试

| 测试类 | 补全场景 |
|--------|---------|
| `ProcessControllerTest` | **POST start 端点测试（完全缺失）**、businessKey 不存在→404 |
| `TaskControllerTest` | 任务不存在→404、非 assignee 审批→403、已签收重复签收→409 |
| `WorkflowAdminControllerTest` | 必填字段缺失→400 |

### 4.3 L3 - 集成测试

| 测试类 | 场景 |
|--------|------|
| `WorkflowIntegrationTest` | 流程启动→任务分配→签收→审批→流程完成 全链路（需 Flowable in-memory engine） |
| `SlaIntegrationTest` | SLA 计算 + 工作日历集成的端到端验证 |

---

## 阶段 5：bootstrap 全链路集成测试

| 测试类 | 场景 |
|--------|------|
| `FullAuthChainTest` | 请求→Filter→Interceptor→@BizAuth→DataScope→Service 完整鉴权链路 |
| `CrossModuleApiTest` | 模块间 *Api 调用链路验证 |

---

## 测试基础设施建设

### 需新建的公共配置

1. **H2 兼容 DDL**：将 MySQL DDL 转为 H2 兼容语法
   - `auth-permission-center/src/test/resources/schema-h2.sql`
   - `system-governance-center/src/test/resources/schema-h2.sql`
   - `workflow-center/src/test/resources/schema-h2.sql`

2. **测试配置文件**：
   - 各模块 `src/test/resources/application-test.yml`（H2 数据源、禁用 Redis、Flowable 内存引擎）

3. **测试工具类**（可选）：
   - `TestDataBuilder`：测试数据构造工具
   - 公共 mock 辅助方法

### 测试风格约定（与现有代码一致）

- 框架：JUnit 5 + Mockito + AssertJ
- 命名：`methodName_condition_expectedBehavior`
- 类修饰符：package-private（无 public）
- 单元测试：`@ExtendWith(MockitoExtension.class)`
- 集成测试：`@SpringBootTest` + `@ActiveProfiles("test")`
- Controller 单元测试：MockMvc standalone setup
- 参数化测试：`@ParameterizedTest` + `@ValueSource` / `@CsvSource`

---

## 验证方式

每完成一个阶段后：
```bash
# 运行该模块所有测试
cd <module-name>
mvn test

# 运行全量测试
cd ..
mvn test

# 确认无编译错误、无测试失败
```

## 执行节奏

建议每个阶段完成后暂停，review 测试覆盖率和质量，确认后再推进下一阶段。各阶段内部按 L1 → L2 → L3 顺序执行。
