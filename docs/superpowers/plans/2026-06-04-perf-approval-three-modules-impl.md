# 手机端业绩调整审批拆分三模块 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把手机端"业绩调整"单一合并列表拆成"我的申请 / 待审批 / 已审批"三个独立模块，每模块只操作与自己相关的数据；新增页自动回显客户原分配关系。

**Architecture:** 后端在 perf 加"按申请人查询"接口 + "按待办/已办分别查询"重载；网关加 `PERF_MY_LIST` / `PERF_LIST(queryStatus)` / `PERF_ORIG_ALLOC` 三条分发；前端 `index.vue` 拆三块导航，三个独立列表页（删除 `list.vue`），新增页接入原分配预填。

**Tech Stack:** Spring Boot 3.2.3 / MyBatis / JUnit5 + Mockito + AssertJ（后端）；Vue 2.6 + Vant 2（前端）。

**关键约束（来自 CLAUDE.md 红线）：**
- 严格 TDD（红-绿-重构），每步独立 commit；后端编译用 JDK 17（`JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64`，避免 JDK 26 + lombok 假失败）。
- 跨模块只走 `*Api`；中文注释 + UTF-8。
- 本仓 `front/xazc_transfer_front` 非 git、`yiti` 若非 git 仓库则跳过所有 `git commit` 步骤（改为人工存档）。前端无测试运行器，列表页/新增页为"写 + 人工验证"，不强行套红-绿。

---

## 文件结构总览

**后端 perf（`performance-engine-center`）**
- 改 `api/PerfApprovalQueryApi.java`：加 2 个方法签名。
- 改 `facade/PerfApprovalQueryFacade.java`：加 2 个方法实现 + 注入 `PerfAllocAdjustApplyMapper`。
- 改 `facade/PerfApprovalQueryFacadeTest`（如有）/ 新增单测覆盖。

**后端网关（`soap-gateway-center`）**
- 改 `controller/dto/CallPuRequest.java`：`Parm` 加 `queryStatus` 字段。
- 新增 `controller/dto/OrigAllocData.java` + `OrigAllocItem`。
- 改 `service/CallPuDispatchService.java`：注入 `AllocApi`，加 `handleMyList` / `handleOrigAlloc`、`handlePerfList` 读 `queryStatus`、`toApprStatus` 加 `WITHDRAWN→"3"`。
- 改 `service/CallPuDispatchServiceTest.java`：加 `@Mock AllocApi` + 新用例。

**前端（`front/xazc_transfer_front`）**
- 新增 `performanceAdjustment/callpu.js`：报文封装工具（相对路径引入，**不走 `@CF`**）。
- 改 `views/index.vue`：删"业绩调整"，加三块导航。
- 新增 `performanceAdjustment/performanceMyList.vue` / `performancePending.vue` / `performanceApproved.vue`。
- 删 `performanceAdjustment/list.vue`。
- 改 `performanceAdjustment/applyAdd.vue`：原分配自动预填。
- 改 `performanceAdjustment/applyInfo.vue`：`listType==='approved'` 只读兜底。

---

# Phase 1 — perf 后端查询接口

> 工作目录：`/home/djdev/lijh/yiti`。每个 `mvn` 命令前置 `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64`。
> 跨模块改动后若上游 jar 陈旧：先 `JAVA_HOME=... mvn clean install -DskipTests -pl performance-engine-center`。

