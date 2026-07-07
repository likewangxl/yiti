# P1a 客户营销 3 个真缺失 REST 端点 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans (inline) — 用户已选 inline execution，review 在最后一次性做。
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 03 文档要求但代码缺失的 3 个 REST 端点（`POST /api/claims/{id}/re-touch`、`GET /api/admin/touch-tasks/summary`、`GET /api/leads/import/batches/{batchId}`），并落地 A1 强依赖的 2 条新错误码（`CUST-40908/40909`）。

**Architecture:** Controller 包装现有/新增 Service；A1 新增 `ClaimService.reTouch` 业务方法和 2 个 `CustomerErrorCode` 枚举；A2/A3 仅 Controller。严格 TDD：先 service 单元测试（仅 A1）→ Controller 集成测试（3 端点）→ 实现 → 文档同步 → subagent 一次 review spec+code。

**Tech Stack:** Spring Boot 3.2.3 + MyBatis 3.0.3 + JUnit 5 + Mockito + MockMvc + H2（测试）+ MySQL（生产）

**关联 spec:** [`docs/superpowers/specs/2026-04-28-customer-p1a-3-rest-endpoints-design.md`](../specs/2026-04-28-customer-p1a-3-rest-endpoints-design.md)

---

## Task 0: 实现阶段侦察修订（Task 1 输出，2026-04-28）

> Task 1 完成后回填本节。Task 2~10 执行时按本表调整 — spec 设计意图不变，只把代码现状反映到 plan。

| 原 spec/plan 假设 | 真值 | 影响 task |
|---|---|---|
| `ActionType.READ/UPDATE` | **`BizAction.READ / UPDATE / IMPORT / EXPORT / LIST / CREATE / DELETE`**（注解类 `BizAuth(bizType=, action=)`） | 6/7/8 |
| `ApiResult.ok(...)` | **`ResponseWrapper<T>` + `ResponseWrapper.success(...)`** | 6/7/8 |
| `OrgTouchSummaryDTO` | **`TouchTaskSummaryDTO`** | 7 |
| `getOrgTouchSummary(orgCode)` | **`getOrgTouchSummary(orgCode, startDate, endDate)`** — endpoint 也加两个可选 startDate/endDate 参数 | 7 |
| `LeadImportBatch.batchStatus / totalCount / failCount + successCount` | **`status` / `totalRowCount` / `errorRowCount`，无 `successCount`**；多 `errorSummary` / `processInstanceId` / `updatedTime` / `updatedBy` / `fileMd5` / `batchNo` | 4 |
| `TouchTaskService.createFromClaim(claim, type, reason, planTime)` | **真签名 `(custId, orgId, assigneeEmpId)`，写死 type=FIRST_TOUCH + plan 7d**；本批扩展为新重载 `createFromClaim(custId, orgId, assigneeEmpId, TouchTaskType, String reason, String planFinishTime)`，旧 3 参版本保留向后兼容 | 5 |
| `TouchTaskMapper.existsByCustIdAndStatusIn / selectLatestByCustId` | **不存在**；本批新增 2 个 mapper 方法 + xml 片段 | 5 |
| `ClaimService` 已注入 `TouchTaskMapper / TouchTaskService / CurrentUserApi` | **改为注入 `TouchTaskMapper / TouchTaskService` 仅 2 项**；不注入 CurrentUserApi（仿 cancelClaim 模式由 Controller 传入 operatorEmpId/operatorOrgCode）；reTouch 签名改为 `reTouch(claimId, req, operatorEmpId, operatorOrgCode)` | 5/6 |
| 用 `existsByCustIdAndStatusIn / selectLatestByCustId` | **复用现有 `TouchTaskMapper.selectActiveByCust(custId)` 和 `selectByCustOrderByCreatedDesc(custId)`** — 不新增 mapper 方法 | 5 |
| `TouchTaskService.createFromClaim` 重载 | **改为 `TouchTaskService.createFollowUpTask(custId, orgId, assigneeEmpId, reason, planFinishTime)` 新独立方法**（不动 createFromClaim）；reason/planFinishTime 仅记录日志，table 无对应字段 | 5 |
| CUST-40909 RE_TOUCH_LAST_NOT_FINISHED 在 reTouch 中触发 | **简化：reTouch 只用 CUST-40908**（selectActiveByCust 已覆盖所有"最近未完成"场景，40909 与 40908 数据来源相同）；CUST-40909 枚举保留为占位防未来撞码 | 5（4 测试而非 5） |
| `ClaimStatus` 真值 | **`CLAIMED / CANCELLED`**（与 ClaimService.claim 现有 line 77 用法一致；模块 CLAUDE.md 写 `ACTIVE/CANCELLED` 是错的） | 5 |

