# business-application-center 契约对齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task with a fresh sonnet subagent per task + two-stage review (spec reviewer + code quality reviewer, both sonnet). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 business-application-center 从 17/30 契约对齐度提升到 ≥27/30,消除所有 P0 阻断问题,修复大部分 P1 功能缺失,按余量推进 P2。

**Architecture:** 参照已完成的 customer-marketing-center 修复模式:api/dto + api/converter 分层、Entity 不出 Controller、`@TransactionalEventListener(AFTER_COMMIT)` 事件消费、Optional<T> 返回、高危操作强制 `@AuditLog`、状态机 Service 化。跨模块新字段通过 `CustomerQueryApi` 获取,避免表 JOIN 泄漏。

**Tech Stack:** Java 17 + Spring Boot 3.2.3 + MyBatis 3.0.3 + Flowable 7.0.1 + JUnit 5 + Mockito + AssertJ + MockMvc + H2 (MySQL 兼容模式)

**Branch:** 当前工作树已在 `claude/suspicious-kirch-8968cd` 分支(customer-marketing-center 对齐的延续)。本计划所有提交直接追加到该分支。

**Worktree:** `D:\Project\oneplate\.claude\worktrees\suspicious-kirch-8968cd\`

**成功标准:**
- 全部 Phase 任务测试绿(`mvn -f business-application-center/pom.xml clean test` 0 失败)
- `mvn -f bootstrap/pom.xml clean install` BUILD SUCCESS
- business-application-center 单模块测试数 ≥ 140(当前 114,新增 26+)
- 所有 P0 问题在 Phase 1 关闭
- 最终整体 review APPROVED 后合并到 master 并推送

**执行策略(用户偏好):**
- 每个 Task 由 fresh sonnet 子代理实现(`implementer`),Task 完成后并行调度 `spec reviewer` + `code quality reviewer`(均为 sonnet)
- **每个 Phase 末尾立即 `git push -u origin claude/suspicious-kirch-8968cd`**
- 全部 Phase 完成 + 最终 review 通过后:`git checkout master && git merge --no-ff claude/suspicious-kirch-8968cd && git push origin master`
- Task 内 implementer 自行 commit(保留细粒度 commit,不 squash)

---

## 文件结构概览

### 将新增的文件

```
business-application-center/src/main/java/com/bank/branch/platform/bizapp/
├── api/
│   ├── converter/
│   │   ├── LoanApplyDTOConverter.java        # Entity → DTO
│   │   └── SupportRequestDTOConverter.java   # Entity → DTO
│   └── dto/
│       ├── LoanApplyListItemDTO.java         # 列表条目(轻量,带 custName 冗余)
│       ├── SupportRequestListItemDTO.java    # 同上
│       ├── SupportRequestCreateRespDTO.java  # 含 submitGroupId + 子单列表
│       ├── SubmitRespDTO.java                # 提交响应(含 processInstanceId)
│       └── ParsedBusinessKey.java            # businessKey 解析 record
├── event/
│   ├── LoanRejectedEvent.java                # 已存在,仅补发布点
│   └── SupportRejectedEvent.java             # 【新增】场景 A/B 驳回事件
└── listener/
    └── (无新文件,改造现有 2 个)

business-application-center/src/test/java/com/bank/branch/platform/bizapp/
├── api/
│   └── converter/
│       ├── LoanApplyDTOConverterTest.java
│       └── SupportRequestDTOConverterTest.java
├── controller/
│   └── LoanExportAuditTest.java              # 验证 @AuditLog 生效
└── listener/
    └── WorkflowListenerTransactionTest.java  # 验证 AFTER_COMMIT 行为
```

### 将修改的文件

```
workflow-center/src/main/java/com/bank/branch/platform/workflow/listener/
└── ProcessCompletedListener.java             # 扩展事件载荷 + outcome/reason

business-application-center/src/main/java/com/bank/branch/platform/bizapp/
├── api/
│   ├── dto/
│   │   ├── LoanApplyDTO.java                 # 删 deleted + 加 custName
│   │   └── SupportRequestDTO.java            # 删 deleted + 加 custName/productName/supportDeptName
│   ├── LoanQueryApi.java                     # 参数 start/end → startTime/endTime
│   └── SupportQueryApi.java                  # 同上
├── controller/
│   ├── LoanController.java                   # 不再返回 Entity + 补 @AuditLog
│   ├── SupportController.java                # 同上 + create 返回类型变更
│   └── SupportDeptController.java            # 补 @AuditLog + dispatchRemark 透传
├── dto/
│   ├── req/DispatchReq.java                  # 补 dispatchRemark 字段
│   └── resp/LoanDetailResp.java              # 富化 custInfo + canOperate
├── enums/
│   └── SupportSourceType.java                # MANUAL → EXISTING_CUSTOMER
├── facade/
│   ├── LoanApiImpl.java                      # Batch 500 条上限
│   ├── SupportApiImpl.java                   # 同上
│   └── (其余 facade 按 DTO 字段变更同步)
├── listener/
│   ├── LoanWorkflowListener.java             # AFTER_COMMIT + REJECTED 分支 + 条件 UPDATE
│   └── SupportWorkflowListener.java          # 同上 + 新增 rejected 分支
├── mapper/
│   ├── LoanApplyMapper.java / .xml           # 条件 UPDATE 方法 + sum 只计 COMPLETED
│   └── SupportRequestMapper.java / .xml      # 条件 UPDATE 方法
└── service/
    ├── BizStateMachine.java                  # 补 Support IN_APPROVAL→REJECTED
    ├── LoanService.java                      # submit 返回 processInstanceId
    └── SupportService.java                   # 构造 SupportRequestCreateRespDTO

business-application-center/CLAUDE.md         # 更新端点/错误码/测试数

docs/superpowers/sql/
└── 2026-04-21-bizapp-contract-alignment-pt-resource.sql  # PT_RESOURCE 补登记
```

---

# Phase 1 — P0 阻断修复(必须全部完成)

**目标:** 消除所有 7 项 P0 严重偏差,使模块达到"可上线合规"基线(评分 ≥ 22/30)。

**Phase 结束动作:**
```bash
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd push -u origin claude/suspicious-kirch-8968cd
```

---

### Task 1.1: 扩展 workflow-center `ProcessCompletedEvent` 携带 outcome + reason

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/listener/ProcessCompletedListener.java`
- Test: `workflow-center/src/test/java/com/bank/branch/platform/workflow/listener/ProcessCompletedListenerTest.java` (新增或修改)