## Task 1.1: perf 新增"我的申请"查询 `listMyAllocAdjustApplications`

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/PerfApprovalQueryApi.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/PerfApprovalQueryFacade.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/PerfApprovalQueryFacadeMyApplyTest.java`（新建）

- [ ] **Step 1: 写失败测试**

新建 `PerfApprovalQueryFacadeMyApplyTest.java`：

```java
package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfApprovalQueryFacadeMyApplyTest {

    @Mock private AllocAdjustTodoService allocAdjustTodoService;
    @Mock private AllocAdjustDoneService allocAdjustDoneService;
    @Mock private AddressBookApi addressBookApi;
    @Mock private CustomerQueryApi customerQueryApi;
    @Mock private PerfAllocAdjustApplyMapper allocAdjustApplyMapper;

    @InjectMocks private PerfApprovalQueryFacade facade;

    @Test
    void listMyApplications_filtersByCreatedBy_andMapsSnapshotFields() {
        PerfAllocAdjustApply e = new PerfAllocAdjustApply();
        e.setId("PA_900");
        e.setApplyNo("AA20260604001");
        e.setCustId("C001");
        e.setCustName("某某客户");        // 实体自带客户名快照，无需再 RPC
        e.setCreatedBy("U001");
        e.setStatus("WITHDRAWN");
        e.setCreatedTime(LocalDateTime.of(2026, 6, 4, 10, 0, 0));

        when(allocAdjustApplyMapper.selectByConditions(
                eq(null), eq(null), eq(null), eq(null), eq("U001"), anyInt(), anyInt()))
                .thenReturn(List.of(e));
        when(allocAdjustApplyMapper.countByConditions(
                eq(null), eq(null), eq(null), eq(null), eq("U001")))
                .thenReturn(1L);
        when(addressBookApi.getEmployee("U001"))
                .thenReturn(Optional.of(EmployeeDTO.builder().empName("张三").build()));

        PageResult<AllocAdjustApprovalItemDTO> page =
                facade.listMyAllocAdjustApplications("U001", 1, 15);

        assertThat(page.getTotal()).isEqualTo(1L);
        AllocAdjustApprovalItemDTO item = page.getRecords().get(0);
        assertThat(item.getPerfAdjustNo()).isEqualTo("PA_900");
        assertThat(item.getCustName()).isEqualTo("某某客户");
        assertThat(item.getApplyFullname()).isEqualTo("张三");
        assertThat(item.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(item.getCategory()).isEqualTo("MINE");
    }
}
```

> 注：`EmployeeDTO.builder().empName(...)` 若 `EmployeeDTO` 无 builder，改用 setter 构造（执行 Step 2 报错即知；按实际 DTO 调整构造方式，字段语义为 empName=姓名）。

- [ ] **Step 2: 运行测试，确认失败**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl performance-engine-center \
  test -Dtest=PerfApprovalQueryFacadeMyApplyTest
```
Expected: 编译失败 / `listMyAllocAdjustApplications` 方法不存在。

- [ ] **Step 3: 加接口方法签名**

在 `PerfApprovalQueryApi.java` 接口内追加：

```java
    /**
     * 查询某员工"作为申请人"提交的「分配关系调整」申请列表（全状态，含 WITHDRAWN）。
     *
     * <p>区别于 {@link #listAllocAdjustApprovals}（审批人视角）：本方法按 {@code createdBy = empId}
     * 过滤，供手机端"我的申请"模块使用。</p>
     *
     * @param empId    申请人 USER_ID（网关已把报文工号转为 USER_ID）
     * @param pageNo   页码（从 1 开始，&lt;1 归一为 1）
     * @param pageSize 每页大小（&lt;1 归一为默认值）
     * @return 按申请时间倒序的分页结果
     */
    PageResult<AllocAdjustApprovalItemDTO> listMyAllocAdjustApplications(String empId, int pageNo, int pageSize);
```

- [ ] **Step 4: 加 facade 实现**

在 `PerfApprovalQueryFacade.java`：注入 mapper 字段（加到现有 `private final` 字段区）：

```java
    private final com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper allocAdjustApplyMapper;
```

> 用全限定名避免改 import 区导致的 diff 噪声；也可在文件顶部加 `import`。`@RequiredArgsConstructor` 会把它纳入构造器。

新增方法实现：

```java
    @Override
    public PageResult<AllocAdjustApprovalItemDTO> listMyAllocAdjustApplications(
            String empId, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        log.info("[PerfApprovalQueryFacade.listMyAllocAdjustApplications] empId={}, pageNo={}, pageSize={}",
                empId, safePageNo, safePageSize);

        long total = allocAdjustApplyMapper.countByConditions(null, null, null, null, empId);
        int offset = (safePageNo - 1) * safePageSize;
        if (offset >= total) {
            return PageResult.of(safePageNo, safePageSize, total, java.util.Collections.emptyList());
        }
        // createdBy 过滤；其余条件全 null；按申请人视角全状态返回（mapper 内 ORDER BY created_time DESC）
        List<PerfAllocAdjustApply> rows = allocAdjustApplyMapper.selectByConditions(
                null, null, null, null, empId, offset, safePageSize);

        Map<String, String> empNameCache = new HashMap<>();
        List<AllocAdjustApprovalItemDTO> records = new ArrayList<>(rows.size());
        for (PerfAllocAdjustApply e : rows) {
            records.add(AllocAdjustApprovalItemDTO.builder()
                    .perfAdjustNo(e.getId())
                    .applyNo(e.getApplyNo())
                    .custId(e.getCustId())
                    .custName(e.getCustName())                 // 实体自带客户名快照
                    .createdBy(e.getCreatedBy())
                    .applyFullname(resolveEmpName(e.getCreatedBy(), empNameCache))
                    .applyTime(e.getCreatedTime())
                    .status(e.getStatus())
                    .category("MINE")
                    .build());
        }
        return PageResult.of(safePageNo, safePageSize, total, records);
    }
```

> `PerfAllocAdjustApply`、`List`、`Map`、`HashMap`、`ArrayList` 需 import（facade 已 import 多数；`PerfAllocAdjustApply` 用全限定名或加 import）。`resolveEmpName` 是 facade 现有私有方法，直接复用。

确认 `selectByConditions` 的 mapper XML 已 `ORDER BY created_time DESC`；若未排序，在 XML 末尾补 `ORDER BY created_time DESC, id DESC`（文件 `performance-engine-center/src/main/resources/mapper/PerfAllocAdjustApplyMapper.xml`）。

- [ ] **Step 5: 运行测试，确认通过**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl performance-engine-center \
  test -Dtest=PerfApprovalQueryFacadeMyApplyTest
```
Expected: PASS（1 test）。

- [ ] **Step 6: Commit**

```bash
cd /home/djdev/lijh/yiti && git add -A && git commit -m "feat(perf): 新增按申请人查询分配调整申请 listMyAllocAdjustApplications

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
（非 git 仓库则跳过本步。）

---

## Task 1.2: perf 审批列表加"按待办/已办状态"过滤重载

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/PerfApprovalQueryApi.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/PerfApprovalQueryFacade.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/PerfApprovalQueryFacadeStatusTest.java`（新建）

设计：`PENDING` 只查待办（`listMyTodosByEmp`），`DONE` 只查已办（`listMyDones`），`null` 维持现有合并逻辑（向后兼容）。

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import com.bank.branch.platform.portal.api.AddressBookApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfApprovalQueryFacadeStatusTest {

    @Mock private AllocAdjustTodoService allocAdjustTodoService;
    @Mock private AllocAdjustDoneService allocAdjustDoneService;
    @Mock private AddressBookApi addressBookApi;
    @Mock private CustomerQueryApi customerQueryApi;
    @Mock private PerfAllocAdjustApplyMapper allocAdjustApplyMapper;

    @InjectMocks private PerfApprovalQueryFacade facade;

    private static AdjustTodoRespDTO todo(String id, String status) {
        AdjustTodoRespDTO d = new AdjustTodoRespDTO();
        d.setId(id);
        d.setStatus(status);
        d.setCreatedTime(LocalDateTime.of(2026, 6, 4, 9, 0, 0));
        return d;
    }

    @Test
    void pending_queriesOnlyTodo_notDone() {
        when(allocAdjustTodoService.listMyTodosByEmp(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 1L, List.of(todo("T1", "IN_APPROVAL"))));

        PageResult<AllocAdjustApprovalItemDTO> page =
                facade.listAllocAdjustApprovals("U001", "PENDING", 1, 100);

        assertThat(page.getRecords()).extracting(AllocAdjustApprovalItemDTO::getPerfAdjustNo).containsExactly("T1");
        verify(allocAdjustDoneService, never()).listMyDones(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void done_queriesOnlyDone_notTodo() {
        when(allocAdjustDoneService.listMyDones(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 1L, List.of(todo("D1", "APPROVED"))));

        PageResult<AllocAdjustApprovalItemDTO> page =
                facade.listAllocAdjustApprovals("U001", "DONE", 1, 100);

        assertThat(page.getRecords()).extracting(AllocAdjustApprovalItemDTO::getPerfAdjustNo).containsExactly("D1");
        verify(allocAdjustTodoService, never()).listMyTodosByEmp(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }
}
```

> 若 `AdjustTodoRespDTO` 无无参构造/ setter，按其实际形态构造（执行 Step 2 即暴露）。

- [ ] **Step 2: 运行，确认失败**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl performance-engine-center \
  test -Dtest=PerfApprovalQueryFacadeStatusTest
```
Expected: 编译失败（4 参重载不存在）。

- [ ] **Step 3: 加接口重载**

`PerfApprovalQueryApi.java` 追加：

```java
    /**
     * 按状态域查询审批列表：
     * {@code statusFilter="PENDING"} 仅待办、{@code "DONE"} 仅已办、{@code null} 合并（同 3 参版）。
     */
    PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(
            String empId, String statusFilter, int pageNo, int pageSize);
```

- [ ] **Step 4: facade 实现 + 旧方法委托**

`PerfApprovalQueryFacade.java`：把现有 `listAllocAdjustApprovals(empId,pageNo,pageSize)` 的方法体抽到新 4 参方法，旧 3 参方法委托：

```java
    @Override
    public PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(String empId, int pageNo, int pageSize) {
        return listAllocAdjustApprovals(empId, null, pageNo, pageSize);
    }

    @Override
    public PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(
            String empId, String statusFilter, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        log.info("[PerfApprovalQueryFacade.listAllocAdjustApprovals] empId={}, statusFilter={}, pageNo={}, pageSize={}",
                empId, statusFilter, safePageNo, safePageSize);

        boolean wantTodo = statusFilter == null || "PENDING".equals(statusFilter);
        boolean wantDone = statusFilter == null || "DONE".equals(statusFilter);

        PageResult<AdjustTodoRespDTO> todoPage = wantTodo
                ? allocAdjustTodoService.listMyTodosByEmp(empId, null, null, null, null, null, 1, FETCH_CAP)
                : PageResult.of(1, FETCH_CAP, 0L, Collections.emptyList());
        PageResult<AdjustTodoRespDTO> donePage = wantDone
                ? allocAdjustDoneService.listMyDones(empId, null, null, null, null, null, 1, FETCH_CAP)
                : PageResult.of(1, FETCH_CAP, 0L, Collections.emptyList());

        if (todoPage.getTotal() > FETCH_CAP || donePage.getTotal() > FETCH_CAP) {
            log.warn("[PerfApprovalQueryFacade] empId={} 审批记录超过拉取上限 {}，合并列表可能被截断"
                            + "（todoTotal={}, doneTotal={}）",
                    empId, FETCH_CAP, todoPage.getTotal(), donePage.getTotal());
        }

        Map<String, String> empNameCache = new HashMap<>();
        Map<String, String> custNameCache = new HashMap<>();
        Map<String, AllocAdjustApprovalItemDTO> byId = new LinkedHashMap<>();
        for (AdjustTodoRespDTO t : todoPage.getRecords()) {
            byId.put(t.getId(), toItem(t, "TODO", empNameCache, custNameCache));
        }
        for (AdjustTodoRespDTO d : donePage.getRecords()) {
            byId.putIfAbsent(d.getId(), toItem(d, "DONE", empNameCache, custNameCache));
        }

        List<AllocAdjustApprovalItemDTO> merged = new ArrayList<>(byId.values());
        merged.sort(Comparator.comparing(AllocAdjustApprovalItemDTO::getApplyTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        long total = merged.size();
        int from = (safePageNo - 1) * safePageSize;
        if (from >= merged.size()) {
            return PageResult.of(safePageNo, safePageSize, total, Collections.emptyList());
        }
        int to = Math.min(merged.size(), from + safePageSize);
        return PageResult.of(safePageNo, safePageSize, total, new ArrayList<>(merged.subList(from, to)));
    }
```

（`toItem` / `resolveEmpName` / `resolveCustName` 为现有私有方法，不变。）

- [ ] **Step 5: 运行，确认通过 + 不回归旧用例**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl performance-engine-center \
  test -Dtest='PerfApprovalQueryFacade*Test'
```
Expected: PASS（含 Task 1.1 / 1.2 新测 + 任何已有 facade 测试）。

- [ ] **Step 6: Commit**

```bash
cd /home/djdev/lijh/yiti && git add -A && git commit -m "feat(perf): 审批列表加 statusFilter 重载（PENDING 仅待办 / DONE 仅已办）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 7: install 到本地 .m2（供网关模块编译用最新接口）**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl performance-engine-center \
  install -DskipTests
```
Expected: BUILD SUCCESS。

---

# Phase 2 — 网关分发

## Task 2.1: 网关 DTO 加 `queryStatus` 字段 + `OrigAllocData`

**Files:**
- Modify: `soap-gateway-center/src/main/java/com/bank/branch/platform/soap/controller/dto/CallPuRequest.java`
- Create: `soap-gateway-center/src/main/java/com/bank/branch/platform/soap/controller/dto/OrigAllocData.java`

- [ ] **Step 1: 给 `Parm` 加字段**

在 `CallPuRequest.Parm` 内（`apprStatus` 附近）追加：

```java
        /** 列表状态域（PERF_LIST 用）：PENDING=待审批 / DONE=已审批；空=合并。 */
        private String queryStatus;
```

- [ ] **Step 2: 新建 `OrigAllocData`**

```java
package com.bank.branch.platform.soap.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PERF_ORIG_ALLOC 成功载荷：客户原分配关系列表，字段对齐前端 applyAdd.vue flexList。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrigAllocData {

    /** 原分配明细（前端预填 flexList）。 */
    private List<OrigAllocItem> allocaters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrigAllocItem {
        /** 工号（PT_USER.USERNAME，由 USER_ID 反查）。 */
        private String username;
        /** 姓名。 */
        private String fullname;
        /** 比例（字符串）。 */
        private String ratio;
        /** 原始分配标记，固定 1（前端只读底色）。 */
        private Integer isOriginal;
    }
}
```

- [ ] **Step 3: 编译确认**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl soap-gateway-center test-compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
cd /home/djdev/lijh/yiti && git add -A && git commit -m "feat(gateway): CallPuRequest.Parm 加 queryStatus + 新增 OrigAllocData DTO

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2.2: 网关 `PERF_MY_LIST` 分发 + `WITHDRAWN` 状态码

**Files:**
- Modify: `soap-gateway-center/src/main/java/com/bank/branch/platform/soap/service/CallPuDispatchService.java`
- Test: `soap-gateway-center/src/test/java/com/bank/branch/platform/soap/service/CallPuDispatchServiceTest.java`

- [ ] **Step 1: 写失败测试**（加到 `CallPuDispatchServiceTest`）

```java
    @Test
    void perfMyList_resolvesEmployeeNo_andMapsWithdrawnTo3() {
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(user("E001", "U001")));
        AllocAdjustApprovalItemDTO dto = AllocAdjustApprovalItemDTO.builder()
                .perfAdjustNo("PA_900").applyFullname("张三").custName("某某客户")
                .status("WITHDRAWN").category("MINE").build();
        when(perfApprovalQueryApi.listMyAllocAdjustApplications(eq("U001"), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 100, 1L, List.of(dto)));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_MY_LIST");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        PerfListData data = (PerfListData) resp.getRspMsg();
        assertThat(data.getPerfs()).hasSize(1);
        assertThat(data.getPerfs().get(0).getApprStatus()).isEqualTo("3"); // WITHDRAWN→3
        verify(perfApprovalQueryApi).listMyAllocAdjustApplications("U001", 1, 100);
    }
```

- [ ] **Step 2: 运行，确认失败**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl soap-gateway-center \
  test -Dtest=CallPuDispatchServiceTest#perfMyList_resolvesEmployeeNo_andMapsWithdrawnTo3
```
Expected: FAIL（`PERF_MY_LIST` 走到 unsupported 分支，ReturnCd=99）。

- [ ] **Step 3: 实现**

`CallPuDispatchService.dispatch` 在 `PERF_LIST` 分支后加：

```java
            if ("PERF_MY_LIST".equals(ruleName)) {
                return handleMyList(parm);
            }
```

新增 handler（放在 `handlePerfList` 后）：

```java
    /**
     * PERF_MY_LIST：按申请人（createdBy）拉取"我的申请"列表，全状态（含已撤回）。
     */
    private CallPuResponse handleMyList(CallPuRequest.Parm parm) {
        String empId = parm != null ? parm.getEmployeeNo() : null;
        if (!StringUtils.hasText(empId)) {
            return CallPuResponse.fail("员工号不能为空");
        }
        String userId = resolveUserId(empId);
        PageResult<AllocAdjustApprovalItemDTO> page =
                perfApprovalQueryApi.listMyAllocAdjustApplications(userId, 1, PERF_LIST_PAGE_SIZE);
        List<PerfListItem> perfs = page.getRecords().stream().map(this::toListItem).toList();
        return CallPuResponse.ok(new PerfListData(perfs));
    }
```

`toApprStatus` 加 WITHDRAWN 分支（在现有 APPROVED/REJECTED 判断后、`return "0"` 前）：

```java
        if ("WITHDRAWN".equals(status)) {
            return "3";
        }
```

- [ ] **Step 4: 运行，确认通过**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl soap-gateway-center \
  test -Dtest=CallPuDispatchServiceTest
```
Expected: PASS（含新用例与原有用例）。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lijh/yiti && git add -A && git commit -m "feat(gateway): 新增 PERF_MY_LIST 分发 + WITHDRAWN→3 状态映射

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2.3: 网关 `PERF_LIST` 接入 `queryStatus`

**Files:**
- Modify: `soap-gateway-center/src/main/java/com/bank/branch/platform/soap/service/CallPuDispatchService.java`
- Test: `soap-gateway-center/src/test/java/com/bank/branch/platform/soap/service/CallPuDispatchServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
    @Test
    void perfList_passesQueryStatusToPerf() {
        when(userApi.getUsersByUsernames(List.of("E001"))).thenReturn(List.of(user("E001", "U001")));
        when(perfApprovalQueryApi.listAllocAdjustApprovals(eq("U001"), eq("PENDING"), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 100, 0L, List.of()));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setQueryStatus("PENDING");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_LIST");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        verify(perfApprovalQueryApi).listAllocAdjustApprovals("U001", "PENDING", 1, 100);
    }
```

> 注意：原有用例 `perfList_resolvesEmployeeNoToUserId_andReturnsMappedItems` mock 的是 3 参 `listAllocAdjustApprovals`。改造后 `handlePerfList` 改调 4 参版本（queryStatus 为 null 时传 null）。需同步把该旧用例的 stub/verify 改为 4 参：
> `when(perfApprovalQueryApi.listAllocAdjustApprovals(eq("U001"), eq(null), anyInt(), anyInt()))...`
> `verify(...).listAllocAdjustApprovals("U001", null, 1, 100);`

- [ ] **Step 2: 运行，确认失败**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl soap-gateway-center \
  test -Dtest=CallPuDispatchServiceTest#perfList_passesQueryStatusToPerf
```
Expected: FAIL。

- [ ] **Step 3: 改 `handlePerfList`**

把 `handlePerfList` 内的查询调用改为 4 参：

```java
        PageResult<AllocAdjustApprovalItemDTO> page =
                perfApprovalQueryApi.listAllocAdjustApprovals(
                        userId, parm.getQueryStatus(), 1, PERF_LIST_PAGE_SIZE);
```

（`parm` 已校验非空 empId；`getQueryStatus()` 为 null 时落到 perf 合并分支。）同步把旧用例改 4 参（见 Step 1 注）。

- [ ] **Step 4: 运行，确认通过**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl soap-gateway-center test -Dtest=CallPuDispatchServiceTest
```
Expected: PASS（全部用例）。

- [ ] **Step 5: Commit**

```bash
cd /home/djdev/lijh/yiti && git add -A && git commit -m "feat(gateway): PERF_LIST 透传 queryStatus 到 perf 状态域查询

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2.4: 网关 `PERF_ORIG_ALLOC` 分发（原分配回显）

**Files:**
- Modify: `soap-gateway-center/src/main/java/com/bank/branch/platform/soap/service/CallPuDispatchService.java`
- Test: `soap-gateway-center/src/test/java/com/bank/branch/platform/soap/service/CallPuDispatchServiceTest.java`

依赖：注入 `com.bank.branch.platform.performance.api.AllocApi`（方法 `getCurrentAllocations(custId, bizKind)` 返 `List<CustAllocRelationDTO>`，字段 `empId`(USER_ID)/`empName`(姓名)/`ratio`）。USER_ID→工号用 `userApi.getUserByEmpIds(List)`（`UserDTO.username`=工号，`UserDTO.empId`=USER_ID）。

- [ ] **Step 1: 写失败测试**（先在测试类加 `@Mock AllocApi allocApi;`，放在其它 @Mock 旁）

```java
    @Test
    void perfOrigAlloc_returnsCurrentAllocations_withUsernameReverseLookup() {
        // bizKind = CORP_DEPOSIT（applyType=1→CORP, businessType=存款→DEPOSIT）
        CustAllocRelationDTO rel = new CustAllocRelationDTO();
        rel.setEmpId("U002");
        rel.setEmpName("李四");
        rel.setRatio(new java.math.BigDecimal("60"));
        when(allocApi.getCurrentAllocations("C001", "CORP_DEPOSIT")).thenReturn(List.of(rel));
        UserDTO u = new UserDTO();
        u.setEmpId("U002");
        u.setUsername("E002");
        when(userApi.getUserByEmpIds(List.of("U002"))).thenReturn(List.of(u));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");
        parm.setBusinessType("存款");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_ORIG_ALLOC");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        OrigAllocData data = (OrigAllocData) resp.getRspMsg();
        assertThat(data.getAllocaters()).hasSize(1);
        OrigAllocData.OrigAllocItem item = data.getAllocaters().get(0);
        assertThat(item.getUsername()).isEqualTo("E002");   // USER_ID→工号
        assertThat(item.getFullname()).isEqualTo("李四");
        assertThat(item.getRatio()).isEqualTo("60");
        assertThat(item.getIsOriginal()).isEqualTo(1);
    }

    @Test
    void perfOrigAlloc_emptyAllocations_returnsEmptyList() {
        when(allocApi.getCurrentAllocations("C001", "CORP_DEPOSIT")).thenReturn(List.of());
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");
        parm.setBusinessType("存款");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_ORIG_ALLOC");
        req.setParm(parm);

        CallPuResponse resp = service.dispatch(req);

        assertThat(resp.getReturnCd()).isEqualTo("0");
        assertThat(((OrigAllocData) resp.getRspMsg()).getAllocaters()).isEmpty();
    }
```

加 import：`com.bank.branch.platform.performance.api.AllocApi`、`com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO`、`com.bank.branch.platform.soap.controller.dto.OrigAllocData`。

- [ ] **Step 2: 运行，确认失败**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl soap-gateway-center \
  test -Dtest=CallPuDispatchServiceTest#perfOrigAlloc_returnsCurrentAllocations_withUsernameReverseLookup
```
Expected: FAIL（`PERF_ORIG_ALLOC` 未支持；且 `allocApi` 字段未注入编译错误）。

- [ ] **Step 3: 实现**

在 `CallPuDispatchService` 字段区加注入：

```java
    private final com.bank.branch.platform.performance.api.AllocApi allocApi;
```

`dispatch` 加分支：

```java
            if ("PERF_ORIG_ALLOC".equals(ruleName)) {
                return handleOrigAlloc(parm);
            }
```

新增 handler：

```java
    /**
     * PERF_ORIG_ALLOC：按客户号 + 申请类型/业务类型拉取当前分配关系，供新增页回显。
     *
     * <p>bizKind 由 applyType(→custType) + businessType 拼出（复用现有映射）；
     * 分配关系 empId 为 USER_ID，反查成工号(USERNAME) 回前端 flexList。查不到回空列表（前端转手动添加）。</p>
     */
    private CallPuResponse handleOrigAlloc(CallPuRequest.Parm parm) {
        if (parm == null) {
            return CallPuResponse.fail("参数不能为空");
        }
        if (!StringUtils.hasText(parm.getCustId())) {
            return CallPuResponse.fail("客户号不能为空");
        }
        String custType = APPLY_TYPE_TO_CUST_TYPE.get(parm.getApplyType());
        if (custType == null) {
            return CallPuResponse.fail("申请类型不合法: " + parm.getApplyType());
        }
        String bizKind = toBizKind(custType, parm.getBusinessType());
        if (bizKind == null) {
            return CallPuResponse.fail("业务类型不合法: " + parm.getBusinessType());
        }

        List<CustAllocRelationDTO> rels = allocApi.getCurrentAllocations(parm.getCustId(), bizKind);
        if (rels == null || rels.isEmpty()) {
            return CallPuResponse.ok(new OrigAllocData(new ArrayList<>()));
        }

        // USER_ID → 工号(USERNAME) 反查
        Set<String> userIds = new LinkedHashSet<>();
        for (CustAllocRelationDTO r : rels) {
            if (StringUtils.hasText(r.getEmpId())) {
                userIds.add(r.getEmpId());
            }
        }
        Map<String, String> usernameByUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<UserDTO> users = userApi.getUserByEmpIds(new ArrayList<>(userIds));
            if (users != null) {
                for (UserDTO u : users) {
                    if (u != null && StringUtils.hasText(u.getEmpId())) {
                        usernameByUserId.put(u.getEmpId(), u.getUsername());
                    }
                }
            }
        }

        List<OrigAllocData.OrigAllocItem> items = new ArrayList<>(rels.size());
        for (CustAllocRelationDTO r : rels) {
            items.add(OrigAllocData.OrigAllocItem.builder()
                    .username(usernameByUserId.get(r.getEmpId()))
                    .fullname(r.getEmpName())
                    .ratio(r.getRatio() == null ? null : r.getRatio().toPlainString())
                    .isOriginal(1)
                    .build());
        }
        return CallPuResponse.ok(new OrigAllocData(items));
    }
```

加 import：`com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO`、`com.bank.branch.platform.soap.controller.dto.OrigAllocData`、`com.bank.branch.platform.auth.api.dto.UserDTO`（若未引入）。`Set/LinkedHashSet/Map/HashMap/ArrayList/List` 已 import。

- [ ] **Step 4: 运行，确认通过**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl soap-gateway-center test -Dtest=CallPuDispatchServiceTest
```
Expected: PASS（全部）。

- [ ] **Step 5: 全模块回归（确认未破坏 SOAP 端点等）**

```bash
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl soap-gateway-center test
```
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
cd /home/djdev/lijh/yiti && git add -A && git commit -m "feat(gateway): 新增 PERF_ORIG_ALLOC 分发（客户原分配关系回显，USER_ID 反查工号）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

# Phase 3 — 前端（`front/xazc_transfer_front`）

> 无测试运行器，逐文件"写 + 人工验证"；提交按 §头部约束（非 git 仓库跳过 commit）。
> 工作目录：`/home/djdev/lijh/front/xazc_transfer_front`。

## Task 3.1: 报文封装工具 `callpu.js`（不走 `@CF`）

**Files:**
- Create: `performanceAdjustment/callpu.js`

- [ ] **Step 1: 新建文件**

```js
// 业绩调整 callpu 报文封装：复刻现有双层 params/Parm 结构，避免各页手抄出错。
// 注意：按业务要求不走 @CF 别名，调用方用相对路径 import。
export function buildParam(ruleName, parm, intType = "get") {
  return {
    params: {
      params: { Parm: parm },
      Parm: parm,
      RuleName: ruleName,
      IntType: intType,
      SrvicName1: sessionStorage.parameters
    }
  };
}
```

- [ ] **Step 2: 人工验证**：文件存在、语法正确（`node -e "require('./performanceAdjustment/callpu.js')"` 因 ESM 可能报错可忽略，仅检查无明显笔误）。

- [ ] **Step 3: Commit**（非 git 跳过）

```bash
cd /home/djdev/lijh/front/xazc_transfer_front && git add performanceAdjustment/callpu.js && git commit -m "feat(front): 新增 callpu 报文封装工具"
```

---

## Task 3.2: `index.vue` 拆三块导航

**Files:**
- Modify: `views/index.vue`

- [ ] **Step 1: 替换 `typeList`**

把现有 `typeList` 数组（头寸管理 / 业绩调整 / 定价审批）改为删除"业绩调整"项、追加三块：

```js
      typeList: [
        {
          name: "头寸管理",
          path: require("@A/imgs/index/icon_1.png"),
          pathName: "/travelList"
        },
        {
          name: "定价审批",
          path: require("@A/imgs/index/icon_3.png"),
          pathName: "/priceList"
        },
        {
          name: "我的申请",
          path: require("@A/imgs/index/icon_2.png"),
          pathName: "/performanceMyList"
        },
        {
          name: "待审批",
          path: require("@A/imgs/index/icon_2.png"),
          pathName: "/performancePending"
        },
        {
          name: "已审批",
          path: require("@A/imgs/index/icon_2.png"),
          pathName: "/performanceApproved"
        }
      ]
```

> 图标暂复用 `icon_2.png`；待业务方提供正式图后替换 `path`。

- [ ] **Step 2: 人工验证**：首页九宫格显示 5 块，点"我的申请/待审批/已审批"分别跳对应路由（路由由对方工程登记后生效）。

- [ ] **Step 3: Commit**（非 git 跳过）

---

## Task 3.3: `performanceMyList.vue`（我的申请）

**Files:**
- Create: `performanceAdjustment/performanceMyList.vue`

差异化：显示客户名称 + 申请时间 + 状态徽标（含已撤回）；底部固定"新增"。

- [ ] **Step 1: 新建文件**

```vue
<template>
  <div class="adjustList">
    <div class="applyList">
      <ul>
        <li v-for="(item, index) in list" :key="index" @click="linkInfo(item)">
          <div class="cellBox clearfix">
            <div class="cellInfo fl">
              <div class="clearfix">
                <div class="mainContent fl">
                  <p><span>客户名称：{{ item.custName }}</span></p>
                  <p>申请时间：{{ item.applyTime }}</p>
                </div>
                <div class="mainStatus fl">
                  <span v-if="item.apprStatus == '0'">待审核</span>
                  <span v-else-if="item.apprStatus == '1'" class="ok">已同意</span>
                  <span v-else-if="item.apprStatus == '2'" class="no">已拒绝</span>
                  <span v-else-if="item.apprStatus == '3'" class="gray">已撤回</span>
                </div>
              </div>
            </div>
          </div>
        </li>
      </ul>
    </div>
    <div class="orderWrap">
      <button class="addOrder" @click="applyAdd">新增</button>
    </div>
  </div>
</template>
<script>
import config from "@CF/config";
import { buildParam } from "./callpu";
export default {
  data() {
    return { list: [] };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      const param = buildParam("PERF_MY_LIST", { EmployeeNo: sessionStorage.empId });
      this.$toast.loading({ duration: 0, forbidClick: true, message: "加载中" });
      this.$HttpPostForm
        .post(config.PostURL, null, { params: param })
        .then(response => {
          this.$toast.clear();
          if (response.ReturnCd == "0") {
            if (response.RspMsg && response.RspMsg != "null") {
              this.list = response.RspMsg.perfs || [];
            } else {
              this.$toast.fail("暂无数据");
            }
          } else {
            this.$toast.fail(response.RspMsg);
          }
        })
        .catch(() => {
          this.$toast.clear();
          this.$toast.fail("请求超时");
        });
    },
    linkInfo(item) {
      this.$router.push({
        path: "/performanceInfo",
        query: { id: item.perfAdjustNo, listType: "mine" }
      });
    },
    applyAdd() {
      this.$router.push({ path: "/performanceAdd" });
    }
  }
};
</script>
<style>
.adjustList { padding: 0; margin: 0; width: 100%; position: relative; }
.adjustList .applyList { padding-bottom: 70px; }
.adjustList ul { padding: 0; margin: 0; }
.adjustList ul li { width: 100%; padding: 12px 5px 8px; box-sizing: border-box; background: #fff; margin-top: 5px; }
.adjustList ul li .cellInfo { width: 100%; }
.adjustList ul li .cellInfo .mainContent { width: 75%; padding: 0 8px; box-sizing: border-box; }
.adjustList ul li .cellInfo .mainContent p:nth-child(1) { color: #333; font-size: 14px; }
.adjustList ul li .cellInfo .mainContent p:nth-child(2) { color: #808080; padding-top: 8px; font-size: 14px; }
.adjustList ul li .cellInfo .mainStatus { width: 25%; line-height: 40px; font-size: 14px; }
.adjustList ul li .cellInfo .mainStatus .ok { color: #07c160; }
.adjustList ul li .cellInfo .mainStatus .no { color: #ee0a24; }
.adjustList ul li .cellInfo .mainStatus .gray { color: #999; }
.adjustList .orderWrap { position: fixed; left: 0; bottom: 0; width: 100%; height: 55px; background: #fff; text-align: center; }
.adjustList .addOrder { background: #1a93f9; border: 1px solid #1a93f9; color: #fff; width: 90%; height: 45px; line-height: 45px; font-size: 18px; border-radius: 6px; }
</style>
```

- [ ] **Step 2: 人工验证**：进入页面调 `PERF_MY_LIST`；撤回单显"已撤回"灰色；底部"新增"跳 `/performanceAdd`。

- [ ] **Step 3: Commit**（非 git 跳过）

---

## Task 3.4: `performancePending.vue`（待审批）

**Files:**
- Create: `performanceAdjustment/performancePending.vue`

差异化：突出申请人 + 客户 + 申请时间；左侧蓝色待办竖条；无状态徽标、无新增。

- [ ] **Step 1: 新建文件**

```vue
<template>
  <div class="pendingList">
    <ul>
      <li v-for="(item, index) in list" :key="index" @click="linkInfo(item)">
        <div class="bar"></div>
        <div class="content">
          <p class="title">{{ item.applyFullname }} · {{ item.custName }}</p>
          <p class="time">申请时间：{{ item.applyTime }}</p>
        </div>
        <span class="tag">待审批</span>
      </li>
    </ul>
    <p v-if="list.length === 0" class="empty">暂无待审批</p>
  </div>
</template>
<script>
import config from "@CF/config";
import { buildParam } from "./callpu";
export default {
  data() {
    return { list: [] };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      const param = buildParam("PERF_LIST", {
        EmployeeNo: sessionStorage.empId,
        queryStatus: "PENDING"
      });
      this.$toast.loading({ duration: 0, forbidClick: true, message: "加载中" });
      this.$HttpPostForm
        .post(config.PostURL, null, { params: param })
        .then(response => {
          this.$toast.clear();
          if (response.ReturnCd == "0") {
            this.list = (response.RspMsg && response.RspMsg.perfs) || [];
          } else {
            this.$toast.fail(response.RspMsg);
          }
        })
        .catch(() => {
          this.$toast.clear();
          this.$toast.fail("请求超时");
        });
    },
    linkInfo(item) {
      this.$router.push({
        path: "/performanceInfo",
        query: { id: item.perfAdjustNo, listType: "pending" }
      });
    }
  }
};
</script>
<style>
.pendingList { padding: 0; margin: 0; }
.pendingList ul { padding: 0; margin: 0; }
.pendingList li { position: relative; display: flex; align-items: center; background: #fff; margin-top: 5px; padding: 14px 10px 14px 0; }
.pendingList li .bar { width: 4px; height: 38px; background: #1a93f9; margin-right: 10px; }
.pendingList li .content { flex: 1; }
.pendingList li .title { color: #333; font-size: 15px; font-weight: 500; }
.pendingList li .time { color: #808080; font-size: 13px; padding-top: 6px; }
.pendingList li .tag { color: #1a93f9; font-size: 13px; border: 1px solid #1a93f9; border-radius: 3px; padding: 1px 6px; }
.pendingList .empty { text-align: center; color: #999; padding-top: 40px; font-size: 14px; }
</style>
```

- [ ] **Step 2: 人工验证**：调 `PERF_LIST` + `queryStatus:PENDING`；左侧蓝条 + "待审批"角标；点进详情底部出"审批"。

- [ ] **Step 3: Commit**（非 git 跳过）

---

## Task 3.5: `performanceApproved.vue`（已审批）

**Files:**
- Create: `performanceAdjustment/performanceApproved.vue`

差异化：结果徽标（已同意=绿/已拒绝=红）+ 客户 + 审批时间；灰底只读。

- [ ] **Step 1: 新建文件**

```vue
<template>
  <div class="approvedList">
    <ul>
      <li v-for="(item, index) in list" :key="index" @click="linkInfo(item)">
        <div class="content">
          <p class="title">{{ item.custName }}</p>
          <p class="time">申请时间：{{ item.applyTime }}</p>
        </div>
        <span class="result ok" v-if="item.apprStatus == '1'">已同意</span>
        <span class="result no" v-else-if="item.apprStatus == '2'">已拒绝</span>
        <span class="result" v-else>{{ item.apprStatus }}</span>
      </li>
    </ul>
    <p v-if="list.length === 0" class="empty">暂无已审批</p>
  </div>
</template>
<script>
import config from "@CF/config";
import { buildParam } from "./callpu";
export default {
  data() {
    return { list: [] };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      const param = buildParam("PERF_LIST", {
        EmployeeNo: sessionStorage.empId,
        queryStatus: "DONE"
      });
      this.$toast.loading({ duration: 0, forbidClick: true, message: "加载中" });
      this.$HttpPostForm
        .post(config.PostURL, null, { params: param })
        .then(response => {
          this.$toast.clear();
          if (response.ReturnCd == "0") {
            this.list = (response.RspMsg && response.RspMsg.perfs) || [];
          } else {
            this.$toast.fail(response.RspMsg);
          }
        })
        .catch(() => {
          this.$toast.clear();
          this.$toast.fail("请求超时");
        });
    },
    linkInfo(item) {
      this.$router.push({
        path: "/performanceInfo",
        query: { id: item.perfAdjustNo, listType: "approved" }
      });
    }
  }
};
</script>
<style>
.approvedList { padding: 0; margin: 0; background: #f1f1f1; min-height: 100vh; }
.approvedList ul { padding: 0; margin: 0; }
.approvedList li { display: flex; align-items: center; background: #fafafa; margin-top: 5px; padding: 14px 10px; }
.approvedList li .content { flex: 1; }
.approvedList li .title { color: #555; font-size: 15px; }
.approvedList li .time { color: #999; font-size: 13px; padding-top: 6px; }
.approvedList li .result { font-size: 14px; }
.approvedList li .result.ok { color: #07c160; }
.approvedList li .result.no { color: #ee0a24; }
.approvedList .empty { text-align: center; color: #999; padding-top: 40px; font-size: 14px; }
</style>
```

- [ ] **Step 2: 人工验证**：调 `PERF_LIST` + `queryStatus:DONE`；结果徽标颜色正确；灰底只读；点进详情无操作按钮（Task 3.7 保证）。

- [ ] **Step 3: Commit**（非 git 跳过）

---

## Task 3.6: `applyAdd.vue` 原分配自动预填

**Files:**
- Modify: `performanceAdjustment/applyAdd.vue`

- [ ] **Step 1: 引入工具 + 新增 fetchOrigAlloc**

文件顶部 `<script>` 内，在 `import config from "@CF/config";` 后加：

```js
import { buildParam } from "./callpu";
```

在 `methods` 内新增：

```js
    // 客户号 + 申请类型 + 业务类型齐备时，自动拉取客户原分配关系并预填（isOriginal=1 只读行）。
    fetchOrigAlloc() {
      const { custId, applyType, businessType } = this.dataForm;
      if (!custId || !applyType || !businessType) {
        return;
      }
      const param = buildParam(
        "PERF_ORIG_ALLOC",
        { EmployeeNo: sessionStorage.empId, custId, applyType, businessType },
        "get"
      );
      this.$HttpPostForm
        .post(config.PostURL, null, { params: param })
        .then(response => {
          if (response.ReturnCd === "0" && response.RspMsg && response.RspMsg.allocaters) {
            const orig = response.RspMsg.allocaters.map(a => ({
              username: a.username,
              fullname: a.fullname,
              ratio: a.ratio,
              isOriginal: 1
            }));
            // 只替换原分配行，保留用户已手动添加的行（isOriginal===2）
            const manual = this.flexList.filter(r => r.isOriginal === 2);
            this.flexList = orig.length > 0
              ? orig.concat(manual)
              : (manual.length > 0 ? manual : [{ username: "", fullname: "", ratio: "", isOriginal: 2 }]);
          }
        })
        .catch(() => {});
    },
```

- [ ] **Step 2: 三处触发**

- `custIdChange()` 末尾（查名 `then` 成功分支之后或方法末尾）追加调用：`this.fetchOrigAlloc();`
- `onApplyTypeConfirm(val)` 末尾追加：`this.fetchOrigAlloc();`
- `businessType1()` 方法体加：`this.fetchOrigAlloc();`

> 注：现有手动新增行 `addAdjust` 已把新行标记 `isOriginal = 2`；初始行 `{username:"",fullname:"",ratio:""}` 无 `isOriginal`，会被视为非手动行，重拉时可能被替换——可接受（初始空行本就该被原分配覆盖）。如需保留首个空行，把 data 初始 `flexList` 改为 `[{ username: "", fullname: "", ratio: "", isOriginal: 2 }]`。

- [ ] **Step 3: 人工验证**：选客户号→选申请类型→选业务类型后，分配明细自动出现原分配行；查不到时保留空行可手动添加；提交逻辑不变（`submitCust` 仍读 `flexList`）。

- [ ] **Step 4: Commit**（非 git 跳过）

---

## Task 3.7: `applyInfo.vue` 已审批只读 + 删除 `list.vue`

**Files:**
- Modify: `performanceAdjustment/applyInfo.vue`
- Delete: `performanceAdjustment/list.vue`

- [ ] **Step 1: 详情读 listType，approved 上下文隐藏操作**

`applyInfo.vue` 的 `data` 加 `listType: ""`；`created` 内读取：

```js
    this.listType = this.$route.query.listType || "";
```

底部 `van-tabbar` 三个按钮的 `v-if` 各 `&& listType !== 'approved'`，例如：

```html
      <van-tabbar-item v-if="dataForm.isCanAppr==1 && listType !== 'approved'" @click="checkHandle('2')">
```
对 `isCanOpera` / `isCanDelete` 两项同样追加 `&& listType !== 'approved'`。

- [ ] **Step 2: 删除 list.vue**

```bash
rm performanceAdjustment/list.vue
```

> 路由 `/performanceList` 由对方工程下线（见 spec §8 交接清单）。

- [ ] **Step 3: 人工验证**：从"已审批"进详情无任何底部操作按钮；从"我的申请"进可见"撤回"；从"待审批"进可见"审批"。

- [ ] **Step 4: Commit**（非 git 跳过）

---

# 收尾验证

- [ ] **后端全量回归**

```bash
cd /home/djdev/lijh/yiti
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -q -pl performance-engine-center,soap-gateway-center test
```
Expected: BUILD SUCCESS。

- [ ] **交接路由清单**：通知对方工程登记 `/performanceMyList` `/performancePending` `/performanceApproved`，`/performanceInfo` 接受可选 `query.listType`，下线 `/performanceList`（spec §8）。

- [ ] **人工联调**：手机端首页五块 → 三列表取数隔离 → 新增原分配回显 → 撤回单只读。

---

## 自检（Self-Review）记录

- **Spec 覆盖**：§4.1 perf 两接口→Task 1.1/1.2；§4.2 网关三分发→Task 2.1-2.4；§4.3 WITHDRAWN→Task 2.2 Step 3；§5.1 index→Task 3.2；§5.2 三页→Task 3.3-3.5 + 删除 list.vue Task 3.7；§5.3 差异化→各页 template/style；§5.4 原分配→Task 3.6；§5.5 详情只读→Task 3.7；§5.6 callpu.js→Task 3.1；§8 交接→收尾。全覆盖。
- **占位符扫描**：无 TBD/TODO；每个改码步骤含完整代码。
- **类型/命名一致**：`listMyAllocAdjustApplications` / `listAllocAdjustApprovals(empId,statusFilter,pageNo,pageSize)` / `queryStatus` / `OrigAllocData.OrigAllocItem` / `buildParam(ruleName,parm,intType)` / apprStatus `"3"`=WITHDRAWN 全文一致。
- **已知微调点**（执行期按实际 DTO 形态确认，不阻塞）：`EmployeeDTO` / `AdjustTodoRespDTO` 的构造方式（builder vs setter）；`selectByConditions` mapper XML 是否已含 `ORDER BY created_time DESC`。