---

## Task 1: 实现前置代码侦察（消化 spec §6 风险）

**Files:** read-only

- [ ] **Step 1: 并行 grep 4 个不确定项**

```bash
# 1) TouchTaskService.createFromClaim 签名
grep -n "createFromClaim\|public.*createFromClaim" customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/TouchTaskService.java

# 2) TouchTaskQueryApi.getOrgTouchSummary 签名 + 返回 DTO
grep -n "getOrgTouchSummary\|OrgTouchSummary" customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/TouchTaskQueryApi.java customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/

# 3) LeadImportBatch entity 字段
grep -n "private " customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/LeadImportBatch.java

# 4) ActionType 枚举值
grep -rn "enum ActionType" common/ auth-permission-center/ 2>/dev/null
```

- [ ] **Step 2: 记录侦察结果到 spec §6 表格右列；如发现签名/字段与 spec 不符，**修 spec 不修代码**（spec 是真值的镜像）。如果 `createFromClaim` 不接受 `reason` 参数，扩展该方法签名属于 Task 5 的范围**

---

## Task 2: 补 2 条 409 错误码枚举

**Files:**
- Modify: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/CustomerErrorCode.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/enums/CustomerErrorCodeTest.java`（如不存在则创建）

- [ ] **Step 1: 写失败的枚举存在性测试**

`CustomerErrorCodeTest.java`:
```java
@Test
void retouchHasRunningCodeIs40908() {
    assertEquals("CUST-40908", CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode());
    assertTrue(CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getMessage().contains("已有进行中"));
}

@Test
void retouchLastNotFinishedCodeIs40909() {
    assertEquals("CUST-40909", CustomerErrorCode.RE_TOUCH_LAST_NOT_FINISHED.getCode());
    assertTrue(CustomerErrorCode.RE_TOUCH_LAST_NOT_FINISHED.getMessage().contains("未完成"));
}
```

- [ ] **Step 2: 跑测试，确认 RED（编译错，符号未定义）**

```bash
MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED" mvn test -pl customer-marketing-center -Dtest=CustomerErrorCodeTest -q
```
预期：编译失败 — `RE_TOUCH_HAS_RUNNING` 未定义。

- [ ] **Step 3: 在 CustomerErrorCode.java 追加枚举（紧接 CUSTOMER_HAS_RUNNING_PROCESS 后）**

```java
RE_TOUCH_HAS_RUNNING("CUST-40908", "触达任务已有进行中，不可重新发起"),
RE_TOUCH_LAST_NOT_FINISHED("CUST-40909", "最近触达任务未完成，不可重新发起"),
```

- [ ] **Step 4: 跑测试，确认 GREEN**

预期：2 测试通过。

- [ ] **Step 5: 不单独 commit（与后续 ClaimService 实现一起 commit，避免历史碎片）**

---

## Task 3: 创建 ReTouchReqDTO

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/dto/req/ReTouchReqDTO.java`

- [ ] **Step 1: 直接写（DTO 无逻辑无需 TDD）**