**背景:** 当前 `ProcessCompletedEvent(processInstanceId, businessKey)` 载荷只有 2 个字段,导致下游 bizapp 无法区分 APPROVED / REJECTED,全部回写为 COMPLETED(P0-4 根因)。本任务扩展 record 字段,publisher 读取流程变量 `approved` 和 `reason` 填充。

- [ ] **Step 1: 先写失败测试 `publishesEventWithOutcomeAndReason`**

```java
// workflow-center/src/test/java/.../listener/ProcessCompletedListenerTest.java
@Test
@DisplayName("流程完成时应发布携带 outcome/reason 的 ProcessCompletedEvent")
void publishesEventWithOutcomeAndReason() {
    // given: mock DelegateExecution 返回 approved=false, reason="金额超限"
    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getProcessInstanceId()).thenReturn("pi-1");
    when(execution.getVariable("approved")).thenReturn(Boolean.FALSE);
    when(execution.getVariable("reason")).thenReturn("金额超限");
    BizProcessMap map = new BizProcessMap();
    map.setBusinessKey("LOAN:la001");
    when(bizProcessMapMapper.selectByProcessInstanceId("pi-1")).thenReturn(map);

    // when
    listener.notify(execution);

    // then: 捕获发布事件,断言 outcome=REJECTED + reason 正确
    ArgumentCaptor<ProcessCompletedListener.ProcessCompletedEvent> captor =
        ArgumentCaptor.forClass(ProcessCompletedListener.ProcessCompletedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().outcome()).isEqualTo("REJECTED");
    assertThat(captor.getValue().reason()).isEqualTo("金额超限");
    assertThat(captor.getValue().businessKey()).isEqualTo("LOAN:la001");
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -f D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd/workflow-center/pom.xml -Dtest=ProcessCompletedListenerTest#publishesEventWithOutcomeAndReason test
```
Expected: FAIL — record 没有 `outcome()` / `reason()` 方法。

- [ ] **Step 3: 扩展 record + publisher 填充**

修改 `ProcessCompletedListener.java`:

```java
public record ProcessCompletedEvent(
        String processInstanceId,
        String businessKey,
        String outcome,   // APPROVED / REJECTED / CANCELLED
        String reason     // 可为 null
) {}
```

在 `notify()` 中:

```java
Object approved = execution.getVariable("approved");
String outcome = Boolean.FALSE.equals(approved) ? "REJECTED" : "APPROVED";
String reason = asString(execution.getVariable("reason"));
// ... 原有 biz_process_map 更新逻辑保持不变
eventPublisher.publishEvent(new ProcessCompletedEvent(
        processInstanceId, map.getBusinessKey(), outcome, reason));
```

`asString()` 工具方法:null-safe 转成 String。

- [ ] **Step 4: 运行新测试 + 全模块回归测试验证通过**

```bash
mvn -f D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd/workflow-center/pom.xml clean test
```
Expected: PASS(新增测试 + 所有既有测试)。

- [ ] **Step 5: Commit**

```bash
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd add workflow-center/
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd commit -m "feat(workflow): ProcessCompletedEvent 补 outcome/reason 供下游区分审批结果"
```

**通过标准:** workflow-center 全模块测试绿;`ProcessCompletedEvent` 有 4 个字段;publisher 正确读取 approved/reason。

---

### Task 1.2: 新增 API Converter 层 + DTO 删除 `deleted` 字段并补冗余展示字段

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/converter/LoanApplyDTOConverter.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/converter/SupportRequestDTOConverter.java`
- Modify: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/LoanApplyDTO.java` (删 `deleted`, 加 `custName`)
- Modify: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/SupportRequestDTO.java` (删 `deleted`, 加 `custName`/`productName`/`supportDeptName`)
- Modify: `facade/LoanApiImpl.java`, `facade/SupportApiImpl.java`, `facade/LoanQueryApiImpl.java`, `facade/SupportQueryApiImpl.java`, `facade/BizApplyQueryApiImpl.java` (改用 converter,注入 `CustomerQueryApi` / `ProductApi` / `OrgApi` 补冗余字段)
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/api/converter/LoanApplyDTOConverterTest.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/api/converter/SupportRequestDTOConverterTest.java`

**背景:** DTO 泄露 `deleted` 字段(P0-5),同时缺 `custName` / `productName` / `supportDeptName` 等消费方急需的冗余字段(P1-3)。本任务合并修复,引入独立 Converter 集中补齐。

- [ ] **Step 1: 写 `LoanApplyDTOConverterTest.toDTO_populatesCustNameFromQueryApi`**

```java
@ExtendWith(MockitoExtension.class)
class LoanApplyDTOConverterTest {
    @Mock CustomerQueryApi customerQueryApi;
    @InjectMocks LoanApplyDTOConverter converter;

    @Test
    @DisplayName("toDTO 应补齐 custName 且不输出 deleted 字段")
    void toDTO_populatesCustNameFromQueryApi() {
        LoanApply entity = new LoanApply();
        entity.setId("la001");
        entity.setCustId("cust001");
        entity.setCreditAmount(new BigDecimal("500000"));
        entity.setDeleted(0);
        CustomerDTO cust = new CustomerDTO();
        cust.setCustId("cust001");
        cust.setCustName("腾讯云计算");
        when(customerQueryApi.getCustomer("cust001")).thenReturn(Optional.of(cust));

        LoanApplyDTO dto = converter.toDTO(entity);

        assertThat(dto.getCustName()).isEqualTo("腾讯云计算");
        // deleted 字段已删 — 编译期即不存在 getter,此断言保证 API 不泄露
        assertThat(BeanUtils.getPropertyDescriptors(LoanApplyDTO.class))
            .noneMatch(pd -> pd.getName().equals("deleted"));
    }

    @Test
    @DisplayName("toDTO 当客户不存在时 custName 为空字符串,不抛异常")
    void toDTO_custNotFound_returnsEmptyName() {
        LoanApply entity = new LoanApply();
        entity.setCustId("ghost");
        when(customerQueryApi.getCustomer("ghost")).thenReturn(Optional.empty());
        LoanApplyDTO dto = converter.toDTO(entity);
        assertThat(dto.getCustName()).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试验证失败(类不存在)**

```bash
mvn -f D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd/business-application-center/pom.xml -Dtest=LoanApplyDTOConverterTest test
```
Expected: FAIL — `LoanApplyDTOConverter` 未定义。

- [ ] **Step 3: 新增 `LoanApplyDTOConverter` + 同步修改 DTO**

创建 Converter(注入 `CustomerQueryApi`):

```java
@Component
@RequiredArgsConstructor
public class LoanApplyDTOConverter {
    private final CustomerQueryApi customerQueryApi;

