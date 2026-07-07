# customer-marketing-center 契约对齐修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `customer-marketing-center` 模块的代码实现严格对齐到 `docs/modules/customer-marketing-center/` 文档 (尤其 04-对外API契约.md / 01-功能规格.md §7.3bis) — 补齐 20+ 缺失 API 方法、修正触达状态机、引入 DTO 对外封装、补齐关键 REST 端点、同步更新 business-application-center 下游调用方。

**Architecture:** 采用"先扩后替"的增量迁移策略：①先引入 `api/dto/` 对外 DTO 包 + 静态转换器；②逐个 Api 接口用新签名替换（破坏性）并同步修正 2 个下游消费者；③修正触达状态机 (PENDING → IN_PROGRESS → SUCCESS/CANCELLED)；④补齐文档要求的 REST 端点；⑤细节打磨。每一 Phase 可独立提交并通过测试。

**Tech Stack:** Java 17 / Spring Boot 3.2.3 / MyBatis 3.0.3 / JUnit 5 / Mockito / MockMvc / H2 / Maven 多模块

**TDD 红线：** 项目 CLAUDE.md 硬性规定 Red-Green-Refactor。**每个代码任务必须先写测试、让其失败、再最小实现、再确认通过**，严禁事后补测试。

---

## 范围与原则

### 必须对齐的文档
- [docs/modules/customer-marketing-center/04-对外API契约.md](../../../docs/modules/customer-marketing-center/04-对外API契约.md) — API 签名权威
- [docs/modules/customer-marketing-center/01-功能规格.md](../../../docs/modules/customer-marketing-center/01-功能规格.md) — 业务流程 (§7.3bis 状态机)
- [docs/modules/customer-marketing-center/07-审计要求.md](../../../docs/modules/customer-marketing-center/07-审计要求.md) — 高危操作审计
- [docs/common-dev-guide.md](../../../docs/common-dev-guide.md) — 错误码 / 响应包装 / 分页规范

### 关键约束
1. **DTO 对外封装**：所有 `*Api` 方法必须返回 `api/dto/` 下的 DTO，不得暴露 `entity`
2. **Optional 返回**：单对象查询必须 `Optional<T>`；列表返回空 `List`（不 null）
3. **业务键约定**：`LEAD:{leadId}` / `LEAD:IMP_{batchId}` / `LEAD_DELETE:{leadId}` / `TOUCH:{taskId}`
4. **状态机完整性**：TouchTaskStatus 必须含 `IN_PROGRESS`，且首次日志提交触发 PENDING→IN_PROGRESS
5. **下游同步**：`business-application-center` 的 `LoanService`/`SupportService` 是已知消费者，每次破坏性签名修改后必须同步修复并通过其测试

### 不在本次范围
- BPMN XML 文件（文档预留但 V1.0 明确忽略）
- 消息总线事件（当前 `customer.touch.completed.v1` 仅 Spring ApplicationEvent，不对接 MQ）
- 副本库只读（report-analytics-center 走副本库，不调用本模块 Api）

---

## 文件结构规划

### 新增
```
customer-marketing-center/src/main/java/com/bank/branch/platform/customer/
├── api/
│   └── dto/                                    【新增目录】
│       ├── CustomerDTO.java                    客户 DTO
│       ├── LeadDTO.java                        线索 DTO
│       ├── TagDTO.java                         标签 DTO
│       ├── CustClaimDTO.java                   认领 DTO
│       ├── TouchTaskDTO.java                   触达任务 DTO
│       ├── TouchLogDTO.java                    触达日志 DTO
│       ├── RunningFlowDTO.java                 在途流程 DTO
│       ├── CustomerFilterDTO.java              客户过滤条件 DTO
│       └── TouchTaskSummaryDTO.java            触达汇总 DTO
├── api/converter/                              【新增目录】
│   ├── CustomerDTOConverter.java               Entity → DTO 转换器
│   ├── LeadDTOConverter.java
│   ├── TagDTOConverter.java
│   ├── CustClaimDTOConverter.java
│   └── TouchTaskDTOConverter.java
├── controller/
│   ├── CustomerHistoryController.java          【新增】跨机构历史查询
│   ├── CustomerTagController.java              【新增】客户打标/取消打标
│   ├── CustomerExportController.java           【新增】客户/标签/触达导出
│   ├── LeadVersionController.java              【新增】线索版本历史 API
│   └── admin/
│       └── AdminTouchTaskController.java       【新增】管理后台触达任务
└── service/
    └── TouchTaskStateMachineService.java       【新增】状态转移规则封装
```

### 修改
```
customer-marketing-center/
├── src/main/java/com/bank/branch/platform/customer/
│   ├── api/*.java (5 个)                       扩展方法签名 + 改返回类型为 DTO
│   ├── facade/*.java (5 个)                    重写实现
│   ├── enums/TouchTaskStatus.java              加 IN_PROGRESS
│   ├── service/TouchTaskService.java           加 markSuccess / markInProgress
│   ├── service/TouchLogService.java            首次日志触发状态转移
│   ├── controller/TouchTaskController.java     /complete → /success + @AuditLog
│   ├── controller/CustomerController.java      集成 /delete-apply 已有，补 tags
│   ├── listener/WorkflowCallbackListener.java  @TransactionalEventListener(AFTER_COMMIT)
│   ├── dto/resp/LeadImportPreviewResp.java     补 successCount/failCount/errorSamples
│   └── mapper/*.java + xml (按需加查询方法)
business-application-center/                    【下游同步】
├── src/main/java/.../service/LoanService.java  迁移到新 DTO 签名
└── src/main/java/.../service/SupportService.java  同上
```