```java
package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 重新发起触达请求 DTO */
@Data
public class ReTouchReqDTO {
    @NotBlank(message = "重新触达原因不能为空")
    @Size(min = 10, max = 500, message = "原因长度须在 10~500 字符")
    private String reason;

    /** 计划完成时间（可选，缺省按默认 SLA 计算） */
    private String planFinishTime;
}
```

- [ ] **Step 2: 编译验证**

```bash
MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED" mvn test-compile -pl customer-marketing-center -q
```

---

## Task 4: 创建 LeadImportBatchDetailRespDTO

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/dto/resp/LeadImportBatchDetailRespDTO.java`

- [ ] **Step 1: 按 Task 1 侦察到的 LeadImportBatch entity 字段写 RespDTO（仅暴露下表字段，机敏字段不出对外）**

```java
package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;
import java.time.LocalDateTime;

/** 导入批次详情响应 DTO */
@Data
public class LeadImportBatchDetailRespDTO {
    private String id;
    private String sourceFileName;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String batchStatus;
    private String errorFileObjectId;
    private String businessKey;
    private LocalDateTime createdTime;
    private String createdBy;
    private String ownerOrgId;
}
```

- [ ] **Step 2: 编译验证（同 Task 3 Step 2）**

---

## Task 5: 实现 ClaimService.reTouch（核心业务，严格 TDD）

**Files:**
- Modify: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/ClaimService.java`
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/service/ClaimServiceReTouchTest.java`

- [ ] **Step 1: 写 5 个失败的 service 单元测试**

```java
@ExtendWith(MockitoExtension.class)
class ClaimServiceReTouchTest {
    @Mock CustClaimMapper claimMapper;
    @Mock TouchTaskMapper touchTaskMapper;
    @Mock TouchTaskService touchTaskService;
    @Mock CurrentUserApi currentUserApi;
    @InjectMocks ClaimService claimService;

    @Test
    void reTouch_success_createsFollowUpTask() {
        // 准备：claim ACTIVE / 当前用户机构匹配 / 无 PENDING/IN_PROGRESS 任务 / 最近任务 SUCCESS
        // 断言：调用 touchTaskService.createFromClaim(claim, FOLLOW_UP, reason, planTime) 一次
        // 断言：返回 TouchTaskRespDTO 非空，taskId 来自 mock 返回
    }

    @Test
    void reTouch_claimNotFound_throwsCust40404() {
        when(claimMapper.selectById("c1")).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
            () -> claimService.reTouch("c1", buildReq("原因充足十字以上")));
        assertEquals("CUST-40404", ex.getCode());
    }

    @Test
    void reTouch_otherOrgClaim_throwsCust40305() { /* ... */ }

    @Test
    void reTouch_runningTouchTask_throwsCust40908() { /* ... */ }

    @Test
    void reTouch_lastNotFinished_throwsCust40909() { /* ... */ }
}
```

- [ ] **Step 2: 跑 RED 验证**

```bash
MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED" mvn test -pl customer-marketing-center -Dtest=ClaimServiceReTouchTest -q
```
预期：5 测试 FAIL（reTouch 方法不存在）

- [ ] **Step 3: 在 ClaimService.java 实现 reTouch 方法**

```java
@Transactional(rollbackFor = Exception.class)
public TouchTaskRespDTO reTouch(String claimId, ReTouchReqDTO req) {
    CustClaim claim = claimMapper.selectById(claimId);
    if (claim == null) {
        throw new BizException(CustomerErrorCode.CLAIM_NOT_FOUND);
    }
    String currentOrg = currentUserApi.getCurrentOrgCode();
    if (!Objects.equals(claim.getOrgId(), currentOrg)) {
        throw new BizException(CustomerErrorCode.CLAIM_ORG_FORBIDDEN);  // CUST-40305
    }
    // 校验当前无在途任务（按 Task 1 侦察决定 mapper 方法名）
    boolean hasRunning = touchTaskMapper.existsByCustIdAndStatusIn(
        claim.getCustId(), List.of("PENDING", "IN_PROGRESS"));
    if (hasRunning) {
        throw new BizException(CustomerErrorCode.RE_TOUCH_HAS_RUNNING);
    }
    // 校验最近任务已完结
    TouchTask latest = touchTaskMapper.selectLatestByCustId(claim.getCustId());
    if (latest != null && !List.of("SUCCESS", "CANCELLED").contains(latest.getTaskStatus())) {
        throw new BizException(CustomerErrorCode.RE_TOUCH_LAST_NOT_FINISHED);
    }
    return touchTaskService.createFromClaim(claim, TouchTaskType.FOLLOW_UP,
                                             req.getReason(), req.getPlanFinishTime());
}
```

> 注：`existsByCustIdAndStatusIn` 和 `selectLatestByCustId` 等 mapper 方法名按 Task 1 侦察确定；缺则在本 Task 加 mapper 方法 + xml。`createFromClaim` 签名按 Task 1 确定，不匹配时本 Task 扩展该方法签名（保持向后兼容）。

- [ ] **Step 4: 跑 GREEN 验证**

预期：5 测试 PASS。

- [ ] **Step 5: 重构（必要时）— 保持方法 < 30 行；提取私有方法 `assertNoRunningTouchTask` / `assertLastFinished`**

- [ ] **Step 6: 跑全模块 surefire 确认无回归**

```bash
MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED" mvn test -pl customer-marketing-center -q 2>&1 | tail -10
```
预期：原 321 + 新 7 = 328 测试全绿（5 reTouch + 2 ErrorCode）

---

## Task 6: A1 Controller — POST /api/claims/{claimId}/re-touch

**Files:**
- Modify: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/ClaimController.java`
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/controller/ClaimControllerReTouchTest.java`

- [ ] **Step 1: 写 4 个失败的 Controller 集成测试**

```java
class ClaimControllerReTouchTest extends AbstractControllerIntegrationTest {