    public LoanApplyDTO toDTO(LoanApply entity) {
        if (entity == null) return null;
        LoanApplyDTO dto = new LoanApplyDTO();
        // 复制所有字段,但不包含 deleted
        dto.setId(entity.getId());
        dto.setApplyNo(entity.getApplyNo());
        dto.setCustId(entity.getCustId());
        dto.setCustName(resolveCustName(entity.getCustId()));
        // ... 其他字段
        return dto;
    }

    public List<LoanApplyDTO> toDTOList(List<LoanApply> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        // 批量场景:合并 custIds 一次查询避免 N+1
        Set<String> custIds = entities.stream()
            .map(LoanApply::getCustId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> custNameMap = customerQueryApi.getCustomersBatch(new ArrayList<>(custIds))
            .stream().collect(Collectors.toMap(CustomerDTO::getCustId, CustomerDTO::getCustName, (a, b) -> a));
        return entities.stream().map(e -> {
            LoanApplyDTO dto = toDTOWithoutCustName(e);  // 纯字段复制
            dto.setCustName(custNameMap.getOrDefault(e.getCustId(), ""));
            return dto;
        }).collect(Collectors.toList());
    }

    private String resolveCustName(String custId) {
        if (!StringUtils.hasText(custId)) return "";
        return customerQueryApi.getCustomer(custId).map(CustomerDTO::getCustName).orElse("");
    }
}
```

修改 `LoanApplyDTO.java`:
- **删除** `private Integer deleted;` 和对应 getter/setter
- **新增** `private String custName;` 及注释

修改所有 facade 实现,改用 `converter.toDTO(entity)` 替换 `BeanUtils.copyProperties` 或手动字段复制。`SupportRequestDTOConverter` 同构,额外依赖 `ProductApi`(`listByIds` 获取 `productName`)和 `OrgApi`(获取 `supportDeptName`)。

- [ ] **Step 4: 运行全模块测试**

```bash
mvn -f D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd/business-application-center/pom.xml clean test
```
Expected: 所有 facade 测试因 mock 签名变更可能需要微调(新增 `customerQueryApi.getCustomer(...)` stub),补齐后全绿。

- [ ] **Step 5: Commit**

```bash
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd add business-application-center/
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd commit -m "refactor(bizapp): API DTO 层去除 deleted + 引入 converter 补 custName/productName"
```

**通过标准:** `LoanApplyDTO` / `SupportRequestDTO` 无 `deleted` 字段;Converter 测试覆盖单条 + 批量 + 缺失场景;facade 测试全绿。

---

### Task 1.3: Controller 层停止直接返回 Entity(引入 ListItem DTO)

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/LoanApplyListItemDTO.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/SupportRequestListItemDTO.java`
- Modify: `controller/LoanController.java` (`listPage`, `getById` 返回类型)
- Modify: `controller/SupportController.java` (`listPage`, `getById`)
- Modify: `controller/SupportDeptController.java` (`listPageForDept`)
- Modify: `service/LoanService.java` / `SupportService.java` / `SupportDeptService.java` (新增 `listPageAsDTO` 方法或调整返回类型)
- Test: `controller/LoanControllerTest.java` / `SupportControllerTest.java` / `SupportDeptControllerTest.java` 同步修改断言

**背景:** 当前 3 个 Controller 的 `listPage` / `getById` 直接把 `LoanApply` / `SupportRequest` Entity 包进 `ResponseWrapper` 返回(P0-1),暴露 `deleted` 等内部字段。规范:REST 层禁止返回 Entity。

- [ ] **Step 1: 新增列表条目 DTO(纯展示,轻量)**

```java
// LoanApplyListItemDTO.java
@Data
public class LoanApplyListItemDTO {
    private String id;
    private String applyNo;
    private String custId;
    private String custName;       // 冗余展示
    private BigDecimal creditAmount;
    private String status;
    private String ownerOrgId;
    private LocalDateTime createdTime;
    // 列表不含敏感字段:不含 businessKey / processInstanceId / updatedBy 等
}
```

`SupportRequestListItemDTO` 包含 `id/requestNo/submitGroupId/custName/productName/supportDeptName/scenario/status/createdTime` 等。

- [ ] **Step 2: 写失败的 Controller 测试断言 `listPage` 返回 ListItemDTO**

```java
// LoanControllerTest
@Test
@DisplayName("GET /api/loans 返回 LoanApplyListItemDTO 列表,不含 deleted 字段")
void listPage_returnsListItemDTO_notEntity() throws Exception {
    LoanApplyListItemDTO item = new LoanApplyListItemDTO();
    item.setId("la001");
    item.setCustName("测试客户");
    PageResult<LoanApplyListItemDTO> page = PageResult.of(List.of(item), 1L, 1, 20);
    when(loanService.listPageAsDTO(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

    mockMvc.perform(get("/api/loans"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records[0].custName").value("测试客户"))
        .andExpect(jsonPath("$.data.records[0].deleted").doesNotExist());
}
```

- [ ] **Step 3: 运行测试验证失败**

```bash
mvn -f .../business-application-center/pom.xml -Dtest=LoanControllerTest#listPage_returnsListItemDTO_notEntity test
```
Expected: FAIL — `loanService.listPageAsDTO` 不存在。

- [ ] **Step 4: 实现 Service 新方法 + Controller 切换**

- 在 `LoanService` 中新增 `PageResult<LoanApplyListItemDTO> listPageAsDTO(...)`,内部 `listPage` 之后调用 `LoanApplyListItemDTOConverter.toListItems(entities)`(可复用 Task 1.2 的 converter)
- `LoanController.listPage` 返回类型改为 `ResponseWrapper<LoanApplyListItemDTO>`
- `LoanController.getById` 保持 `LoanDetailResp`(已经是 DTO,Task 2.5 进一步富化)
- 同步修改 `SupportController` / `SupportDeptController` + 对应 Service

- [ ] **Step 5: 运行测试验证通过 + Commit**

```bash
mvn -f .../business-application-center/pom.xml clean test
```

```bash
git add business-application-center/
git commit -m "refactor(bizapp): Controller 改用 ListItemDTO 返回,停止暴露 Entity"
```

**通过标准:** 3 个 Controller 的 `listPage` / `getById` 不再引用 `LoanApply` / `SupportRequest` Entity 类;响应 JSON 无 `deleted` 字段;Controller 测试断言更新并全绿。

---

### Task 1.4: `SupportController.create` 返回 `SupportRequestCreateRespDTO` + 枚举 MANUAL→EXISTING_CUSTOMER

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/SupportRequestCreateRespDTO.java`
- Modify: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/enums/SupportSourceType.java`
- Modify: `controller/SupportController.java` (create 返回类型)
- Modify: `service/SupportService.java` (返回值携带 submitGroupId)
- Modify: 所有使用 `SupportSourceType.MANUAL` 的 service / test 文件全量迁移
- Test: `controller/SupportControllerTest.java` 修改 create 断言
- Test: `service/SupportServiceTest.java` 修改枚举断言

**背景:** `POST /api/support-requests` 当前返回 `ResponseWrapper<List<String>>`(P0-2),消费方拿不到 `submitGroupId`,无法做同批追溯。同时 `SupportSourceType` 的 `MANUAL` 与文档契约的 `EXISTING_CUSTOMER` 不匹配(P0-6)。两者合并成一个任务,因为二者共同影响 create 流程。

- [ ] **Step 1: 先写 DTO 类 + 写失败测试**

```java
// SupportRequestCreateRespDTO.java
@Data
@Builder
public class SupportRequestCreateRespDTO {
    private String submitGroupId;
    private int productCount;
    private List<CreatedItem> requests;

    @Data
    @Builder
    public static class CreatedItem {
        private String id;
        private String requestNo;
        private String productId;
        private String scenario;
        private String processInstanceId;  // 提交后才有,这里是 create 草稿阶段,可为 null
    }
}
```

```java
// SupportControllerTest
@Test
@DisplayName("POST /api/support-requests 返回 SupportRequestCreateRespDTO 含 submitGroupId")
void create_returnsCreateRespWithSubmitGroupId() throws Exception {
    CreateSupportReq req = ...;
    SupportRequestCreateRespDTO resp = SupportRequestCreateRespDTO.builder()
        .submitGroupId("grp-001")
        .productCount(2)
        .requests(List.of(
            SupportRequestCreateRespDTO.CreatedItem.builder().id("sr001").scenario("A").build(),
            SupportRequestCreateRespDTO.CreatedItem.builder().id("sr002").scenario("A").build()))
        .build();
    when(supportService.create(...)).thenReturn(resp);

    mockMvc.perform(post("/api/support-requests")...)
        .andExpect(jsonPath("$.data.submitGroupId").value("grp-001"))
        .andExpect(jsonPath("$.data.productCount").value(2))
        .andExpect(jsonPath("$.data.requests", hasSize(2)));
}
```

```java
// SupportServiceTest
@Test
@DisplayName("SupportSourceType 字典:无 MANUAL,有 EXISTING_CUSTOMER 与 TOUCH_TASK")
void supportSourceType_enumContractMatchesDoc() {
    assertThat(SupportSourceType.values())
        .extracting(SupportSourceType::name)
        .containsExactlyInAnyOrder("EXISTING_CUSTOMER", "TOUCH_TASK");
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn ... -Dtest=SupportControllerTest#create_returnsCreateRespWithSubmitGroupId,SupportServiceTest#supportSourceType_enumContractMatchesDoc test
```
Expected: FAIL(类不存在 / 枚举不匹配)。

- [ ] **Step 3: 实施双修改**

- 修改 `SupportSourceType.java`: `MANUAL("MANUAL", "手动创建")` → `EXISTING_CUSTOMER("EXISTING_CUSTOMER", "存量客户")`
- 全局搜索 `SupportSourceType.MANUAL` 替换为 `EXISTING_CUSTOMER`(预计 ≤ 3 处:SupportService、测试夹具、可能的 SQL 注释)
- `SupportService.create(...)` 返回类型 `List<SupportRequest>` 改为 `SupportRequestCreateRespDTO`,内部生成 `submitGroupId`(UUID32 格式),组装 `CreatedItem`
- `SupportController.create` 返回类型 `ResponseWrapper<SupportRequestCreateRespDTO>`

- [ ] **Step 4: 运行全模块测试,修复连带失败**

```bash
mvn -f .../business-application-center/pom.xml clean test
```
预计需要修订 3-5 个既有测试(如 `SupportProductSplitServiceTest`、`SupportApiImplTest` 中关于 sourceType 的断言)。

- [ ] **Step 5: Commit**

```bash
git add business-application-center/
git commit -m "feat(bizapp): SupportController.create 返回 CreateRespDTO + 枚举 MANUAL→EXISTING_CUSTOMER"
```

**通过标准:** create 端点响应含 `submitGroupId`;`SupportSourceType` 仅含 2 个值且与文档一致;全模块测试绿。

---

### Task 1.5: Workflow Listener 改造(AFTER_COMMIT + REJECTED 分支 + 条件 UPDATE 幂等 + 新事件)

**Files:**
- Modify: `listener/LoanWorkflowListener.java`
- Modify: `listener/SupportWorkflowListener.java`
- Create: `event/SupportRejectedEvent.java`
- Modify: `mapper/LoanApplyMapper.java` + `.xml`(新增 `conditionalUpdateStatus(id, expectedStatus, targetStatus, updatedBy)`)
- Modify: `mapper/SupportRequestMapper.java` + `.xml`(同上)
- Modify: `enums/BizAppErrorCode.java`(如需新增错误码)
- Test: `listener/LoanWorkflowListenerTest.java` 补 rejected / 幂等 / AFTER_COMMIT 用例
- Test: `listener/SupportWorkflowListenerTest.java` 同上
- Create: `listener/WorkflowListenerTransactionTest.java`(SpringBootTest 验证 AFTER_COMMIT 行为)

**背景:** 4 个 P0 问题合并修复(P0-3 + P0-4 + P2-1):
- `@EventListener` → `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)`
- 读取 event 的 `outcome()` 字段,分派 APPROVED(→COMPLETED)/REJECTED(→REJECTED)
- 条件 UPDATE:`UPDATE loan_apply SET status=?, updated_by='SYSTEM' WHERE id=? AND status='IN_APPROVAL'`,返回 `rowsAffected`
- 只有 `rowsAffected > 0` 才发事件
- REJECTED 时发布 `LoanRejectedEvent` 或 `SupportRejectedEvent`(后者新增)

- [ ] **Step 1: 新增 `SupportRejectedEvent`**

```java
public record SupportRejectedEvent(
        String requestId,
        String requestNo,
        String custId,
        String createdBy,
        String rejectReason  // 来自流程 variable reason
) {}
```

- [ ] **Step 2: 写失败测试**

覆盖 4 个分支:

```java
// LoanWorkflowListenerTest
@Test
void onProcessCompleted_approved_updatesToCompleted_andPublishesApprovedEvent() { ... }

@Test
void onProcessCompleted_rejected_updatesToRejected_andPublishesRejectedEvent() {
    ProcessCompletedEvent event = new ProcessCompletedEvent("pi-1", "LOAN:la001", "REJECTED", "金额超限");
    LoanApply loan = new LoanApply();
    loan.setId("la001"); loan.setStatus("IN_APPROVAL"); loan.setApplyNo("LA001"); /* ... */
    when(loanMapper.selectById("la001")).thenReturn(loan);
    when(loanMapper.conditionalUpdateStatus("la001", "IN_APPROVAL", "REJECTED", "SYSTEM"))
        .thenReturn(1);

    listener.onProcessCompleted(event);

    verify(loanMapper).conditionalUpdateStatus("la001", "IN_APPROVAL", "REJECTED", "SYSTEM");
    ArgumentCaptor<LoanRejectedEvent> captor = ArgumentCaptor.forClass(LoanRejectedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().rejectReason()).isEqualTo("金额超限");
}

@Test
void onProcessCompleted_whenRowsAffectedZero_doesNotPublishEvent() {
    // 模拟并发:条件 UPDATE 返回 0(已被其他实例处理)
    when(loanMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(0);
    listener.onProcessCompleted(new ProcessCompletedEvent("pi-1", "LOAN:la001", "APPROVED", null));
    verify(eventPublisher, never()).publishEvent(any());
}
```

`WorkflowListenerTransactionTest`(SpringBootTest):

```java
@SpringBootTest
@Transactional
@Rollback
class WorkflowListenerTransactionTest {
    @Test
    @DisplayName("事务回滚时监听器不应写入数据库")
    void listenerSkipsWhenTransactionRollsBack() {
        // 在一个会回滚的事务内发布事件,验证 AFTER_COMMIT 阶段不触发 UPDATE
        // (用 MockBean 截获 mapper,断言 verify(mapper, never()))
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

```bash
mvn ... -Dtest=LoanWorkflowListenerTest,SupportWorkflowListenerTest,WorkflowListenerTransactionTest test
```
Expected: FAIL(新事件字段、conditional update 方法、AFTER_COMMIT 行为均未实现)。

- [ ] **Step 4: 实施改造**

Mapper:

```xml
<!-- LoanApplyMapper.xml -->
<update id="conditionalUpdateStatus">
    UPDATE loan_apply
    SET status = #{targetStatus}, updated_by = #{updatedBy}, updated_time = NOW()
    WHERE id = #{id} AND status = #{expectedStatus}
</update>
```

Listener 改造(示例):

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void onProcessCompleted(ProcessCompletedListener.ProcessCompletedEvent event) {
    String businessKey = event.businessKey();
    if (businessKey == null || !businessKey.startsWith("LOAN:")) return;
    String loanId = businessKey.substring("LOAN:".length());

    try {
        String targetStatus = "REJECTED".equals(event.outcome())
            ? LoanStatus.REJECTED.getCode()
            : LoanStatus.COMPLETED.getCode();

        int rowsAffected = loanMapper.conditionalUpdateStatus(
            loanId, LoanStatus.IN_APPROVAL.getCode(), targetStatus, "SYSTEM");
        if (rowsAffected == 0) {
            log.warn("[LoanWorkflowListener] 状态已被其他实例处理,跳过 id={}", loanId);
            return;
        }

        LoanApply loan = loanMapper.selectById(loanId);
        if ("REJECTED".equals(event.outcome())) {
            eventPublisher.publishEvent(new LoanRejectedEvent(
                loanId, loan.getApplyNo(), loan.getCustId(),
                loan.getCreatedBy(), event.reason()));
        } else {
            eventPublisher.publishEvent(new LoanApprovedEvent(
                loanId, loan.getApplyNo(), loan.getCustId(),
                loan.getOwnerOrgId(), loan.getCreditAmount()));
        }
    } catch (Exception e) {
        log.error("[LoanWorkflowListener] 处理异常", e);
    }
}
```

`SupportWorkflowListener` 同构,REJECTED 发布 `SupportRejectedEvent`。

- [ ] **Step 5: 运行测试 + Commit**

```bash
mvn -f .../business-application-center/pom.xml clean test
```

```bash
git add business-application-center/
git commit -m "fix(bizapp): Listener AFTER_COMMIT + REJECTED 分支 + 条件 UPDATE 幂等"
```

**通过标准:** 两个 Listener 均用 `@TransactionalEventListener(AFTER_COMMIT)`;`LoanRejectedEvent` / `SupportRejectedEvent` 在 REJECTED 时发布;`rowsAffected=0` 时不发事件;新增测试全绿。

---

### Task 1.6: 高危操作补齐 `@AuditLog`

**Files:**
- Modify: `controller/LoanController.java`(submit, cancel, delete, create, update, export)
- Modify: `controller/SupportController.java`(create, submit, cancel, delete, export)
- Modify: `controller/SupportDeptController.java`(dispatch, transfer, complete)
- Test: `controller/LoanExportAuditTest.java`(新建,验证注解存在)
- Test: 3 个 Controller Test 补 `@AuditLog` 元数据断言

**背景:** 所有写/导出接口当前仅有 `@BizAuth`,无 `@AuditLog`(P0-7)。`common` 模块的 `AuditLogAspect` 通过扫描 `@AuditLog` 方法发布 `AuditLogEvent` 到 governance。本任务为 14 个高危端点补齐注解。

- [ ] **Step 1: 写验证注解存在的测试(反射)**

```java
// LoanExportAuditTest.java
class LoanExportAuditTest {
    @Test
    @DisplayName("LoanController 的高危端点必须都有 @AuditLog")
    void allHighRiskEndpointsHaveAuditLog() throws Exception {
        Class<?> cls = LoanController.class;
        String[] methodsNeedAudit = {"submit", "cancel", "delete", "create", "update", "export"};
        for (String name : methodsNeedAudit) {
            Method m = Arrays.stream(cls.getDeclaredMethods())
                .filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
            assertThat(m.getAnnotation(AuditLog.class))
                .as("方法 %s 必须有 @AuditLog", name)
                .isNotNull();
        }
    }

    @Test
    @DisplayName("transfer 方法 @AuditLog 必须 reasonRequired=true")
    void transferRequiresReason() throws Exception {
        Method transfer = SupportDeptController.class.getDeclaredMethod(
            "transfer", String.class, TransferReq.class);
        AuditLog audit = transfer.getAnnotation(AuditLog.class);
        assertThat(audit).isNotNull();
        assertThat(audit.reasonRequired()).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn ... -Dtest=LoanExportAuditTest test
```
Expected: FAIL — `@AuditLog` 不存在。

- [ ] **Step 3: 补齐所有注解**

参考 docs/modules/business-application-center/07-审计要求.md §1 表格:

```java
// LoanController
@PostMapping("/{id}/submit")
@BizAuth(bizType = BizType.LOAN, action = BizAction.WRITE)
@AuditLog(action = "SUBMIT_LOAN_APPLY", resourceType = "LOAN_APPLY")
public ResponseWrapper<Void> submit(@PathVariable String id) { ... }

@PostMapping("/{id}/cancel")
@BizAuth(bizType = BizType.LOAN, action = BizAction.WRITE)
@AuditLog(action = "CANCEL_LOAN_APPLY", resourceType = "LOAN_APPLY", reasonRequired = true)
public ResponseWrapper<Void> cancel(@PathVariable String id) { ... }

@GetMapping("/export")
@BizAuth(bizType = BizType.LOAN, action = BizAction.EXPORT)
@AuditLog(action = "EXPORT_LOAN_APPLY", resourceType = "LOAN_APPLY", reasonRequired = true)
public ResponseWrapper<?> export() { ... }
```

清单(14 条):
- LoanController:create/update/delete/submit/cancel/export(6)
- SupportController:create/submit/delete/cancel/export(5)
- SupportDeptController:dispatch/transfer/complete(3),其中 transfer 必须 `reasonRequired=true`

- [ ] **Step 4: 运行全模块测试验证通过**

```bash
mvn -f .../business-application-center/pom.xml clean test
```

- [ ] **Step 5: Commit**

```bash
git add business-application-center/
git commit -m "feat(bizapp): 14 个高危端点补齐 @AuditLog(transfer/cancel/export reasonRequired)"
```

**通过标准:** 14 个高危端点全部带 `@AuditLog`;transfer/cancel/export 的 `reasonRequired=true`;新增反射测试全绿。

---

**Phase 1 结束动作:**

```bash
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd push -u origin claude/suspicious-kirch-8968cd
```

---

# Phase 2 — P1 功能完善(建议全部完成)

**目标:** 修复 P1 中的主要功能缺失,使评分达到 ≥ 25/30。

**Phase 结束动作:**
```bash
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd push origin claude/suspicious-kirch-8968cd
```

---

### Task 2.1: `LoanController.submit` / `SupportController.submit` 返回 `SubmitRespDTO`(含 processInstanceId)

**Files:**
- Create: `api/dto/SubmitRespDTO.java`
- Modify: `controller/LoanController.java` / `SupportController.java`
- Modify: `service/LoanService.java` / `SupportService.java` — submit 方法返回 `processInstanceId`
- Test: 对应 Controller/Service 测试补断言

**背景:** submit 返回 `Void`(P1-4),前端无法获取流程实例 ID 用于后续跳转。

- [ ] **Step 1: DTO + 写失败测试**

```java
@Data @AllArgsConstructor
public class SubmitRespDTO {
    private String processInstanceId;
    private String businessKey;
    private String status;  // "IN_APPROVAL"
}
```

```java
@Test
void submit_returnsProcessInstanceId() throws Exception {
    when(loanService.submitForApproval("la001", "e001", "org01"))
        .thenReturn(new SubmitRespDTO("pi-abc", "LOAN:la001", "IN_APPROVAL"));
    mockMvc.perform(post("/api/loans/la001/submit"))
        .andExpect(jsonPath("$.data.processInstanceId").value("pi-abc"))
        .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"));
}
```

- [ ] **Step 2-5:** 实现 Service 返回 DTO → Controller 封装 → 测试绿 → commit。

```bash
git commit -m "feat(bizapp): submit 返回 SubmitRespDTO 含 processInstanceId"
```

**通过标准:** loan/support 两个 submit 端点返回的 JSON 含 `processInstanceId`。

---

### Task 2.2: Batch API 500 条上限校验

**Files:**
- Modify: `facade/LoanApiImpl.java`(`getLoanApplyBatch`)
- Modify: `facade/SupportApiImpl.java`(`getSupportRequestBatch`)
- Test: `facade/LoanApiImplTest.java` / `SupportApiImplTest.java` 补超限用例

**背景:** P1-2,文档契约 §7.1 明确 500 条上限。

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("getLoanApplyBatch 入参超过 500 条应抛 IllegalArgumentException")
void getLoanApplyBatch_exceeds500_throws() {
    List<String> ids = Stream.generate(() -> UUID.randomUUID().toString())
        .limit(501).toList();
    assertThatThrownBy(() -> loanApi.getLoanApplyBatch(ids))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("500");
}
```

- [ ] **Step 2-5:** Facade 补 `if (ids.size() > 500) throw new IllegalArgumentException(...)` → 测试绿 → commit。

```bash
git commit -m "feat(bizapp): batch API 补 500 条上限校验"
```

---

### Task 2.3: `dispatchRemark` 透传 + dispatch/transfer/reject reasonRequired 链路完整

**Files:**
- Modify: `dto/req/DispatchReq.java` — 补 `dispatchRemark` 字段(文档 §D.2)
- Modify: `controller/SupportDeptController.java` — 透传 `dispatchRemark` 到 Service
- Modify: `service/SupportDeptService.java` — 接收 `dispatchRemark`,记录到 `updated_by`/事件中
- Modify: `event/SupportDispatchedEvent.java` — 补 `dispatchRemark` 字段(对齐文档 §8.5)
- Test: `SupportDeptControllerTest.java` / `SupportDeptServiceTest.java` 覆盖新字段

**背景:** P1-6,派单备注信息丢失,审计 reason 缺失。

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("dispatch 必须把 dispatchRemark 透传到 Service 和事件")
void dispatch_propagatesDispatchRemark() {
    DispatchReq req = new DispatchReq();
    req.setAssignedEmpId("e002");
    req.setDispatchRemark("请优先处理");

    controller.dispatch("sr001", req);

    verify(supportDeptService).dispatch(eq("sr001"), eq("e002"), eq("e001-ctx"), eq("请优先处理"));
}
```

- [ ] **Step 2-5:** 实施 → 测试绿 → commit。

```bash
git commit -m "feat(bizapp): dispatchRemark 透传至 Service+事件"
```

---

### Task 2.4: QueryApi 参数重命名 start/end → startTime/endTime

**Files:**
- Modify: `api/LoanQueryApi.java` / `api/SupportQueryApi.java` — 参数名
- Modify: `facade/LoanQueryApiImpl.java` / `facade/SupportQueryApiImpl.java`
- Modify: `mapper/LoanApplyMapper.java` / `mapper/SupportRequestMapper.java` — `@Param("startTime")`
- Modify: 对应 `.xml` 中的 `#{start}` / `#{end}` → `#{startTime}` / `#{endTime}`
- Modify: `service/BizApplySearchService.java` 调用处
- Test: 所有相关测试改参数名

**背景:** P1-5,文档 §2/§4 定义为 `startTime/endTime`。Java 方法参数名不强制校验,但 Mapper XML 的 `#{name}` 需一致,文档可维护性也重要。

- [ ] **Step 1:** 写一个测试断言接口源码含参数名(可通过反射 `-parameters` 编译标志检查)。

```java
@Test
@DisplayName("LoanQueryApi.countCompletedByOrg 参数名必须为 startTime/endTime")
void queryApiParamNames_matchesDoc() throws Exception {
    Method m = LoanQueryApi.class.getMethod("countCompletedByOrg",
        String.class, LocalDateTime.class, LocalDateTime.class);
    assertThat(m.getParameters())
        .extracting(Parameter::getName)
        .containsExactly("orgId", "startTime", "endTime");
}
```

(需要 pom 确认带 `-parameters` 编译选项;若不带,测试降级为"Mapper XML 中无 `#{start}`"断言。)

- [ ] **Step 2-5:** 批量改名 → 测试绿 → commit。

```bash
git commit -m "refactor(bizapp): QueryApi 参数重命名 start/end → startTime/endTime"
```

---

### Task 2.5: `LoanDetailResp` 富化 — custInfo + canOperate

**Files:**
- Modify: `dto/resp/LoanDetailResp.java` — 新增 `CustInfoVO custInfo` + `Boolean canOperate`
- Modify: `service/LoanService.java` — `getById` 改为 `getDetail(id, currentEmpId)` 注入权限信息
- Modify: `controller/LoanController.java` — 调用新签名
- Test: `LoanServiceTest.java` / `LoanControllerTest.java` 覆盖富化字段

**背景:** P1-1,前端详情页需要客户基础信息和按钮可见性。

- [ ] **Step 1: 新增 CustInfoVO 嵌套类 + 写失败测试**

```java
@Data
public static class CustInfoVO {
    private String custId;
    private String custName;
    private String custType;
    private String ownerEmpId;
}
```

```java
@Test
void getById_populatesCustInfoAndCanOperate() throws Exception {
    // mock CustomerQueryApi 返回 cust 详情
    // mock LoanApply: createdBy="emp001"
    // 当 currentEmpId = emp001 → canOperate=true
    // 当 currentEmpId = other → canOperate=false
}
```

- [ ] **Step 2-5:** 
- `LoanService.getDetail(id, empId)` 内部: `LoanApply entity = selectById(id); CustomerDTO cust = customerQueryApi.getCustomer(entity.getCustId()).orElse(null);`
- `canOperate = DRAFT/IN_APPROVAL 状态下 && currentEmpId == createdBy`
- `LoanDetailResp` 组装 custInfo + canOperate
- Note:`processMap` / `approvalLogs` 依赖 workflow-center 的历史查询 API,本任务**不纳入范围**(V1 保留 TODO 注释)
- 测试 + commit

```bash
git commit -m "feat(bizapp): LoanDetailResp 补 custInfo + canOperate"
```

**通过标准:** 详情响应含 `custInfo.custName`、`canOperate` 字段;未包含的 `processMap`/`approvalLogs` 在代码中明确标注 V2。

---

**Phase 2 结束动作:**

```bash
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd push origin claude/suspicious-kirch-8968cd
```

---

# Phase 3 — P2 质量提升(按余量推进)

**目标:** 修补 P2 偏差,使评分达到 ≥ 27/30。达到目标后可提前结束,未完成项标记为技术债。

**Phase 结束动作:** push 后进入最终整体 review。

---

### Task 3.1: BizStateMachine 补 Support `IN_APPROVAL → REJECTED` 转移

**Files:**
- Modify: `service/BizStateMachine.java`
- Test: `BizStateMachineTest.java`

**背景:** P2-4,场景 A 简单流程直接被驳回时无合法转移路径。

- [ ] **Step 1:** 写失败测试 `supportTransition_inApproval_rejected_allowed`
- [ ] **Step 2-5:** 在 `SUPPORT_TRANSITIONS.get("IN_APPROVAL")` 的 Set 里追加 `"REJECTED"` → 测试绿 → commit。

```bash
git commit -m "fix(bizapp): Support 状态机补 IN_APPROVAL→REJECTED"
```

---

### Task 3.2: `sumCreditAmountByEmp` 只统计 COMPLETED 状态

**Files:**
- Modify: `mapper/LoanApplyMapper.xml` — `sumCreditAmountByEmp` 加 `AND status = 'COMPLETED'`
- Test: `LoanApiImplTest.java` 补状态过滤用例(需要 H2 或 Mapper 单元测试)

**背景:** P2-6,绩效统计若包含草稿/审批中,数据失真。

- [ ] **Step 1-5:** TDD 流程修改 SQL + 补测试 → commit。

```bash
git commit -m "fix(bizapp): sumCreditAmountByEmp 只统计 COMPLETED 状态"
```

---

### Task 3.3: 更新 CLAUDE.md + 补 PT_RESOURCE SQL

**Files:**
- Modify: `business-application-center/CLAUDE.md`(更新 REST 端点表、错误码表、测试数统计、DTO 层级描述)
- Create: `docs/superpowers/sql/2026-04-21-bizapp-contract-alignment-pt-resource.sql`(仅对 Phase 1-3 新增 / 修改的端点做 INSERT/UPDATE)

**背景:** 文档和 PT_RESOURCE 注册必须与代码同步。

- [ ] **Step 1-3:**
- 梳理 Phase 1-3 新增 / 修改的端点清单(主要是 @AuditLog 不改变 URL,但应新增 `BIZAPP:LOAN:EXPORT` 等资源若缺失)
- 按 customer-marketing-center 模式(`docs/superpowers/sql/2026-04-14-bizapp-pt-resource.sql` 参考)新建脚本
- CLAUDE.md 的 REST 端点表补 `@AuditLog` 列、DTO 返回类型列
- [ ] **Step 4:** Commit

```bash
git commit -m "docs(bizapp): 更新 CLAUDE.md + PT_RESOURCE 注册新端点"
```

---

### Task 3.4(可选): bootstrap 下新增 business-application-center 集成测试

**Files:**
- Create: `bootstrap/src/test/java/com/bank/branch/platform/it/BusinessApplicationCenterIT.java`

**背景:** 当前 bootstrap 只有 `CustomerMarketingCenterIT`。理想情况下 bizapp 也应该有一个最小闭环 IT:创建 loan 草稿 → 提交 → listener 模拟 workflow 完成事件 → 断言状态迁移到 COMPLETED + 事件发布。

- [ ] **Step 1:** 写 IT 类,参考 `CustomerMarketingCenterIT` 结构
- [ ] **Step 2:** `mvn -f bootstrap/pom.xml test -Dtest=BusinessApplicationCenterIT` 运行
- [ ] **Step 3:** Commit

```bash
git commit -m "test(bootstrap): 新增 BusinessApplicationCenterIT 最小闭环集成测试"
```

**说明:** 本任务可选。若前序 Task 已使评分达标(≥27/30),可将此任务降级为技术债,留给独立 IT 完善 Phase。

---

**Phase 3 结束动作:**

```bash
git -C D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd push origin claude/suspicious-kirch-8968cd
```

---

# 最终整体 Review 与合并

所有 Phase 完成后执行:

- [ ] **Step A: Full build 回归**

```bash
mvn -f D:/Project/oneplate/.claude/worktrees/suspicious-kirch-8968cd/pom.xml clean install
```
Expected: BUILD SUCCESS,business-application-center 测试数 ≥ 140。

- [ ] **Step B: 分派 sonnet 模型的"最终整体 review"子代理**
  - 输入:分析报告 + 本计划 + 所有 commit 列表(`git log --oneline 2f1efd2..HEAD`)
  - 要求:评估评分提升是否达标(≥27/30);检查是否有遗漏的 P0/P1;给出 APPROVED 或 ISSUES FOUND

- [ ] **Step C: 若 review 提出补丁,小步修复后再次 push**

- [ ] **Step D: 合并到 master**

```bash
git -C D:/Project/oneplate fetch origin
git -C D:/Project/oneplate checkout master
git -C D:/Project/oneplate pull origin master
git -C D:/Project/oneplate merge --no-ff claude/suspicious-kirch-8968cd -m "Merge bizapp contract alignment (Phase 1-3)

- P0 7 项全部修复(Entity→DTO / AFTER_COMMIT / REJECTED 分支 / @AuditLog / 枚举 / create 返回 / deleted 字段)
- P1 5 项修复(submit 返回 / batch 上限 / dispatchRemark / 参数重命名 / LoanDetailResp 富化)
- P2 2-4 项修复(状态机 / sum 过滤 / 文档 / [可选]IT)
- 评分从 17/30 提升到 ≥27/30"
git -C D:/Project/oneplate push origin master
```

- [ ] **Step E: 切回原分支避免打扰其他工作**

```bash
git -C D:/Project/oneplate checkout claude/eloquent-mcnulty-f2072a
```

---

# 执行流程总览(subagent-driven-development)

每个 Task 执行模式:

```
implementer (sonnet)
  └─ Red → Green → Refactor → commit
     ↓
 并行双阶段审查(均为 sonnet):
  ├─ spec reviewer: 对照计划文档检查是否完成全部 Step
  └─ code quality reviewer: 检查代码规范、测试质量、DRY/YAGNI
     ↓
 若 APPROVED → 进入下一 Task
 若 ISSUES FOUND → implementer 修订(优先同一子代理会话)
```

**Subagent 调度约束(来自用户偏好 `feedback_subagent_model.md`):**
- 默认 `model: "sonnet"`;仅当 sonnet 明确 BLOCKED 才可申请升级,升级前须与用户确认
- 简单任务(如 Task 2.2 / Task 3.1 这类机械变更)可合并 reviewer,但 implementer 保持独立

**Push 节奏约束(来自用户偏好 `feedback_phase_commit_push.md`):**
- Phase 末立即 push(不 squash commit)
- 全部完成后合并 master 并 push

**TDD 红线:** 所有 Task 的 Step 1 必须是"写失败测试",Step 2 必须是"运行并观察失败",严禁跳过。