### 测试文件
每个修改/新增类对应更新或创建单元测试 (facade/*Test) 或集成测试 (controller/*Test)，严格按 TDD 红线顺序执行。

---

# Phase 0: 基础设施 — DTO 与转换器

## Task 0.1: 创建 api/dto 包下的 9 个 DTO 类

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/CustomerDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/LeadDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/TagDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/CustClaimDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/TouchTaskDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/TouchLogDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/RunningFlowDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/CustomerFilterDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/TouchTaskSummaryDTO.java`

字段严格按 [04-对外API契约.md §6](../../../docs/modules/customer-marketing-center/04-对外API契约.md) 复制，每个类加 `@Data` + Javadoc 说明。此任务**没有业务逻辑**，属于纯结构，可跳过 TDD 循环 (无行为可测)，但**必须在 Task 0.2 建完后 → 编译通过 → 单独提交**。

- [ ] **Step 1: 创建 9 个 DTO 文件** 字段严格参考契约文档 §6

- [ ] **Step 2: 编译验证**

运行: `mvn -pl customer-marketing-center compile -am -DskipTests`
预期: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/
git commit -m "feat(customer): 新增 api/dto 包下的 9 个对外 DTO 类"
```

---

## Task 0.2: 创建 5 个 Entity → DTO 转换器 (带测试)

**Files:**
- Create: `.../api/converter/CustomerDTOConverter.java`
- Create: `.../api/converter/LeadDTOConverter.java`
- Create: `.../api/converter/TagDTOConverter.java`
- Create: `.../api/converter/CustClaimDTOConverter.java`
- Create: `.../api/converter/TouchTaskDTOConverter.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/api/converter/*Test.java` (5 个)

每个转换器类提供：
```java
public final class CustomerDTOConverter {
    private CustomerDTOConverter() {}
    
    public static CustomerDTO toDTO(CustMaster entity);
    public static List<CustomerDTO> toDTOList(List<CustMaster> entities);
}
```

tagIds 字段（JSON 字符串）在 CustomerDTOConverter 中解析为 `List<String>`；所有其他字段直接 BeanUtils 拷贝或手动映射。

### Step-by-step (以 CustomerDTOConverter 为例，其他 4 个同模式):

- [ ] **Step 1: 写失败测试 `CustomerDTOConverterTest.java`**

```java
@Test
void toDTO_mapsAllScalarFields() {
    CustMaster e = new CustMaster();
    e.setId("C001"); e.setCustNo("CUST20260421001");
    e.setCustName("某银行股份有限公司");
    e.setUnifiedCreditCode("91310000MA1234567X");
    e.setCreditAmount(new BigDecimal("500.00"));
    // ...其他字段

    CustomerDTO dto = CustomerDTOConverter.toDTO(e);

    assertThat(dto.getId()).isEqualTo("C001");
    assertThat(dto.getCustNo()).isEqualTo("CUST20260421001");
    assertThat(dto.getCustName()).isEqualTo("某银行股份有限公司");
    assertThat(dto.getUnifiedCreditCode()).isEqualTo("91310000MA1234567X");
    assertThat(dto.getCreditAmount()).isEqualByComparingTo("500.00");
}

@Test
void toDTO_parsesTagIdsJsonArray() {
    CustMaster e = new CustMaster();
    e.setTagIds("[\"TAG_001\",\"TAG_002\"]");
    CustomerDTO dto = CustomerDTOConverter.toDTO(e);
    assertThat(dto.getTagIds()).containsExactly("TAG_001","TAG_002");
}

@Test
void toDTO_returnsNullForNullEntity() {
    assertThat(CustomerDTOConverter.toDTO(null)).isNull();
}

@Test
void toDTOList_filtersNullAndMapsEach() {
    CustMaster a = new CustMaster(); a.setId("A");
    CustMaster b = new CustMaster(); b.setId("B");
    List<CustomerDTO> list = CustomerDTOConverter.toDTOList(Arrays.asList(a, b));
    assertThat(list).extracting(CustomerDTO::getId).containsExactly("A","B");
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn -pl customer-marketing-center test -Dtest=CustomerDTOConverterTest
```
预期: 编译失败 (class 不存在)

- [ ] **Step 3: 最小实现 `CustomerDTOConverter.java`**

```java
public final class CustomerDTOConverter {
    private CustomerDTOConverter() {}
    private static final ObjectMapper OM = new ObjectMapper();

    public static CustomerDTO toDTO(CustMaster e) {
        if (e == null) return null;
        CustomerDTO d = new CustomerDTO();
        BeanUtils.copyProperties(e, d, "tagIds");
        d.setTagIds(parseTagIds(e.getTagIds()));
        return d;
    }

    public static List<CustomerDTO> toDTOList(List<CustMaster> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().filter(Objects::nonNull)
                   .map(CustomerDTOConverter::toDTO)
                   .collect(Collectors.toList());
    }

    private static List<String> parseTagIds(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return OM.readValue(json, new TypeReference<List<String>>(){}); }
        catch (Exception ex) { return Collections.emptyList(); }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn -pl customer-marketing-center test -Dtest=CustomerDTOConverterTest
```
预期: Tests run: 4, Failures: 0

- [ ] **Step 5: 对其他 4 个转换器重复 Step 1-4**

LeadDTOConverter / TagDTOConverter / CustClaimDTOConverter / TouchTaskDTOConverter — 每个独立 Red-Green 循环，每个独立测试类。CustClaimDTOConverter 和 TouchTaskDTOConverter 的 `orgName/maintainerEmpName/custName` 等关联字段暂置为 null，后续在 Api Impl 里按需填充。

- [ ] **Step 6: 提交**

```bash
git add customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/converter/
git add customer-marketing-center/src/test/java/com/bank/branch/platform/customer/api/converter/
git commit -m "feat(customer): 新增 5 个 Entity→DTO 转换器并配套单测"
```

---

# Phase 1: 触达状态机修正

依据: [01-功能规格.md §7.3bis](../../../docs/modules/customer-marketing-center/01-功能规格.md)

目标状态机:
```
INSERT → PENDING
  ├─ 首次 POST /logs → IN_PROGRESS
  ├─ 追加 POST /logs (状态=IN_PROGRESS不变)
  ├─ POST /success (PENDING|IN_PROGRESS → SUCCESS)
  └─ POST /cancel  (PENDING|IN_PROGRESS → CANCELLED)
```

## Task 1.1: 扩展 TouchTaskStatus 加 IN_PROGRESS

**Files:**
- Modify: `.../enums/TouchTaskStatus.java`
- Test: `.../enums/TouchTaskStatusTest.java` (新增)

- [ ] **Step 1: 写测试**

```java
@Test
void hasAllFourStatuses() {
    assertThat(TouchTaskStatus.values())
        .extracting(TouchTaskStatus::getCode)
        .containsExactly("PENDING","IN_PROGRESS","SUCCESS","CANCELLED");
}
@Test
void inProgressIsTransientState() {
    assertThat(TouchTaskStatus.IN_PROGRESS.getLabel()).isEqualTo("进行中");
}
```

- [ ] **Step 2: 运行测试失败** → 预期: `cannot find symbol IN_PROGRESS`

- [ ] **Step 3: 在枚举中加一项**

```java
PENDING("PENDING","待处理"),
IN_PROGRESS("IN_PROGRESS","进行中"),
SUCCESS("SUCCESS","已完成"),
CANCELLED("CANCELLED","已取消");
```

- [ ] **Step 4: 运行测试通过**

- [ ] **Step 5: 提交** `feat(customer): TouchTaskStatus 补齐 IN_PROGRESS 状态`

---

## Task 1.2: TouchTaskStateMachineService 状态转移校验

**Files:**
- Create: `.../service/TouchTaskStateMachineService.java`
- Test: `.../service/TouchTaskStateMachineServiceTest.java`

封装合法转移表，抛 `BizException(CUST-40007)` 于非法转移。

```java
public final class TouchTaskStateMachineService {
    private static final Map<TouchTaskStatus, Set<TouchTaskStatus>> ALLOWED = Map.of(
        PENDING,     Set.of(IN_PROGRESS, SUCCESS, CANCELLED),
        IN_PROGRESS, Set.of(SUCCESS, CANCELLED),
        SUCCESS,     Set.of(),           // 终态
        CANCELLED,   Set.of()            // 终态
    );

    public void assertTransition(TouchTaskStatus from, TouchTaskStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION);
        }
    }
}
```

- [ ] **Step 1-4:** 先写 Red 测试（PENDING→IN_PROGRESS / IN_PROGRESS→SUCCESS / SUCCESS→PENDING 抛异常 / CANCELLED→任何 抛异常），再实现
- [ ] **Step 5: 在 CustomerErrorCode 加 `TOUCH_TASK_ILLEGAL_TRANSITION("CUST-40010","触达任务非法状态转移")`**
- [ ] **Step 6: 提交** `feat(customer): 新增 TouchTaskStateMachineService 封装状态转移规则`

---

## Task 1.3: TouchLogService 首次日志触发 PENDING → IN_PROGRESS

**Files:**
- Modify: `.../service/TouchLogService.java`
- Modify: `.../service/TouchTaskService.java` 加 `markInProgress(taskId)`
- Test: `.../service/TouchLogServiceTest.java` (扩展)

- [ ] **Step 1: 新增/扩展测试**

```java
@Test
void addLog_firstLogTransitionsPendingToInProgress() {
    // given: 一个 PENDING 的 task
    TouchTask task = pendingTask("T001");
    when(touchTaskMapper.selectById("T001")).thenReturn(task);
    when(touchLogMapper.countByTaskId("T001")).thenReturn(0L);  // 无历史日志

    // when
    service.addLog("T001", "UUID-1", "首次访谈", null, "E001", "ORG001");

    // then
    verify(touchTaskService).markInProgress("T001");
}

@Test
void addLog_subsequentLogKeepsInProgress() {
    TouchTask task = inProgressTask("T001");
    when(touchTaskMapper.selectById("T001")).thenReturn(task);
    when(touchLogMapper.countByTaskId("T001")).thenReturn(1L);  // 已有日志

    service.addLog("T001", "UUID-2", "二次跟进", null, "E001", "ORG001");

    verify(touchTaskService, never()).markInProgress(anyString());
}
```

- [ ] **Step 2: 运行失败**

- [ ] **Step 3: TouchTaskService 加 `markInProgress(taskId)`**

```java
public void markInProgress(String taskId) {
    TouchTask t = requireById(taskId);
    TouchTaskStatus from = TouchTaskStatus.valueOf(t.getTaskStatus());
    stateMachine.assertTransition(from, TouchTaskStatus.IN_PROGRESS);
    t.setTaskStatus("IN_PROGRESS");
    mapper.updateById(t);
}
```

- [ ] **Step 4: TouchLogService.addLog 末尾加状态转移**

```java
// 记录日志插入成功后
long count = touchLogMapper.countByTaskId(taskId);
if (count == 1L && PENDING.name().equals(task.getTaskStatus())) {
    touchTaskService.markInProgress(taskId);
}
```

- [ ] **Step 5: TouchLogMapper.xml 加 countByTaskId SQL**

- [ ] **Step 6: 运行测试通过**

- [ ] **Step 7: 提交** `feat(customer): 首次触达日志自动驱动 PENDING→IN_PROGRESS`

---

## Task 1.4: TouchTaskController `/complete` 改为 `/success` + 新增 markSuccess

**Files:**
- Modify: `.../controller/TouchTaskController.java`
- Modify: `.../service/TouchTaskService.java` 加 `markSuccess(taskId, ...)`
- Test: `.../controller/TouchTaskControllerTest.java` (更新/扩展)

- [ ] **Step 1: 写/改测试**
  - 新增 `success_returnsOk` 打 `/api/touch-tasks/{id}/success`
  - 删除原 `complete_returnsOk`（若存在）
  - 扩展 `success_transitionsFromInProgressToSuccess`

- [ ] **Step 2: 运行失败**

- [ ] **Step 3: Controller 改端点**

```java
@PostMapping("/{id}/success")
@BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.WRITE)
@AuditLog(action = "MARK_TOUCH_TASK_SUCCESS", resourceType = "TOUCH_TASK")
public ResponseWrapper<Void> markSuccess(@PathVariable String id) {
    touchTaskService.markSuccess(id);
    return ResponseWrapper.success();
}
```

- [ ] **Step 4: Service markSuccess (状态机校验 → SUCCESS, actualFinishAt=now, 发布 TouchCompletedEvent)**

- [ ] **Step 5: 删除/标记 `/complete` 端点**（直接删除；module CLAUDE.md 需同步更新）

- [ ] **Step 6: 运行测试通过**

- [ ] **Step 7: 提交** `feat(customer): 触达任务端点 /complete → /success + markSuccess 服务`

---

## Task 1.5: 补 TouchTask.sla_warning 字段 & 同步 SlaStatus 去留决策

**Files:**
- Modify: `.../entity/TouchTask.java` (加 `Boolean slaWarning`)
- Modify: `.../mapper/TouchTaskMapper.xml`
- 检查: [05-表结构DDL.md §触达表](../../../docs/modules/customer-marketing-center/05-表结构DDL.md) 确认字段类型
- Test: `.../service/TouchTaskServiceTest.java` SLA 相关测试调整

SLA 文档口径是 `sla_deadline + sla_warning (布尔)`，代码用 `slaStatus` 枚举 (GREEN/YELLOW/RED)。决策：
- **保留现有 slaStatus 枚举**（向后兼容，内部逻辑已依赖）
- **新增 slaWarning 布尔列**（文档要求，DTO 映射使用）
- 定时任务 `refreshSlaStatus` 同时维护两个字段

- [ ] **Step 1-5: TDD 循环**，增加 slaWarning 布尔列 + 同步刷新逻辑

- [ ] **Step 6: 提交** `feat(customer): 触达任务补 sla_warning 布尔列，与 slaStatus 并存维护`

---

# Phase 2: CustomerQueryApi 全面对齐 (含下游迁移)

## Task 2.1: CustomerQueryApi 接口签名全替换

**Files:**
- Modify: `.../api/CustomerQueryApi.java`
- Modify: `.../facade/CustomerQueryApiImpl.java`
- Modify: `.../mapper/CustMasterMapper.java` + xml (加 `selectByIds` / `searchByKeyword` / `countByFilter`)
- Test: `.../facade/CustomerQueryApiImplTest.java` (全面重写)

**签名最终形态** (契约 §1.1 原样)：
```java
Optional<CustomerDTO> getCustomer(String custId);
List<CustomerDTO> listCustomers(List<String> custIds);         // 新增, max 500
List<CustomerDTO> searchCustomers(String keyword, int limit);  // 新增, max 50
boolean isValidCustomer(String custId);                        // 保留
boolean isClaimedByOrg(String custId, String orgCode);         // 保留
List<CustClaimDTO> getCustomerClaims(String custId);           // 返回 DTO 列表
boolean hasRunningProcess(String custId, String bizType);      // 新增 (依赖 WorkflowApi)
List<RunningFlowDTO> listRunningProcesses(String custId);      // 新增
long countCustomers(CustomerFilterDTO filter);                 // 新增
```

**删除**：`getCustomerByCustNo(String)` — 契约未定义，仅内部 Service 使用的话移到 `CustomerService`

### Step-by-step (简版)

- [ ] **Step 1: 先写失败测试覆盖所有 9 个方法，含边界**
  - `listCustomers_throwsWhenMoreThan500`
  - `searchCustomers_throwsWhenLimitExceeds50`
  - `searchCustomers_throwsWhenKeywordBlank`
  - `getCustomer_returnsOptionalEmpty_whenNotFound`
  - `hasRunningProcess_delegatesToWorkflowApi`
  - `countCustomers_appliesAllFilterFields`
  - 其他契约明确的边界

- [ ] **Step 2: 运行失败**

- [ ] **Step 3: 改 interface + Impl**
  - 注入 `WorkflowApi` 实现 `hasRunningProcess` / `listRunningProcesses`
  - `CustomerFilterDTO` → 动态 SQL (可在 xml `<where>` 组合)

- [ ] **Step 4: 新增 Mapper 方法 + xml**

- [ ] **Step 5: 运行测试通过**

- [ ] **Step 6: 提交** `feat(customer): CustomerQueryApi 对齐契约 — 返回 DTO + 新增 4 方法`

---

## Task 2.2: 下游迁移 — business-application-center 适配新签名

**Files:**
- Modify: `business-application-center/.../service/LoanService.java`
- Modify: `business-application-center/.../service/SupportService.java`
- Test: `business-application-center/.../service/LoanServiceTest.java`
- Test: `business-application-center/.../service/SupportServiceTest.java`

当前调用点 (见探索报告)：
- `LoanService:76` `customerQueryApi.isValidCustomer(...)` ✅ 无变化
- `LoanService:84` `customerQueryApi.isClaimedByOrg(...)` ✅ 无变化
- `LoanService:93` `touchTaskQueryApi.getTaskById(...)` ❌ 需改 `getTouchTask(...).orElseThrow(...)`
- `SupportService:65` `customerQueryApi.isValidCustomer(...)` ✅ 无变化

- [ ] **Step 1: 更新 LoanService 的 getTaskById 调用** (Phase 6 时统一处理，此任务占位)

- [ ] **Step 2: 如 LoanService 曾引用 `CustMaster` 等 entity，改为 `CustomerDTO`**

- [ ] **Step 3: 运行 business-application-center 测试**

```bash
mvn -pl business-application-center test
```
预期: 全部通过

- [ ] **Step 4: 提交** `refactor(bizapp): 适配 customer api 新 DTO 签名`

---

## Task 2.3: CustomerController 列表详情转 DTO 返回

**Files:**
- Modify: `.../controller/CustomerController.java`
- Modify: `.../controller/CustomerPoolController.java`
- Test: `.../controller/CustomerControllerTest.java`
- Test: `.../controller/CustomerPoolControllerTest.java`

REST 返回也统一 DTO（Web 层本就应以 DTO 出入，避免 entity 泄漏）。复用 Phase 0 转换器。

- [ ] **Step 1-5: TDD 循环** - 断言响应 JSON 字段与 `CustomerDTO` 对齐

- [ ] **Step 6: 提交** `refactor(customer): Customer REST 响应改为 CustomerDTO`

---

# Phase 3: LeadApi 对齐

## Task 3.1: LeadApi 方法 + Impl + Mapper

**Files:**
- Modify: `.../api/LeadApi.java`
- Modify: `.../facade/LeadApiImpl.java`
- Modify: `.../mapper/CustLeadMapper.java` + xml (加 `selectByBusinessKey` / `selectByImportBatchId` / `selectVersionChain` / `countByCustName`)
- Test: `.../facade/LeadApiImplTest.java`

**最终签名** (契约 §2.1)：
```java
Optional<LeadDTO> getLead(String leadId);
Optional<LeadDTO> getLeadByBusinessKey(String businessKey);
List<LeadDTO> getLeadsByBatch(String importBatchId);
List<LeadDTO> getLeadVersionChain(String leadId);
boolean isLeadCustNameAvailable(String custName, String excludeLeadId);
```

**删除**: `getById`, `getByLeadNo` (契约未定义；若需要 `byLeadNo` 则作为 `Optional<LeadDTO> getLeadByLeadNo(String)` 扩展)

- [ ] **Step 1-5**: TDD 循环，重点测试版本链遍历 (prev_lead_id 递归/查询) 与 businessKey 前缀解析

- [ ] **Step 6: 提交** `feat(customer): LeadApi 对齐契约 — 5 方法 + DTO`

---

# Phase 4: TagApi 对齐

## Task 4.1: TagApi 方法 + Impl + Mapper

**Files:**
- Modify: `.../api/TagApi.java`
- Modify: `.../facade/TagApiImpl.java`
- Modify: `.../mapper/CustTagMapper.java` + `CustTagRelMapper.java` + xml
- Test: `.../facade/TagApiImplTest.java`

**最终签名**：
```java
List<TagDTO> listEnabledTags();
Optional<TagDTO> getTagByCode(String tagCode);
List<TagDTO> getCustomerTags(String custId);
Map<String, List<TagDTO>> batchGetCustomerTags(List<String> custIds);
List<String> getCustomerIdsByTag(String tagId);
boolean isTagNameExists(String tagName);
```

- [ ] **Step 1-5**: TDD 循环 — 重点 `batchGetCustomerTags` (max 500 校验 + N+1 规避: 单次 IN 查询 + Map 分组)

- [ ] **Step 6: 提交** `feat(customer): TagApi 对齐契约 — 6 方法 + DTO`

---

# Phase 5: ClaimApi 对齐

## Task 5.1: ClaimApi 方法 + Impl + Mapper

**Files:**
- Modify: `.../api/ClaimApi.java`
- Modify: `.../facade/ClaimApiImpl.java`
- Modify: `.../mapper/CustClaimMapper.java` + xml (加 `selectActiveByEmp` / `selectActiveByOrg` / `countActiveByEmp` / `countActiveByOrg`)
- Test: `.../facade/ClaimApiImplTest.java`

**最终签名**：
```java
Optional<CustClaimDTO> getClaim(String custId, String orgCode);
List<CustClaimDTO> getEmpClaims(String empId);
List<CustClaimDTO> getOrgClaims(String orgCode);
boolean isClaimActive(String custId, String orgCode);
long countEmpClaims(String empId);
long countOrgClaims(String orgCode);
```

**注意**: 所有 list 方法只返回 `claim_status=CLAIMED` 的记录 (契约明示)

- [ ] **Step 1-5**: TDD 循环

- [ ] **Step 6: 提交** `feat(customer): ClaimApi 对齐契约 — 6 方法 + DTO`

---

# Phase 6: TouchTaskQueryApi 对齐 + 下游修复

## Task 6.1: TouchTaskQueryApi 方法 + Impl + Mapper

**Files:**
- Modify: `.../api/TouchTaskQueryApi.java`
- Modify: `.../facade/TouchTaskQueryApiImpl.java`
- Modify: `.../mapper/TouchTaskMapper.java` + xml
- Test: `.../facade/TouchTaskQueryApiImplTest.java`

**最终签名**:
```java
Optional<TouchTaskDTO> getTouchTask(String taskId);       // 原 getTaskById
Optional<TouchTaskDTO> getTouchTaskByBusinessKey(String businessKey);
List<TouchTaskDTO> getEmpTouchTasks(String empId, String status);
int countRunningTouchTasks(String empId);                 // PENDING + IN_PROGRESS
List<TouchTaskDTO> getCustomerTouchHistory(String custId);
List<TouchTaskDTO> getCustomerTouchHistoryByOrg(String custId, String orgCode);
boolean hasCompletedFirstTouch(String custId, String orgCode);
TouchTaskSummaryDTO getOrgTouchSummary(String orgCode, String startDate, String endDate);
```

**删除**: `getTaskById` / `getTaskStatus` / `getSlaStatus` — 契约未定义，全部替换

- [ ] **Step 1-5**: TDD 循环，重点测试 `hasCompletedFirstTouch` 语义 (taskType=FIRST_TOUCH + status=SUCCESS + 同机构)

- [ ] **Step 6: 提交** `feat(customer): TouchTaskQueryApi 对齐契约 — 8 方法 + DTO`

---

## Task 6.2: 下游修复 — LoanService 改 getTaskById → getTouchTask

**Files:**
- Modify: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/LoanService.java:93`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/LoanServiceTest.java`

LoanService 当前代码：
```java
TouchTask task = touchTaskQueryApi.getTaskById(sourceTouchTaskId);
if (task == null || !operatorEmpId.equals(task.getAssigneeEmpId())) {
    // ...
}
```

改为：
```java
TouchTaskDTO task = touchTaskQueryApi.getTouchTask(sourceTouchTaskId)
    .orElseThrow(() -> new BizException(BusinessApplicationErrorCode.TOUCH_TASK_NOT_FOUND));
if (!operatorEmpId.equals(task.getAssigneeEmpId())) { ... }
```

- [ ] **Step 1: LoanServiceTest 增加/修改 mock**

- [ ] **Step 2: 运行失败 — 编译错**

- [ ] **Step 3: 修改调用代码 + import**

- [ ] **Step 4: 运行测试通过 `mvn -pl business-application-center test`**

- [ ] **Step 5: 提交** `refactor(bizapp): LoanService 适配 TouchTaskQueryApi 新签名`

---

## Task 6.3: 核验 bootstrap 集成测试全绿

**Files:** 无 — 只读验证

- [ ] **Step 1: 全项目编译**

```bash
mvn clean install -DskipTests
```
预期: BUILD SUCCESS

- [ ] **Step 2: 全项目测试**

```bash
mvn test
```
预期: Tests run: ???, Failures: 0, Errors: 0

如有失败，**不继续下一阶段** — 先修复

---

# Phase 7: REST Controller 补齐 (高危端点)

## Task 7.1: 跨机构全量历史查询 `/api/customers/{id}/history`

**Files:**
- Create: `.../controller/CustomerHistoryController.java`
- Modify: `.../service/CustomerService.java` 加 `getCrossOrgHistory(custId)` 方法
- Modify: `.../mapper/CustMasterMapper.java` + `CustClaimMapper.java`
- Test: `.../controller/CustomerHistoryControllerTest.java`

**依据:** [01-功能规格.md §4.7](../../../docs/modules/customer-marketing-center/01-功能规格.md) — 独立授权 `CROSS_ORG_VIEW` + 单独审计 + 月度专项

设计:
```java
@GetMapping("/api/customers/{id}/history")
@BizAuth(bizType = BizType.CUSTOMER, action = BizAction.READ, requireCrossOrg = true)
@AuditLog(action = "VIEW_CROSS_ORG_CUSTOMER", resourceType = "CUSTOMER", specialCategory = "CROSS_ORG")
public ResponseWrapper<CustomerCrossOrgHistoryVO> getHistory(@PathVariable String id) { ... }
```

- [ ] **Step 0: 检查 common-security 的 `@BizAuth` 是否支持 `requireCrossOrg` 属性** — 如不支持，在 common-security 加，或用独立注解 `@CrossOrgAuth`

- [ ] **Step 1-5: TDD 循环** — 测试未授权返回 403 / 正常返回聚合 VO

- [ ] **Step 6: 提交** `feat(customer): 新增跨机构客户历史查询端点与独立审计`

---

## Task 7.2: 客户打标 / 取消打标

**Files:**
- Create: `.../controller/CustomerTagController.java`
- Modify: `.../service/TagCustomerService.java` 加 `addTagToCustomer` / `removeTagFromCustomer`
- Test: `.../controller/CustomerTagControllerTest.java`

端点:
- `POST /api/customers/{id}/tags` body: `{tagIds: [...]}` — 追加打标 (非覆盖)
- `DELETE /api/customers/{id}/tags/{tagId}`

- [ ] **Step 1-5: TDD 循环**

- [ ] **Step 6: 提交** `feat(customer): 客户打标/取消打标 REST 端点`

---

## Task 7.3: 导出端点 (客户 / 标签客户 / 触达)

**Files:**
- Create: `.../controller/CustomerExportController.java`
- Modify: `.../controller/TagCustomerController.java` 加 `export`
- Modify: `.../controller/TouchReportController.java` 的 `export` (文档预留，实际未实现)
- Test: 对应 `*Test.java`

端点 (全部高危 `@AuditLog reasonRequired=true`):
- `GET /api/customers/export` — 客户列表导出
- `GET /api/tags/{tagId}/customers/export` — 标签客户导出
- `GET /api/touch-reports/export` — 触达报表导出 (已存在，补实现)

使用 EasyExcel (customer-marketing-center pom 已引入)。大数据量用流式写出。

- [ ] **Step 1-5: TDD 循环** — 测试响应 Content-Type / 文件名 / 审计触发

- [ ] **Step 6: 提交** `feat(customer): 补齐客户/标签/触达导出端点与高危审计`

---

## Task 7.4: 线索版本历史 `/api/leads/{id}/versions`

**Files:**
- Create: `.../controller/LeadVersionController.java` (或扩展 `LeadController`)
- Modify: `.../service/LeadVersionService.java` 加 `listVersionChain`
- Test: 对应 `*Test.java`

- [ ] **Step 1-5: TDD 循环** — 验证按 version_no 升序

- [ ] **Step 6: 提交** `feat(customer): 新增 GET /api/leads/{id}/versions 版本链端点`

---

## Task 7.5: 管理后台 `/api/admin/touch-tasks/*`

**Files:**
- Create: `.../controller/admin/AdminTouchTaskController.java`
- Modify: `.../service/TouchTaskService.java` 加 `batchAssign(taskIds, newEmpId)`
- Test: `.../controller/admin/AdminTouchTaskControllerTest.java`

端点:
- `GET /api/admin/touch-tasks` — 全局 (不限机构) 列表 (需 `ADMIN_TOUCH_VIEW` 权限)
- `GET /api/admin/touch-tasks/export` — 导出 (高危)
- `POST /api/admin/touch-tasks/batch-assign` — 批量分配 (高危 @AuditLog)

- [ ] **Step 1-5: TDD 循环**

- [ ] **Step 6: 提交** `feat(customer): 新增管理后台触达任务端点 (列表/导出/批量分配)`

---

# Phase 8: 细节打磨

## Task 8.1: WorkflowCallbackListener 改 @TransactionalEventListener(AFTER_COMMIT)

**Files:**
- Modify: `.../listener/WorkflowCallbackListener.java`
- Test: `.../listener/WorkflowCallbackListenerTest.java` (新增或扩展)

当前用 `@EventListener`，违反文档 06 §事务边界 "回调在事务提交后" 要求，风险: 事务未提交监听器读不到最新数据。

- [ ] **Step 1: 写测试**（构造一个 `ProcessCompletedEvent`, 断言在提交后才被调用）

- [ ] **Step 2-4:** 改注解并运行

- [ ] **Step 5: 提交** `fix(customer): WorkflowCallbackListener 改为 AFTER_COMMIT 事务回调`

---

## Task 8.2: LeadImportPreviewResp 补字段

**Files:**
- Modify: `.../dto/resp/LeadImportPreviewResp.java`
- Modify: `.../service/LeadImportService.java` 预览返回赋值
- Test: `.../service/LeadImportServiceTest.java` / `.../controller/LeadImportControllerTest.java`

追加字段 (契约 01 §3.8)：
```java
private Integer successCount;              // 可成功导入行数
private Integer failCount;                 // 失败行数
private List<LeadImportErrorVO> errorSamples;  // 错误样例 (最多 10 条)
```

- [ ] **Step 1-5: TDD 循环**

- [ ] **Step 6: 提交** `feat(customer): LeadImportPreviewResp 补 success/fail/errorSamples 字段`

---

## Task 8.3: 更新模块 CLAUDE.md 与 `PT_RESOURCE` SQL

**Files:**
- Modify: `customer-marketing-center/CLAUDE.md`
- Create: `docs/superpowers/sql/2026-04-21-customer-contract-alignment-pt-resource.sql`

- [ ] **Step 1: CLAUDE.md 更新**
  - TouchTaskStatus 值表修正 (SUCCESS 名称一致化)
  - API 表列出 20+ 新方法
  - 新增 REST 端点 (导出/打标/跨机构历史/管理后台/版本链)

- [ ] **Step 2: 新增 PT_RESOURCE SQL**
  - 为新增的 10+ 端点注册资源编码
  - 包括 `ADMIN_TOUCH_VIEW`, `CROSS_ORG_VIEW` 等特殊权限

- [ ] **Step 3: 提交** `docs(customer): 更新 CLAUDE.md 与 PT_RESOURCE 登记新端点`

---

# 验收 (所有 Phase 完成后)

## 最终整体检查

- [ ] **Step 1: 全项目编译**

```bash
mvn clean install -DskipTests
```
预期: BUILD SUCCESS

- [ ] **Step 2: 全项目测试**

```bash
mvn test
```
预期: Tests run: (原 142 + 新增 100+), Failures: 0, Errors: 0

- [ ] **Step 3: customer-marketing-center 启动验证**

```bash
cd bootstrap && mvn spring-boot:run
```
打开 `http://localhost:8080/doc.html` 核对:
- ✅ 36 → 50+ 端点
- ✅ 响应 DTO 字段符合契约
- ✅ `/api/touch-tasks/{id}/success` 存在, `/complete` 不存在
- ✅ 无跨模块编译错误

- [ ] **Step 4: 契约 checkpoint 清单**

对照 [04-对外API契约.md](../../../docs/modules/customer-marketing-center/04-对外API契约.md):
- [ ] CustomerQueryApi 9 方法全部落地且返回类型匹配
- [ ] LeadApi 5 方法
- [ ] TagApi 6 方法
- [ ] ClaimApi 6 方法
- [ ] TouchTaskQueryApi 8 方法
- [ ] 9 个 DTO 字段完整
- [ ] 所有 `*Api` 不暴露 entity

对照 [01-功能规格.md §7.3bis](../../../docs/modules/customer-marketing-center/01-功能规格.md):
- [ ] IN_PROGRESS 状态存在
- [ ] 首次日志 → PENDING→IN_PROGRESS
- [ ] SUCCESS/CANCELLED 为终态
- [ ] `/success` `/cancel` 端点均工作

对照 [07-审计要求.md](../../../docs/modules/customer-marketing-center/07-审计要求.md):
- [ ] 3 类导出端点均 @AuditLog
- [ ] 跨机构历史独立审计 (specialCategory=CROSS_ORG)
- [ ] 批量分配 @AuditLog

---

# 风险与回退

| 风险 | 触发场景 | 回退策略 |
|------|---------|---------|
| business-application-center 测试红 | Task 2.2 或 6.2 适配不完整 | 回滚 Task 6.1 或补强 Mock |
| 签名破坏式变更影响 performance-engine | performance-engine-center 编译失败 | 搜索使用点并同步迁移 |
| TDD 测试数据不足 | H2 不支持某些 SQL 特性 | 改为 Mockito 单测或使用 Testcontainers MySQL |
| 契约文档歧义 | 文档未明确字段行为 | 暂停任务、回到"分析 + 与用户确认" 循环 |

每个 Phase 末提供 checkpoint — Phase 7 之前每个阶段独立可回滚 (git revert)。

---

# 测试计划补充说明

## 测试分类

| 层级 | 用途 | 框架 | 路径 |
|------|------|------|------|
| 单元测试 | Service / Converter 纯逻辑 | JUnit 5 + Mockito | `src/test/java/.../service/*Test.java` |
| Facade 测试 | Api Impl 行为 | JUnit 5 + Mockito | `src/test/java/.../facade/*Test.java` |
| 集成测试 | Controller + H2 | MockMvc + Spring Boot Test | `src/test/java/.../controller/*Test.java` |
| Bootstrap IT | 端到端模块整合 | @SpringBootTest + H2 | `bootstrap/src/test/.../CustomerMarketingCenterIT.java` |

## 测试覆盖目标

- 新增代码 **行覆盖 ≥ 80%**
- 每个 `Api` 方法 **至少 3 个用例**：
  1. happy path (契约中的示例)
  2. 边界值 (max/min, Optional.empty, 空 List)
  3. 异常路径 (BizException / IllegalArgument)
- 状态机测试 **矩阵全覆盖**：4 状态 × 4 状态 = 16 格，非法转移全部断言抛异常

## CI 门禁

在 Phase 8 前完成:
- `mvn test` 全绿
- `mvn -pl customer-marketing-center jacoco:report` 覆盖率 ≥ 80%

---

# 执行建议 (子代理调度)

## 任务并行度

| Phase | 可并行 | 理由 |
|-------|-------|------|
| Phase 0 | Task 0.1 / 0.2 串行 | 0.2 依赖 0.1 |
| Phase 1 | 1.1 → 1.2 → 1.3/1.4 → 1.5 串行 | 状态机依赖链 |
| Phase 2 | Task 2.1 → 2.2/2.3 | 接口改完才能改下游 |
| Phase 3 / 4 / 5 | **可并行** | 互不依赖 (不同 Api) |
| Phase 6 | Task 6.1 → 6.2 → 6.3 串行 | 接口→下游→IT |
| Phase 7 | 5 个 Task **可并行** | 不同 Controller |
| Phase 8 | 3 个 Task **可并行** | 文档/Listener/DTO 独立 |

## 子代理 prompt 模板

每个 Task 子代理应接收：
1. 任务编号 + 完整 Task 段落原文
2. 所在工作目录 (`D:\Project\oneplate\.worktrees\suspicious-kirch-8968cd`)
3. 完成条件：所有 Step 打勾 + 对应提交已完成
4. 必须遵守: TDD 红线 / UTF-8 编码 / Windows bash shell

---

**全文完。2026-04-21**