    @Test @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void reTouch_success_returns200() throws Exception {
        // 数据准备：H2 插入 claim ACTIVE + 历史 SUCCESS 任务
        mockMvc.perform(post("/api/claims/{id}/re-touch", "claim-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"客户提出新需求需重新触达"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.taskId").exists());
    }

    @Test @WithMockEmpContext void reTouch_claimNotFound_returns404OrBizCode() { /* CUST-40404 */ }
    @Test @WithMockEmpContext void reTouch_otherOrg_returnsCust40305() { /* */ }
    @Test @WithMockEmpContext void reTouch_runningTask_returnsCust40908() { /* */ }
}
```

- [ ] **Step 2: 跑 RED — 4 测试失败（端点不存在 → 404）**

- [ ] **Step 3: 在 ClaimController.java 加 endpoint**

```java
@PostMapping("/{claimId}/re-touch")
@BizAuth(bizType = BizType.CLAIM, action = ActionType.UPDATE)
public ApiResult<TouchTaskRespDTO> reTouch(
        @PathVariable String claimId,
        @RequestBody @Valid ReTouchReqDTO req) {
    return ApiResult.ok(claimService.reTouch(claimId, req));
}
```

- [ ] **Step 4: 跑 GREEN — 4 测试通过**

- [ ] **Step 5: 模块全测试无回归**（同 Task 5 Step 6）

---

## Task 7: A2 Controller — GET /api/admin/touch-tasks/summary

**Files:**
- Modify: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/admin/AdminTouchTaskController.java`
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/controller/admin/AdminTouchTaskSummaryTest.java`

- [ ] **Step 1: 写 3 个失败的集成测试**

```java
@Test @WithMockEmpContext(roles = "R_ADMIN")
void summary_validOrg_returns200WithCounts() throws Exception {
    mockMvc.perform(get("/api/admin/touch-tasks/summary")
            .param("orgCode", "ORG_SZ_001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pendingCount").exists());
}

@Test void summary_missingOrgCode_returns400() throws Exception {
    mockMvc.perform(get("/api/admin/touch-tasks/summary"))
        .andExpect(status().isBadRequest());
}

@Test @WithMockEmpContext(roles = "R_RM")  // 普通客户经理
void summary_nonAdmin_returns403() throws Exception {
    mockMvc.perform(get("/api/admin/touch-tasks/summary").param("orgCode","ORG_SZ_001"))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: 跑 RED**

- [ ] **Step 3: 加 endpoint**

```java
@GetMapping("/summary")
@BizAuth(bizType = BizType.TOUCH_TASK, action = ActionType.READ)
public ApiResult<OrgTouchSummaryDTO> summary(@RequestParam String orgCode) {
    return ApiResult.ok(touchTaskQueryApi.getOrgTouchSummary(orgCode));
}
```

> 返回 DTO 类名按 Task 1 侦察确定。

- [ ] **Step 4: 跑 GREEN + 全测试**

---

## Task 8: A3 Controller — GET /api/leads/import/batches/{batchId}

**Files:**
- Modify: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/LeadImportController.java`
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/controller/LeadImportBatchDetailTest.java`

- [ ] **Step 1: 写 3 个失败的集成测试**

```java
@Test @WithMockEmpContext void getBatch_exists_returns200() throws Exception {
    mockMvc.perform(get("/api/leads/import/batches/{id}", "batch-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("batch-001"));
}

@Test @WithMockEmpContext void getBatch_notExists_returnsCust40406() throws Exception { /* */ }

@Test @WithMockEmpContext void getBatch_otherOrg_dataScopeBlocks() throws Exception { /* */ }
```

- [ ] **Step 2: 跑 RED**

- [ ] **Step 3: 加 endpoint + entity → DTO 转换**

```java
@GetMapping("/import/batches/{batchId}")
@BizAuth(bizType = BizType.LEAD, action = ActionType.READ)
public ApiResult<LeadImportBatchDetailRespDTO> getBatch(@PathVariable String batchId) {
    LeadImportBatch entity = leadImportService.getBatchById(batchId);
    if (entity == null) {
        throw new BizException(CustomerErrorCode.BATCH_NOT_FOUND);
    }
    return ApiResult.ok(toDetailRespDTO(entity));
}

private LeadImportBatchDetailRespDTO toDetailRespDTO(LeadImportBatch e) {
    LeadImportBatchDetailRespDTO d = new LeadImportBatchDetailRespDTO();
    BeanUtils.copyProperties(e, d);
    return d;
}
```

- [ ] **Step 4: 跑 GREEN + 全测试**

---

## Task 9: PT_RESOURCE 注册 SQL

**Files:**
- Create: `docs/superpowers/sql/2026-04-28-customer-p1a-rest-pt-resource.sql`

- [ ] **Step 1: 按现有 PT_RESOURCE 列结构写 3 条 INSERT**

参考现有 `docs/superpowers/sql/2026-04-21-customer-contract-alignment-pt-resource.sql` 的列序与值。RESOURCE_ID 严格 ≤ 20 字符。

- [ ] **Step 2: 不在测试 H2 里跑（H2 测试不依赖 PT_RESOURCE 真实数据，由 mock 上下文提供）；本文件只是给 DBA 上线时执行**

---

## Task 10: 文档同步

**Files:**
- Modify: `customer-marketing-center/CLAUDE.md`（REST 端点表 +3 行）
- Modify: `docs/modules/customer-marketing-center/02-后端架构.md` §3 包结构 dto/req+resp 段
- Modify: `docs/modules/customer-marketing-center/03-接口设计与报文.md`（删 §F.3 / §H.1 / §C.4 中"未实现"标注，如有）
- Modify: `docs/superpowers/sessions/2026-04-28-customer-marketing-center-doc-code-deviation.md` §10.4 + §10.8 P1 业务确认行 — 标记 ✓ 已实现

- [ ] **Step 1: CLAUDE.md REST 端点表加 3 行（ClaimController 段、AdminTouchTaskController 段、LeadImportController 段各 +1 行）**

- [ ] **Step 2: 02 §3 dto/req 段加 ReTouchReqDTO；dto/resp 段加 LeadImportBatchDetailRespDTO**

- [ ] **Step 3: 03 文档检查 — 找 "未实现/计划/TODO" 类标注并清除**

- [ ] **Step 4: 偏离度底稿 §10.4 B 类「改代码」3 条加 ✓ 已完成（commit hash 占位）；§10.8 P1 行加 [done] 标记**

---

## Task 11: 全量验收 + subagent review

- [ ] **Step 1: 全模块测试**

```bash
MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED" mvn test -pl customer-marketing-center -q 2>&1 | tail -15
```
预期：全绿，新增测试 ~17 个（5 service + 4×3 controller = 17）。

- [ ] **Step 2: grep 验证无残留 placeholder / TODO**

```bash
grep -rn "TODO\|XXX\|FIXME" customer-marketing-center/src/main/java/com/bank/branch/platform/customer/{controller,service,dto}/ 2>/dev/null | grep -v "已知技术债\|@author\|License"
```

- [ ] **Step 3: 派 子 agent 一次性 review spec + code（按用户指令）**

```
Agent({
  description: "P1a review — spec + code",
  subagent_type: "内部工具:code-reviewer",
  model=高配,
  prompt: "对 P1a 批次（commit ded0cef..HEAD）做严格的 spec + code review。
    Spec: docs/superpowers/specs/2026-04-28-customer-p1a-3-rest-endpoints-design.md
    Plan: docs/superpowers/plans/2026-04-28-customer-p1a-3-rest-endpoints.md
    本批新增/修改文件清单见 git diff。
    评估：correctness / maintainability / edge cases / regressions / test adequacy。
    输出 severity-rated 反馈（CRITICAL / MAJOR / MINOR），每条含文件:行号 + 问题 + 建议修复。
    重点关注：(1) reTouch 并发条件检查是否够；(2) ReTouchReqDTO 校验是否充分；
    (3) Controller 层是否补 @Valid；(4) PT_RESOURCE SQL 列与历史脚本一致；(5) 是否引入 N+1 查询。
    严格、不留情面、≤500 字。"
})
```

- [ ] **Step 4: 应用 review 反馈（如有 CRITICAL / MAJOR），重跑测试**

- [ ] **Step 5: 单 commit 提交全部代码 + 文档（spec 已在前一 commit 180d44b）**

```bash
git add customer-marketing-center/ docs/modules/customer-marketing-center/02-后端架构.md docs/modules/customer-marketing-center/03-接口设计与报文.md docs/superpowers/sql/2026-04-28-customer-p1a-rest-pt-resource.sql docs/superpowers/sessions/2026-04-28-customer-marketing-center-doc-code-deviation.md docs/superpowers/plans/2026-04-28-customer-p1a-3-rest-endpoints.md
git commit -m "feat(customer-v1): P1a — 3 REST 端点（re-touch / admin summary / batch detail）+ CUST-40908/40909 + reviewed"
git pull --rebase origin master
git push origin master
```

---

## Self-Review (writing-plans 内置)

✓ **Spec coverage**: spec §1~§5 每条都对应 Task 2~10 之一  
✓ **Placeholder scan**: 无 TBD / TODO / "implement later"；侦察类风险已识别为 Task 1 显式步骤  
✓ **Type consistency**: `reTouch(String, ReTouchReqDTO)` / `createFromClaim(claim, type, reason, planTime)` / `OrgTouchSummaryDTO` 等签名跨任务一致  
✓ **Scope check**: 11 任务、~17 测试、~600 行代码 — 单 plan 合理范围  
✓ **TDD 严格**: Task 2/5/6/7/8 全部红→绿→重构闭环

---

## 执行交接

按用户指令选 **Inline Execution**（subagent-driven 在每 task 派 agent 与"最后一次性 review"冲突）。
执行入口：本计划就地按 Task 1→11 顺序跑，过程不再额外 invoke 其它技能；最后一步 Task 11 派 子 agent 做合并 review。
