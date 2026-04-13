# Customer-Marketing-Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the customer-marketing-center module — 36 REST endpoints across 7 capability domains (tag management, lead management, customer master, customer pool, claim management, touch tasks, touch reports) + 5 external APIs, using strict TDD with Service unit tests, Mapper tests, and Controller MockMvc tests.

**Architecture:** Maven module following auth/governance/workflow/portal patterns — Entity -> Mapper XML -> Service -> Facade(*ApiImpl) -> Controller. Events via `@TransactionalEventListener(AFTER_COMMIT)`. Three development phases: P1 (foundation + tags, serial), P2 (lead + customer + claim, parallel agents), P3 (touch + reports + external APIs, parallel agents).

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, MySQL 8.0, JUnit 5, Mockito, AssertJ, H2 (test)

**Reference files:**
- Spec: `docs/superpowers/specs/2026-04-13-customer-marketing-center-design.md`
- 接口契约权威: `docs/modules/customer-marketing-center/03-接口设计与报文.md`
- 后端架构: `docs/modules/customer-marketing-center/02-后端架构.md`
- 对外API契约: `docs/modules/customer-marketing-center/04-对外API契约.md`
- 表结构DDL: `docs/modules/customer-marketing-center/05-表结构DDL.md`
- 并发事务策略: `docs/modules/customer-marketing-center/06-并发与事务策略.md`
- 审计要求: `docs/modules/customer-marketing-center/07-审计要求.md`
- 初始化数据: `docs/modules/customer-marketing-center/08-初始化数据清单.md`
- 依赖契约: `docs/modules/customer-marketing-center/09-依赖契约摘要.md`
- 共享规范: `docs/common-dev-guide.md`
- 参考模块: `portal-content-center/` (最新参考), `auth-permission-center/`, `system-governance-center/`

**IMPORTANT - Before each task:**
1. Read the spec sections and source doc sections referenced in the task
2. Verify actual method signatures of cross-module APIs by reading source files
3. Follow `portal-content-center` patterns (最新参考模块)
4. Use `superpowers:test-driven-development` skill for every coding task

**IMPORTANT - Common module classes to use:**

| Class | Package | Purpose |
|---|---|---|
| `ResponseWrapper<T>` | `com.bank.branch.platform.common.web` | Standard API response (`success(data)` / `success()` / `error(code, msg)`) |
| `PageRequest` / `PageResult<T>` | `com.bank.branch.platform.common.web` | Standard pagination (POJO, use setters) |
| `BizException` | `com.bank.branch.platform.common.web.exception` | Business exception (`new BizException(code, message)`) |
| `@BizAuth` | `com.bank.branch.platform.common.security.annotation` | Method-level RBAC |
| `BizType` enum | `com.bank.branch.platform.common.security.enums` | TAG, LEAD, CUSTOMER, CUSTOMER_POOL, CLAIM, TOUCH_TASK, TOUCH_REPORT |
| `BizAction` enum | `com.bank.branch.platform.common.security.enums` | READ / LIST / WRITE / DELETE / EXPORT / IMPORT / TRANSFER |
| `DataScopeContext` (POJO) | `com.bank.branch.platform.common.security.context` | ThreadLocal holder, field `.scope` (NOT `.scopeType`) |
| `DataScopeContext` (record) | `com.bank.branch.platform.auth.api.dto` | `BizScopeApi.buildScopeContext()` return, field `.scopeType` |
| `CurrentUserApi` | `com.bank.branch.platform.auth.api` | `getCurrentEmpId()` / `getCurrentOrgCode()` |
| `BizScopeApi` | `com.bank.branch.platform.auth.api` | `buildScopeContext(empId, BizType, BizAction)` |
| `OrgApi` | `com.bank.branch.platform.auth.api` | `getOrg(orgCode)` / `getOrgSubtreeCodes(orgCode)` |
| `DictApi` | `com.bank.branch.platform.governance.api` | `getDictLabel(type, code)` |
| `FileApi` | `com.bank.branch.platform.governance.api` | `getDownloadUrl(fileId)` / `bindFile(...)` |
| `AuditApi` | `com.bank.branch.platform.governance.api` | `log(cmd)` |
| `WorkflowApi` | `com.bank.branch.platform.workflow.api` | `startProcess(cmd)` |

**IMPORTANT - DDL 为枚举值权威来源:**
- `cust_tag.status`: `ACTIVE` / `DISABLED` (NOT ENABLED)
- `cust_lead.lead_status`: `DRAFT` / `SUBMITTED` / `IN_APPROVAL` / `APPROVED` / `REJECTED` (五态)
- `cust_master.status`: `ACTIVE` / `INACTIVE` (NOT VALID/DELETED)
- `touch_task.task_status`: `PENDING` / `SUCCESS` / `CANCELLED` (三态, NO IN_PROGRESS)
- 错误码前缀: `CUST-{HTTP后两位}{序号}` (见 02-后端架构.md 第 304-398 行)

**IMPORTANT - `PageResult` 使用静态工厂方法:**
`PageResult.of(pageNo, pageSize, total, records)` — 不要用 setters

**IMPORTANT - `PageRequest` 没有 `(int, int)` 构造函数:**
使用 `PageRequest p = new PageRequest(); p.setPageNo(1); p.setPageSize(20);`

**IMPORTANT - 高危操作标注方式:**
`@BizAuth` 注解不支持 `highRisk` 属性。高危操作通过 `@AuditLog(level=HIGH)` 独立标注（来自 common-aop）。

**IMPORTANT - DTO 转换方式:**
项目不使用 MapStruct。使用手动转换（参照 portal-content-center 模式），在 Service 或 Controller 层内联完成。

**IMPORTANT - 完整事件和监听器清单:**
本计划需创建以下所有事件和监听器（分布在各 Task 中）：
- Events: `LeadApprovedEvent`, `LeadDeletedEvent`, `ClaimCreatedEvent`, `ClaimCancelledEvent`, `ClaimTransferredEvent`, `TouchCompletedEvent`, `CustomerDeletedEvent`
- Listeners: `LeadApprovedListener` (Task 2.B), `LeadDeletedListener` (Task 2.B), `WorkflowCallbackListener` (Task 2.A), `TouchTaskCompletedListener` (Task 3.D)

**IMPORTANT - DataScope 统一选择:**
- ThreadLocal: `com.bank.branch.platform.common.security.context.DataScopeContext` (POJO, field `.scope`)
- Mapper XML OGNL: `q.dataScope.scope.name() == 'ALL'`

---

## Phase 0: Module Scaffolding & Infrastructure

### Task 0.1: Create Maven Module + Update Root POM

**Files:**
- Create: `customer-marketing-center/pom.xml`
- Modify: `pom.xml` (root) — add `<module>customer-marketing-center</module>`
- Modify: `bootstrap/pom.xml` — add dependency

**Reference:** Read `portal-content-center/pom.xml` and `pom.xml` (root) for exact format.

- [ ] **Step 1: Create `customer-marketing-center/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.bank.branch.platform</groupId>
        <artifactId>branch-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>customer-marketing-center</artifactId>
    <name>customer-marketing-center</name>
    <description>客户营销中心 — 标签、线索、客户、认领、触达全生命周期管理</description>

    <dependencies>
        <!-- Common 基础设施 -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-trace</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-db</artifactId>
        </dependency>

        <!-- 强依赖模块 -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>auth-permission-center</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>workflow-center</artifactId>
        </dependency>

        <!-- 弱依赖模块 -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>system-governance-center</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- MyBatis -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
        </dependency>

        <!-- EasyExcel (导入/导出) -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>easyexcel</artifactId>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Add module to root POM**

In `pom.xml` root, add `<module>customer-marketing-center</module>` to `<modules>` section (after `portal-content-center`).

- [ ] **Step 3: Add dependency to bootstrap POM**

In `bootstrap/pom.xml`, add:
```xml
<dependency>
    <groupId>com.bank.branch.platform</groupId>
    <artifactId>customer-marketing-center</artifactId>
</dependency>
```

- [ ] **Step 4: Create directory structure**

```bash
mkdir -p customer-marketing-center/src/main/java/com/bank/branch/platform/customer/{api/dto,controller,facade,service,mapper,entity,listener,event,enums,convert,dto/req,dto/resp,util,config}
mkdir -p customer-marketing-center/src/main/resources/mapper/customer
mkdir -p customer-marketing-center/src/test/java/com/bank/branch/platform/customer/{service,controller}
mkdir -p customer-marketing-center/src/test/resources
```

- [ ] **Step 5: Verify compilation**

Run: `mvn clean compile -pl customer-marketing-center -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add customer-marketing-center/ pom.xml bootstrap/pom.xml
git commit -m "feat(customer): Task 0.1 - Maven module scaffolding"
```

### Task 0.2: Create Enums (Error Codes + Status Enums)

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/CustomerErrorCode.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/TagStatus.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/LeadStatus.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/LeadOp.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/BatchStatus.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/ClaimStatus.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/CustMasterStatus.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/TouchTaskStatus.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/TouchTaskType.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/SlaStatus.java`

**Reference:** Read `02-后端架构.md` lines 230-398 for complete enum definitions. Read `auth-permission-center/.../enums/AuthErrorCode.java` for pattern.

- [ ] **Step 1: Create `CustomerErrorCode.java`**

Follow `AuthErrorCode` pattern exactly. Error codes from `02-后端架构.md` lines 304-398. Format: `CUST-{HTTP后两位}{序号}`.

```java
package com.bank.branch.platform.customer.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户营销中心错误码枚举
 * 格式: CUST-{HTTP状态码后两位}{序号}
 */
@Getter
@AllArgsConstructor
public enum CustomerErrorCode {

    // 400 参数/业务错误
    TAG_NAME_BLANK("CUST-40001", "标签名称不能为空"),
    TAG_CODE_BLANK("CUST-40002", "标签编码不能为空"),
    LEAD_NOT_DRAFT("CUST-40003", "线索非草稿状态，不允许编辑"),
    LEAD_NOT_SUBMITTABLE("CUST-40004", "线索状态不允许提交审批"),
    IMPORT_FILE_EMPTY("CUST-40005", "导入文件为空"),
    IMPORT_ROW_LIMIT_EXCEEDED("CUST-40006", "导入数据超过行数限制"),
    TOUCH_TASK_NOT_PENDING("CUST-40007", "触达任务非待处理状态"),
    TRANSFER_REASON_REQUIRED("CUST-40008", "转交原因不能为空"),
    CANCEL_REASON_REQUIRED("CUST-40009", "取消原因不能为空"),

    // 404 资源不存在
    TAG_NOT_FOUND("CUST-40401", "标签不存在"),
    LEAD_NOT_FOUND("CUST-40402", "线索不存在"),
    CUSTOMER_NOT_FOUND("CUST-40403", "客户不存在"),
    CLAIM_NOT_FOUND("CUST-40404", "认领记录不存在"),
    TOUCH_TASK_NOT_FOUND("CUST-40405", "触达任务不存在"),
    BATCH_NOT_FOUND("CUST-40406", "导入批次不存在"),

    // 409 冲突
    TAG_NAME_DUPLICATE("CUST-40901", "标签名称已存在"),
    TAG_CODE_DUPLICATE("CUST-40902", "标签编码已存在"),
    LEAD_NO_DUPLICATE("CUST-40903", "线索编号已存在"),
    CUSTOMER_ALREADY_CLAIMED("CUST-40904", "客户已被该机构认领"),
    TOUCH_LOG_DUPLICATE("CUST-40905", "触达日志重复提交"),
    TAG_CODE_IMMUTABLE("CUST-40906", "标签编码创建后不可修改"),

    // 500 内部错误
    INTERNAL_ERROR("CUST-50001", "客户营销服务内部错误"),
    WORKFLOW_ERROR("CUST-50002", "工作流调用异常");

    private final String code;
    private final String message;
}
```

- [ ] **Step 2: Create status enums (all with DDL-aligned values)**

`TagStatus.java`:
```java
@Getter
@AllArgsConstructor
public enum TagStatus {
    ACTIVE("ACTIVE", "启用"),
    DISABLED("DISABLED", "停用");
    private final String code;
    private final String label;
}
```

`LeadStatus.java`:
```java
@Getter
@AllArgsConstructor
public enum LeadStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    IN_APPROVAL("IN_APPROVAL", "审批中"),
    APPROVED("APPROVED", "审批通过"),
    REJECTED("REJECTED", "审批驳回");
    private final String code;
    private final String label;
}
```

`LeadOp.java`:
```java
@Getter
@AllArgsConstructor
public enum LeadOp {
    CREATE("CREATE", "新建"),
    UPDATE("UPDATE", "修改"),
    DELETE("DELETE", "删除");
    private final String code;
    private final String label;
}
```

`BatchStatus.java`:
```java
@Getter
@AllArgsConstructor
public enum BatchStatus {
    CREATED("CREATED", "已创建"),
    PENDING_APPROVAL("PENDING_APPROVAL", "待审批"),
    APPROVED("APPROVED", "审批通过"),
    REJECTED("REJECTED", "审批驳回");
    private final String code;
    private final String label;
}
```

`ClaimStatus.java`:
```java
@Getter
@AllArgsConstructor
public enum ClaimStatus {
    CLAIMED("CLAIMED", "已认领"),
    CANCELLED("CANCELLED", "已取消");
    private final String code;
    private final String label;
}
```

`TouchTaskType.java`:
```java
@Getter
@AllArgsConstructor
public enum TouchTaskType {
    FIRST_TOUCH("FIRST_TOUCH", "首次触达"),
    FOLLOW_UP("FOLLOW_UP", "后续跟进");
    private final String code;
    private final String label;
}
```

`CustMasterStatus.java`:
```java
@Getter
@AllArgsConstructor
public enum CustMasterStatus {
    ACTIVE("ACTIVE", "正常"),
    INACTIVE("INACTIVE", "非活跃");
    private final String code;
    private final String label;
}
```

`TouchTaskStatus.java`:
```java
@Getter
@AllArgsConstructor
public enum TouchTaskStatus {
    PENDING("PENDING", "待处理"),
    SUCCESS("SUCCESS", "成功完成"),
    CANCELLED("CANCELLED", "已取消");
    private final String code;
    private final String label;
}
```

`SlaStatus.java`:
```java
@Getter
@AllArgsConstructor
public enum SlaStatus {
    GREEN("GREEN", "正常"),
    YELLOW("YELLOW", "预警"),
    RED("RED", "超期");
    private final String code;
    private final String label;
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -pl customer-marketing-center -am`

- [ ] **Step 4: Commit**

```bash
git add customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/
git commit -m "feat(customer): Task 0.2 - Error codes + status enums (DDL-aligned)"
```

### Task 0.3: Create All 8 Entity Classes

**Files:**
- Create: 8 entity classes in `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/`

**Reference:** Read `05-表结构DDL.md` for complete field definitions. Follow `portal-content-center/.../entity/ProductInfo.java` pattern.

- [ ] **Step 1: Create all entity classes**

Each entity is a `@Data` POJO with fields matching DDL columns (camelCase, MyBatis auto-maps). NO `@TableId`/`@TableName` (raw MyBatis, not MP).

**CustTag.java** — 对应 `cust_tag` 表:
```java
@Data
public class CustTag {
    private String id;
    private String tagName;
    private String tagCode;
    private String tagDesc;
    private Integer tagPriority;
    private String status;        // ACTIVE / DISABLED
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
```

**CustTagRel.java** — 对应 `cust_tag_rel` 表:
```java
@Data
public class CustTagRel {
    private String id;
    private String custId;
    private String tagId;
    private String createdBy;
    private LocalDateTime createdTime;
}
```

**CustLead.java** — 对应 `cust_lead` 表 (所有字段参见 DDL):
```java
@Data
public class CustLead {
    private String id;
    private String leadNo;
    private String leadOp;           // CREATE / UPDATE / DELETE
    private String sourceCustId;
    private String prevLeadId;
    private Integer versionNo;
    private Integer isLatest;        // 1=最新, 0=历史
    private String custName;
    private String unifiedCreditCode;
    private String tagIds;           // JSON array
    private String contactPerson;
    private String contactMobile;
    private String industry;
    private String groupType;
    private String customerType;
    private Integer isKeystone;
    private String enterpriseType;
    private String groupName;
    private Integer isAccountOpened;
    private String customerDesc;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private String leadSource;
    private String leadStatus;       // DRAFT / SUBMITTED / IN_APPROVAL / APPROVED / REJECTED
    private String ownerOrgId;
    private String assignedTo;
    private String createdBy;
    private String businessKey;
    private String importBatchId;
    private String processInstanceId;
    private String remark;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
```

**LeadImportBatch.java** — 对应 `lead_import_batch` 表:
```java
@Data
public class LeadImportBatch {
    private String id;
    private String batchNo;
    private String fileName;
    private String fileObjectId;
    private String fileMd5;
    private Integer totalRowCount;
    private Integer errorRowCount;
    private String status;           // CREATED / PENDING_APPROVAL / APPROVED / REJECTED
    private String errorDetail;      // JSON
    private String ownerOrgId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
```

**CustMaster.java** — 对应 `cust_master` 表:
```java
@Data
public class CustMaster {
    private String id;
    private String custNo;
    private String custName;
    private String unifiedCreditCode;
    private String contactPerson;
    private String contactMobile;
    private String industry;
    private String groupType;
    private String customerType;
    private Integer isKeystone;
    private String enterpriseType;
    private String groupName;
    private Integer isAccountOpened;
    private String customerDesc;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private String status;           // ACTIVE / INACTIVE
    private String leadId;           // 关联线索ID
    private String ownerOrgId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
```

**CustClaim.java** — 对应 `cust_claim` 表:
```java
@Data
public class CustClaim {
    private String id;
    private String custId;
    private String orgId;
    private String claimedBy;
    private String maintainerEmpId;
    private String claimStatus;      // CLAIMED / CANCELLED
    private String cancelReason;
    private LocalDateTime claimedTime;
    private LocalDateTime cancelledTime;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
```

**TouchTask.java** — 对应 `touch_task` 表:
```java
@Data
public class TouchTask {
    private String id;
    private String taskNo;
    private String taskType;         // FIRST_TOUCH / FOLLOW_UP
    private String custId;
    private String claimId;
    private String assigneeEmpId;
    private String assigneeOrgId;
    private String taskStatus;       // PENDING / SUCCESS / CANCELLED
    private String slaStatus;        // GREEN / YELLOW / RED
    private LocalDateTime planFinishTime;
    private LocalDateTime warningTime;
    private LocalDateTime actualFinishTime;
    private String cancelReason;
    private String businessKey;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
```

**TouchLog.java** — 对应 `touch_log` 表:
```java
@Data
public class TouchLog {
    private String id;
    private String touchTaskId;
    private String clientUuid;       // 移动端生成, 幂等键
    private String touchType;
    private String touchResult;
    private String touchContent;
    private String photoUrls;        // JSON array (MinIO paths)
    private String createdBy;
    private LocalDateTime createdTime;
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -pl customer-marketing-center -am`

- [ ] **Step 3: Commit**

```bash
git add customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/
git commit -m "feat(customer): Task 0.3 - All 8 entity classes (DDL-aligned)"
```

### Task 0.4: Create All 8 Mapper Interfaces + XML Files

**Files:**
- Create: 8 Mapper interfaces in `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/mapper/`
- Create: 8 Mapper XML files in `customer-marketing-center/src/main/resources/mapper/customer/`

**Reference:** Read `05-表结构DDL.md` for column names. Read `portal-content-center/.../mapper/ProductInfoMapper.java` and XML for pattern.

- [ ] **Step 1: Create Mapper interfaces**

每个 Mapper 接口包含基本 CRUD + 分页 + 统计方法。以 `CustTagMapper` 为例：

```java
package com.bank.branch.platform.customer.mapper;

import com.bank.branch.platform.customer.entity.CustTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CustTagMapper {
    CustTag selectById(String id);
    CustTag selectByTagName(String tagName);
    CustTag selectByTagCode(String tagCode);
    List<CustTag> selectPage(@Param("keyword") String keyword,
                             @Param("status") String status,
                             @Param("offset") int offset,
                             @Param("limit") int limit);
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status);
    List<CustTag> selectEnabled();
    int insert(CustTag tag);
    int updateById(CustTag tag);
}
```

其余 7 个 Mapper 接口同理，根据各表的查询需求定义方法。关键方法：
- `CustTagRelMapper`: `selectByCustId`, `selectByTagId`, `deleteByCustIdAndTagId`, `deleteByTagId`, `insertBatch`
- `CustLeadMapper`: `selectById`, `selectByLeadNo`, `selectLatestBySourceCustId`, `selectPage` (含 DataScope), `countPage`, `insert`, `updateById`, `updateStatusById`, `selectForUpdate`
- `LeadImportBatchMapper`: `selectById`, `selectByBatchNo`, `selectPage`, `insert`, `updateById`
- `CustMasterMapper`: `selectById`, `selectByCustNo`, `selectPage` (含 DataScope), `countPage`, `insert`, `updateById`
- `CustClaimMapper`: `selectById`, `selectByCustIdAndOrgId`, `selectByCustId`, `selectByClaimedBy`, `selectPoolPage`, `countPoolPage`, `selectMyClaimsPage`, `countMyClaimsPage`, `insert`, `updateById`
- `TouchTaskMapper`: `selectById`, `selectByTaskNo`, `selectPage` (含 DataScope), `countPage`, `selectPendingForSlaRefresh`, `insert`, `updateById`
- `TouchLogMapper`: `selectByTaskId`, `selectByTaskIdAndClientUuid`, `insert`

- [ ] **Step 2: Create corresponding XML files**

每个 XML 包含 `<sql id="BASE_COLUMNS">` + 基本 CRUD 语句。以 `CustTagMapper.xml` 为例：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.customer.mapper.CustTagMapper">

    <sql id="BASE_COLUMNS">
        id, tag_name, tag_code, tag_desc, tag_priority, status,
        created_by, created_time, updated_by, updated_time, deleted
    </sql>

    <select id="selectById" resultType="com.bank.branch.platform.customer.entity.CustTag">
        SELECT <include refid="BASE_COLUMNS"/>
        FROM cust_tag WHERE id = #{id} AND deleted = 0
    </select>

    <select id="selectByTagName" resultType="com.bank.branch.platform.customer.entity.CustTag">
        SELECT <include refid="BASE_COLUMNS"/>
        FROM cust_tag WHERE LOWER(tag_name) = LOWER(#{tagName}) AND deleted = 0
    </select>

    <select id="selectByTagCode" resultType="com.bank.branch.platform.customer.entity.CustTag">
        SELECT <include refid="BASE_COLUMNS"/>
        FROM cust_tag WHERE tag_code = #{tagCode} AND deleted = 0
    </select>

    <select id="selectEnabled" resultType="com.bank.branch.platform.customer.entity.CustTag">
        SELECT <include refid="BASE_COLUMNS"/>
        FROM cust_tag WHERE status = 'ACTIVE' AND deleted = 0
        ORDER BY tag_priority ASC, created_time DESC
    </select>

    <select id="selectPage" resultType="com.bank.branch.platform.customer.entity.CustTag">
        SELECT <include refid="BASE_COLUMNS"/>
        FROM cust_tag
        <where>
            deleted = 0
            <if test="keyword != null and keyword != ''">
                AND (tag_name LIKE CONCAT('%', #{keyword}, '%')
                     OR tag_code LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null and status != ''">
                AND status = #{status}
            </if>
        </where>
        ORDER BY created_time DESC
        LIMIT #{offset}, #{limit}
    </select>

    <select id="countPage" resultType="long">
        SELECT COUNT(*) FROM cust_tag
        <where>
            deleted = 0
            <if test="keyword != null and keyword != ''">
                AND (tag_name LIKE CONCAT('%', #{keyword}, '%')
                     OR tag_code LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null and status != ''">
                AND status = #{status}
            </if>
        </where>
    </select>

    <insert id="insert" parameterType="com.bank.branch.platform.customer.entity.CustTag">
        INSERT INTO cust_tag (id, tag_name, tag_code, tag_desc, tag_priority, status,
                              created_by, created_time, updated_by, updated_time, deleted)
        VALUES (#{id}, #{tagName}, #{tagCode}, #{tagDesc}, #{tagPriority}, #{status},
                #{createdBy}, #{createdTime}, #{updatedBy}, #{updatedTime}, 0)
    </insert>

    <update id="updateById" parameterType="com.bank.branch.platform.customer.entity.CustTag">
        UPDATE cust_tag
        <set>
            <if test="tagName != null">tag_name = #{tagName},</if>
            <if test="tagDesc != null">tag_desc = #{tagDesc},</if>
            <if test="tagPriority != null">tag_priority = #{tagPriority},</if>
            <if test="status != null">status = #{status},</if>
            <if test="updatedBy != null">updated_by = #{updatedBy},</if>
            updated_time = NOW(),
        </set>
        WHERE id = #{id} AND deleted = 0
    </update>
</mapper>
```

其余 7 个 XML 同理，按各表 DDL 字段编写。

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -pl customer-marketing-center -am`

- [ ] **Step 4: Commit**

```bash
git add customer-marketing-center/src/main/java/com/bank/branch/platform/customer/mapper/
git add customer-marketing-center/src/main/resources/mapper/customer/
git commit -m "feat(customer): Task 0.4 - All 8 Mapper interfaces + XML"
```

### Task 0.5: Create Test Infrastructure

**Files:**
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/CustomerTestConfiguration.java`
- Create: `customer-marketing-center/src/test/resources/application-test.yml`

**Reference:** Read `auth-permission-center/src/test/java/.../AuthTestConfiguration.java`

- [ ] **Step 1: Create test configuration**

```java
package com.bank.branch.platform.customer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.bank.branch.platform.customer")
@MapperScan("com.bank.branch.platform.customer.mapper")
public class CustomerTestConfiguration {
}
```

- [ ] **Step 2: Create test application.yml**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  session:
    store-type: none

mybatis:
  mapper-locations: classpath*:mapper/**/*Mapper.xml
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.bank.branch.platform.customer: DEBUG
```

- [ ] **Step 3: Commit**

```bash
git add customer-marketing-center/src/test/
git commit -m "feat(customer): Task 0.5 - Test infrastructure"
```

---

## Phase 1: Tag Domain (TDD, Serial)

### Task 1.1: TagService — Create Tag (Red-Green-Refactor)

**Files:**
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/service/TagServiceTest.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/TagService.java`

**Reference:** Read `03-接口设计与报文.md` lines 173-211 for create tag spec.

- [ ] **Step 1: Write failing test for createTag**

```java
package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock CustTagMapper tagMapper;
    @InjectMocks TagService tagService;

    @Test
    void createTag_shouldInsertAndReturnTag() {
        when(tagMapper.selectByTagName("VIP客户")).thenReturn(null);
        when(tagMapper.selectByTagCode("VIP")).thenReturn(null);
        when(tagMapper.insert(any(CustTag.class))).thenReturn(1);

        CustTag result = tagService.createTag("VIP客户", "VIP", "重要客户", 1, "emp001");

        assertThat(result).isNotNull();
        assertThat(result.getTagName()).isEqualTo("VIP客户");
        assertThat(result.getTagCode()).isEqualTo("VIP");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getId()).isNotBlank();
        verify(tagMapper).insert(any(CustTag.class));
    }

    @Test
    void createTag_shouldThrowWhenNameDuplicate() {
        when(tagMapper.selectByTagName("VIP客户")).thenReturn(new CustTag());

        assertThatThrownBy(() -> tagService.createTag("VIP客户", "VIP", null, null, "emp001"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode())
                .isEqualTo(CustomerErrorCode.TAG_NAME_DUPLICATE.getCode()));
    }

    @Test
    void createTag_shouldThrowWhenCodeDuplicate() {
        when(tagMapper.selectByTagName("新标签")).thenReturn(null);
        when(tagMapper.selectByTagCode("VIP")).thenReturn(new CustTag());

        assertThatThrownBy(() -> tagService.createTag("新标签", "VIP", null, null, "emp001"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode())
                .isEqualTo(CustomerErrorCode.TAG_CODE_DUPLICATE.getCode()));
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run: `cd customer-marketing-center && mvn test -Dtest=TagServiceTest -pl . -am`
Expected: FAIL (TagService class not found)

- [ ] **Step 3: Implement minimal TagService.createTag**

```java
package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TagStatus;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 标签管理服务
 * 负责标签的 CRUD、启用/禁用操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final CustTagMapper tagMapper;

    /**
     * 创建标签
     * 校验 tagName 和 tagCode 全行唯一
     */
    @Transactional
    public CustTag createTag(String tagName, String tagCode, String tagDesc,
                             Integer tagPriority, String operatorEmpId) {
        // 唯一性校验
        if (tagMapper.selectByTagName(tagName) != null) {
            throw new BizException(CustomerErrorCode.TAG_NAME_DUPLICATE.getCode(),
                                   CustomerErrorCode.TAG_NAME_DUPLICATE.getMessage());
        }
        if (tagMapper.selectByTagCode(tagCode) != null) {
            throw new BizException(CustomerErrorCode.TAG_CODE_DUPLICATE.getCode(),
                                   CustomerErrorCode.TAG_CODE_DUPLICATE.getMessage());
        }

        CustTag tag = new CustTag();
        tag.setId(UUID.randomUUID().toString().replace("-", ""));
        tag.setTagName(tagName);
        tag.setTagCode(tagCode);
        tag.setTagDesc(tagDesc);
        tag.setTagPriority(tagPriority != null ? tagPriority : 0);
        tag.setStatus(TagStatus.ACTIVE.getCode());
        tag.setCreatedBy(operatorEmpId);
        tag.setCreatedTime(LocalDateTime.now());
        tag.setDeleted(0);
        tagMapper.insert(tag);

        log.info("[TagService.createTag] 标签创建成功 id={}, tagCode={}", tag.getId(), tagCode);
        return tag;
    }
}
```

- [ ] **Step 4: Run test to verify GREEN**

Run: `cd customer-marketing-center && mvn test -Dtest=TagServiceTest -pl . -am`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add customer-marketing-center/src/
git commit -m "feat(customer): Task 1.1 - TagService.createTag (TDD green)"
```

### Task 1.2: TagService — Update, Toggle Status, Query (TDD)

**Files:**
- Modify: `TagServiceTest.java` — add tests for updateTag, toggleStatus, getById, listPage, listEnabled
- Modify: `TagService.java` — implement methods

**Reference:** Read `03-接口设计与报文.md` for each endpoint's spec.

- [ ] **Step 1: Write failing tests for remaining TagService methods**

Add to `TagServiceTest.java`:
```java
@Test
void updateTag_shouldThrowWhenCodeChanged() {
    CustTag existing = makeTag("t1", "VIP客户", "VIP");
    when(tagMapper.selectById("t1")).thenReturn(existing);
    
    assertThatThrownBy(() -> tagService.updateTag("t1", "新名称", "NEW_CODE", null, null, "emp001"))
        .isInstanceOf(BizException.class)
        .satisfies(e -> assertThat(((BizException) e).getCode())
            .isEqualTo(CustomerErrorCode.TAG_CODE_IMMUTABLE.getCode()));
}

@Test
void updateTag_shouldSucceedWhenCodeUnchanged() {
    CustTag existing = makeTag("t1", "VIP客户", "VIP");
    when(tagMapper.selectById("t1")).thenReturn(existing);
    when(tagMapper.selectByTagName("新名称")).thenReturn(null);
    when(tagMapper.updateById(any())).thenReturn(1);
    
    CustTag result = tagService.updateTag("t1", "新名称", "VIP", "新描述", 2, "emp001");
    assertThat(result.getTagName()).isEqualTo("新名称");
    verify(tagMapper).updateById(any());
}

@Test
void toggleStatus_shouldDisableActiveTag() {
    CustTag tag = makeTag("t1", "VIP", "VIP");
    tag.setStatus("ACTIVE");
    when(tagMapper.selectById("t1")).thenReturn(tag);
    when(tagMapper.updateById(any())).thenReturn(1);
    
    tagService.toggleStatus("t1", "DISABLED", "emp001");
    verify(tagMapper).updateById(argThat(t -> "DISABLED".equals(t.getStatus())));
}

@Test
void listEnabled_shouldReturnActiveTagsOnly() {
    when(tagMapper.selectEnabled()).thenReturn(List.of(makeTag("t1", "A", "A")));
    List<CustTag> result = tagService.listEnabled();
    assertThat(result).hasSize(1);
}

@Test
void listPage_shouldCallMapperWithCorrectOffset() {
    when(tagMapper.selectPage("key", null, 0, 20)).thenReturn(List.of());
    when(tagMapper.countPage("key", null)).thenReturn(0L);
    
    PageResult<CustTag> result = tagService.listPage("key", null, 1, 20);
    assertThat(result.getTotal()).isEqualTo(0);
    verify(tagMapper).selectPage("key", null, 0, 20);
}

private CustTag makeTag(String id, String name, String code) {
    CustTag t = new CustTag();
    t.setId(id);
    t.setTagName(name);
    t.setTagCode(code);
    t.setStatus("ACTIVE");
    return t;
}
```

- [ ] **Step 2: Run tests — RED**

- [ ] **Step 3: Implement updateTag, toggleStatus, getById, listPage, listEnabled**

Add to `TagService.java`:
```java
/** 更新标签（tagCode 不可修改） */
@Transactional
public CustTag updateTag(String id, String tagName, String tagCode,
                         String tagDesc, Integer tagPriority, String operatorEmpId) {
    CustTag existing = tagMapper.selectById(id);
    if (existing == null) {
        throw new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(),
                               CustomerErrorCode.TAG_NOT_FOUND.getMessage());
    }
    // tagCode 创建后锁定
    if (tagCode != null && !tagCode.equals(existing.getTagCode())) {
        throw new BizException(CustomerErrorCode.TAG_CODE_IMMUTABLE.getCode(),
                               CustomerErrorCode.TAG_CODE_IMMUTABLE.getMessage());
    }
    // tagName 唯一性
    if (tagName != null && !tagName.equals(existing.getTagName())) {
        CustTag dup = tagMapper.selectByTagName(tagName);
        if (dup != null && !dup.getId().equals(id)) {
            throw new BizException(CustomerErrorCode.TAG_NAME_DUPLICATE.getCode(),
                                   CustomerErrorCode.TAG_NAME_DUPLICATE.getMessage());
        }
    }
    existing.setTagName(tagName != null ? tagName : existing.getTagName());
    existing.setTagDesc(tagDesc);
    existing.setTagPriority(tagPriority);
    existing.setUpdatedBy(operatorEmpId);
    tagMapper.updateById(existing);
    log.info("[TagService.updateTag] id={}", id);
    return existing;
}

/** 启用/禁用标签 */
@Transactional
public void toggleStatus(String id, String status, String operatorEmpId) {
    CustTag tag = tagMapper.selectById(id);
    if (tag == null) {
        throw new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(),
                               CustomerErrorCode.TAG_NOT_FOUND.getMessage());
    }
    tag.setStatus(status);
    tag.setUpdatedBy(operatorEmpId);
    tagMapper.updateById(tag);
    log.info("[TagService.toggleStatus] id={}, status={}", id, status);
}

/** 按 ID 查询 */
public CustTag getById(String id) {
    CustTag tag = tagMapper.selectById(id);
    if (tag == null) {
        throw new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(),
                               CustomerErrorCode.TAG_NOT_FOUND.getMessage());
    }
    return tag;
}

/** 分页查询 */
public PageResult<CustTag> listPage(String keyword, String status, int pageNo, int pageSize) {
    int offset = (pageNo - 1) * pageSize;
    List<CustTag> list = tagMapper.selectPage(keyword, status, offset, pageSize);
    long total = tagMapper.countPage(keyword, status);
    return PageResult.of(pageNo, pageSize, total, list);
}

/** 启用标签列表（选择器用） */
public List<CustTag> listEnabled() {
    return tagMapper.selectEnabled();
}
```

- [ ] **Step 4: Run tests — GREEN**

- [ ] **Step 5: Commit**

```bash
git add customer-marketing-center/src/
git commit -m "feat(customer): Task 1.2 - TagService CRUD complete (TDD green)"
```

### Task 1.3: TagController (TDD with MockMvc)

**Files:**
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/controller/TagControllerTest.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/TagController.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/dto/req/TagCreateReqDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/dto/req/TagUpdateReqDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/dto/req/TagStatusReqDTO.java`

**Reference:** Read `03-接口设计与报文.md` lines 78-300 for all tag endpoints.

- [ ] **Step 1: Write failing MockMvc tests**

Cover all 5 basic tag endpoints (import/export in Task 1.4).

- [ ] **Step 2: RED — run tests**

- [ ] **Step 3: Create DTO classes + implement TagController**

```java
package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.TagCreateReqDTO;
import com.bank.branch.platform.customer.dto.req.TagStatusReqDTO;
import com.bank.branch.platform.customer.dto.req.TagUpdateReqDTO;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理控制器
 * 负责标签的 CRUD、启用/禁用
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tags")
@Tag(name = "标签管理", description = "全行统一标签字典管理")
public class TagController {

    private final TagService tagService;
    private final CurrentUserApi currentUserApi;

    @GetMapping
    @Operation(summary = "标签列表（分页）")
    @BizAuth(bizType = BizType.TAG, action = BizAction.LIST)
    public ResponseWrapper<PageResult<CustTag>> listPage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[TagController.listPage] keyword={}, status={}", keyword, status);
        return ResponseWrapper.success(tagService.listPage(keyword, status, pageNo, pageSize));
    }

    @GetMapping("/enabled")
    @Operation(summary = "启用标签列表（选择器）")
    public ResponseWrapper<List<CustTag>> listEnabled() {
        return ResponseWrapper.success(tagService.listEnabled());
    }

    @PostMapping
    @Operation(summary = "新增标签")
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    public ResponseWrapper<CustTag> create(@Valid @RequestBody TagCreateReqDTO req) {
        log.info("[TagController.create] tagCode={}", req.getTagCode());
        // TODO: get current empId from CurrentUserApi
        CustTag tag = tagService.createTag(req.getTagName(), req.getTagCode(),
                req.getTagDesc(), req.getTagPriority(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success(tag);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑标签")
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    public ResponseWrapper<CustTag> update(@PathVariable("id") String id,
                                           @Valid @RequestBody TagUpdateReqDTO req) {
        log.info("[TagController.update] id={}", id);
        CustTag tag = tagService.updateTag(id, req.getTagName(), req.getTagCode(),
                req.getTagDesc(), req.getTagPriority(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success(tag);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用标签")
    @BizAuth(bizType = BizType.TAG, action = BizAction.WRITE)
    public ResponseWrapper<Void> toggleStatus(@PathVariable("id") String id,
                                              @Valid @RequestBody TagStatusReqDTO req) {
        log.info("[TagController.toggleStatus] id={}, status={}", id, req.getStatus());
        tagService.toggleStatus(id, req.getStatus(), currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success();
    }
}
```

- [ ] **Step 4: GREEN — run tests**

- [ ] **Step 5: Commit**

```bash
git add customer-marketing-center/src/
git commit -m "feat(customer): Task 1.3 - TagController with 5 endpoints (TDD green)"
```

### Task 1.4: TagImportController + TagImportService (TDD)

**Files:**
- Create: `TagImportService.java` + `TagImportServiceTest.java`
- Create: `TagImportController.java`

**Reference:** Read `03-接口设计与报文.md` lines 258-315 for tag customer import/export.

- [ ] **Step 1-5: TDD cycle for tag customer import (覆盖式) and export**

Tag customer import: 覆盖式 delete + insert on `cust_tag_rel`. 高危操作 `@AuditLog(level=HIGH)`.
Tag customer export: 读取标签关联的客户列表，高危操作。

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(customer): Task 1.4 - TagImportService + TagImportController (TDD green)"
```

### Task 1.5: Tag Domain Integration + BizType Registration

**Files:**
- Verify all 7 tag endpoints work
- Register BizType.TAG in `common-security` if not exists
- Add `PT_RESOURCE` seed SQL for tag endpoints

- [ ] **Step 1: Verify BizType.TAG exists**

Read `common-security/.../enums/BizType.java`. If TAG not listed, add it.

- [ ] **Step 2: Create PT_RESOURCE seed SQL**

```sql
-- docs/superpowers/sql/2026-04-13-customer-marketing-pt-resource.sql
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, STATUS, SYS_CODE)
VALUES
('C_TAG_LIST', '/api/tags', 'GET', '标签列表', 0, 'CUSTOMER'),
('C_TAG_ENABLED', '/api/tags/enabled', 'GET', '启用标签列表', 0, 'CUSTOMER'),
('C_TAG_CREATE', '/api/tags', 'POST', '新增标签', 0, 'CUSTOMER'),
('C_TAG_UPDATE', '/api/tags/*', 'PUT', '编辑标签', 0, 'CUSTOMER'),
('C_TAG_STATUS', '/api/tags/*/status', 'PUT', '标签启停', 0, 'CUSTOMER'),
('C_TAG_IMP', '/api/tags/*/customers/import', 'POST', '标签客户导入', 0, 'CUSTOMER'),
('C_TAG_EXP', '/api/tags/*/customers/export', 'GET', '标签客户导出', 0, 'CUSTOMER');
```

- [ ] **Step 3: Full compilation check**

Run: `mvn clean compile -pl customer-marketing-center,bootstrap -am`

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(customer): Task 1.5 - Tag domain integration + PT_RESOURCE seed"
```

---

## Phase 2: Core Business Chain (Parallel Sub-Agents)

> **Execution:** Phase 2 tasks (2.A, 2.B, 2.C) are designed for parallel sub-agent execution in separate git worktrees. Each agent works on an independent capability domain. Merge back to main branch after all complete.

### Task 2.A: Lead Domain (Agent A — 11 endpoints)

**Scope:** LeadService, LeadVersionService, LeadImportService, LeadController, LeadImportController

**Files to create:**
- `service/LeadService.java` + test
- `service/LeadVersionService.java` + test
- `service/LeadImportService.java` + test
- `controller/LeadController.java` + test
- `controller/LeadImportController.java` + test
- `dto/req/LeadCreateReqDTO.java`, `LeadUpdateReqDTO.java`, `LeadImportReqDTO.java`
- `dto/resp/LeadRespDTO.java`, `LeadImportPreviewRespDTO.java`, `BatchRespDTO.java`
- `event/LeadApprovedEvent.java`
- `event/LeadDeletedEvent.java`
- `listener/WorkflowCallbackListener.java`

**Reference docs:**
- `03-接口设计与报文.md` lines 321-720 (all lead endpoints)
- `06-并发与事务策略.md` (线索提交: SELECT FOR UPDATE)
- `01-功能规格.md` lines 100-300 (版本化规则)

**TDD order:**
1. `LeadService.createDraft()` — 新建草稿 (v1, CREATE op, DRAFT status)
2. `LeadService.updateDraft()` — 编辑草稿 (only DRAFT allowed)
3. `LeadService.deleteDraft()` — 删除草稿
4. `LeadService.submitForApproval()` — 提交审批 (SELECT FOR UPDATE + WorkflowApi.startProcess)
5. `LeadVersionService.createEditVersion()` — 已通过线索生成新版本 (new version, prev_lead_id, is_latest flip)
6. `LeadImportService.preview()` — 导入预览 (只读, 不写库)
7. `LeadImportService.execute()` — 执行导入 (整批事务)
8. `LeadService.getById()`, `listPage()`, `getBatchById()`, `listBatches()`
9. `LeadController` + `LeadImportController` MockMvc tests
10. PT_RESOURCE seed SQL for 11 endpoints

**Key business rules:**
- 状态机: DRAFT -> SUBMITTED -> IN_APPROVAL -> APPROVED / REJECTED
- 版本化: CREATE (v1), UPDATE (new version from APPROVED), DELETE (delete version from APPROVED)
- `is_latest` flip: 新版本设 1, 旧版本设 0
- `prev_lead_id`: UPDATE/DELETE 操作时指向被修订版本
- SELECT FOR UPDATE 防并发提交
- 审批集成: `WorkflowApi.startProcess(cmd)` — mock in unit tests
- 发布 `LeadApprovedEvent` on approval callback

### Task 2.B: Customer Master Domain (Agent B — 6 endpoints)

**Scope:** CustomerService, CustMasterAssemblerService, CustomerHistoryService, CustomerController, CustomerHistoryController

**Files to create:**
- `service/CustomerService.java` + test
- `service/CustMasterAssemblerService.java` + test
- `service/CustomerHistoryService.java` + test
- `controller/CustomerController.java` + test
- `controller/CustomerHistoryController.java` + test
- `listener/LeadApprovedListener.java`
- `listener/LeadDeletedListener.java`
- `event/ClaimTransferredEvent.java`
- `event/CustomerDeletedEvent.java`
- `dto/req/TransferReqDTO.java`, `DeleteApplyReqDTO.java`
- `dto/resp/CustomerRespDTO.java`, `CustomerHistoryRespDTO.java`

**Reference docs:**
- `03-接口设计与报文.md` lines 740-970 (customer endpoints)
- `02-后端架构.md` lines 192-206 (CustMasterAssemblerService)

**TDD order:**
1. `CustMasterAssemblerService.assembleFromLead()` — 从线索装配客户主档
2. `LeadApprovedListener` — 监听 LeadApprovedEvent, 调用 assembler
3. `CustomerService.getById()`, `listPage()` (含 DataScope)
4. `CustomerHistoryService.getHistory()` — 跨机构历史 (JOIN cust_claim)
5. `CustomerService.transfer()` — 转交维护人 (高危, reason required)
6. `CustomerService.deleteApply()` — 发起删除审批 (WorkflowApi)
7. `CustomerController` + `CustomerHistoryController` MockMvc tests
8. PT_RESOURCE seed SQL

**Key business rules:**
- 客户自动生成: LeadApprovedEvent -> CustMasterAssemblerService
- status: ACTIVE / INACTIVE
- 转交: 更新 cust_claim.maintainer_emp_id, 高危操作 @AuditLog
- 删除申请: 走工作流审批, 审批通过标记 INACTIVE + deleted=1
- DataScope: ORG_SUBTREE

### Task 2.C: Customer Pool + Claim Domain (Agent C — 4 endpoints)

**Scope:** ClaimService, CustomerPoolService, CustomerPoolController, ClaimController

**Files to create:**
- `service/ClaimService.java` + test
- `service/CustomerPoolService.java` + test
- `controller/CustomerPoolController.java` + test
- `controller/ClaimController.java` + test
- `event/ClaimCreatedEvent.java`, `ClaimCancelledEvent.java`
- `dto/req/ClaimReqDTO.java`, `CancelClaimReqDTO.java`
- `dto/resp/PoolCustomerRespDTO.java`, `ClaimRespDTO.java`

**Reference docs:**
- `03-接口设计与报文.md` lines 980-1160 (pool + claim endpoints)
- `06-并发与事务策略.md` (认领: UK constraint)

**TDD order:**
1. `CustomerPoolService.listPool()` — 待认领池列表 (未被认领的客户)
2. `ClaimService.claim()` — 认领 (INSERT, UK(cust_id,org_id) 防重)
3. `ClaimService.cancelClaim()` — 取消认领 (高危, reason required)
4. `ClaimService.listMyClaims()` — 已认领列表
5. 发布 ClaimCreatedEvent / ClaimCancelledEvent
6. `CustomerPoolController` + `ClaimController` MockMvc tests
7. PT_RESOURCE seed SQL

**Key business rules:**
- 争抢认领: INSERT cust_claim, catch DuplicateKeyException -> CUST-40904
- 取消认领: 更新 claim_status, 高危操作
- 无分布式锁, 依赖数据库唯一键
- DataScope: CUSTOMER_POOL=ALL/ORG_SUBTREE, CLAIM=ORG

---

## Phase 3: Sales Process (Parallel Sub-Agents)

> **Execution:** Phase 3 tasks (3.D, 3.E, 3.F) are designed for parallel sub-agent execution. Depends on Phase 2 completion.

### Task 3.D: Touch Task Domain (Agent D — 5 endpoints)

**Scope:** TouchTaskService, TouchLogService, TouchTaskController

**Files to create:**
- `service/TouchTaskService.java` + test
- `service/TouchLogService.java` + test
- `controller/TouchTaskController.java` + test
- `listener/ClaimCreatedListener.java` (认领成功 -> 创建首次触达任务)
- `event/TouchCompletedEvent.java`
- `listener/TouchTaskCompletedListener.java`
- `dto/req/TouchLogReqDTO.java`
- `dto/resp/TouchTaskRespDTO.java`, `TouchLogRespDTO.java`

**Reference docs:**
- `03-接口设计与报文.md` lines 1192-1380 (touch endpoints)
- `01-功能规格.md` lines 540-700 (SLA rules)

**TDD order:**
1. `TouchTaskService.createFromClaim()` — 认领后创建首次触达任务
2. `ClaimCreatedListener` — 监听 ClaimCreatedEvent
3. `TouchTaskService.complete()` — 完成任务 (PENDING -> SUCCESS)
4. `TouchTaskService.cancel()` — 取消任务 (PENDING -> CANCELLED)
5. `TouchLogService.addLog()` — 添加触达日志 (幂等: UK(task_id, client_uuid))
6. `TouchTaskService.listPage()`, `getById()`
7. SLA 定时刷新: `TouchTaskService.refreshSla()` (GREEN->YELLOW->RED)
8. `TouchTaskController` MockMvc tests
9. PT_RESOURCE seed SQL

**Key business rules:**
- 任务状态: PENDING -> SUCCESS / CANCELLED (三态)
- SLA: GREEN -> YELLOW -> RED, 定时任务 `0 */30 * * * ?`
- 触达日志幂等: UK(touch_task_id, client_uuid)
- 照片: MinIO (FileApi), paths stored in JSON
- DataScope: SELF_ASSIGNED

### Task 3.E: Touch Report Domain (Agent E — 3 endpoints)

**Scope:** TouchReportService, TouchReportController

**Files to create:**
- `service/TouchReportService.java` + test
- `controller/TouchReportController.java` + test
- `dto/resp/TouchReportRespDTO.java`, `TouchStatisticRespDTO.java`

**Reference docs:**
- `03-接口设计与报文.md` lines 1390-1460 (report endpoints)

**TDD order:**
1. `TouchReportService.listPage()` — 聚合查询 (JOIN touch_task + touch_log)
2. `TouchReportService.statistic()` — 触达统计
3. `TouchReportService.export()` — 导出 (高危)
4. `TouchReportController` MockMvc tests
5. PT_RESOURCE seed SQL

### Task 3.F: External APIs (Agent F — 7 ApiImpl classes)

**Scope:** All 7 Facade implementations: TagApiImpl, LeadApiImpl, CustomerApiImpl, CustomerQueryApiImpl, ClaimApiImpl, TouchTaskApiImpl, TouchTaskQueryApiImpl

**Files to create:**
- `api/TagApi.java` + `facade/TagApiImpl.java` + test
- `api/LeadApi.java` + `facade/LeadApiImpl.java` + test
- `api/CustomerQueryApi.java` + `facade/CustomerQueryApiImpl.java` + test
- `api/ClaimApi.java` + `facade/ClaimApiImpl.java` + test
- `api/TouchTaskQueryApi.java` + `facade/TouchTaskQueryApiImpl.java` + test
- `api/dto/*.java` (all external DTOs)

**Reference docs:**
- `04-对外API契约.md` (all API contracts)

**TDD order:**
1. `TagApiImpl` (listEnabled, getById)
2. `LeadApiImpl` (getById, getByLeadNo, listByIds)
3. `CustomerQueryApiImpl` (9 methods: getCustomer, listCustomers, searchCustomers, isValidCustomer, isClaimedByOrg, getCustomerClaims, hasRunningProcess, listRunningProcesses, countCustomers)
4. `ClaimApiImpl` (getClaimsByCustomer, getClaimedOrgs, getMaintainer)
5. `TouchTaskQueryApiImpl` (getTaskStatus, getSlaStatus)
6. Cache configuration: `CustomerCacheConfig.java` (Redis TTL 5min for tags/customers)

**Key:** These are thin wrappers delegating to Service layer. Add `@Cacheable` where spec requires caching.

---

## Phase 4: Final Integration

### Task 4.1: Module CLAUDE.md

**Files:**
- Create: `customer-marketing-center/CLAUDE.md`

Document module structure, development patterns, key decisions.

### Task 4.2: Full Compilation + Bootstrap Integration Test

- [ ] **Step 1:** `mvn clean compile -pl bootstrap -am` — full compile
- [ ] **Step 2:** `mvn test -pl customer-marketing-center` — all module tests
- [ ] **Step 3:** Verify bootstrap starts: `cd bootstrap && mvn spring-boot:run` (manual smoke test)

### Task 4.3: Final Commit + Summary

```bash
git commit -m "feat(customer): Task 4 - customer-marketing-center complete (36 endpoints, 8 tables, TDD)"
```

---

## Summary

| Phase | Tasks | Endpoints | Parallel? |
|-------|-------|-----------|-----------|
| P0 | Module + Enums + Entity + Mapper + Test infra | 0 | Serial |
| P1 | Tag domain (7 endpoints) | 7 | Serial |
| P2 | Lead (11) + Customer (6) + Claim (4) | 21 | 3 agents parallel |
| P3 | Touch (5) + Report (3) + External APIs | 8 + APIs | 3 agents parallel |
| P4 | Integration + CLAUDE.md | 0 | Serial |

**Total: 36 endpoints, 8 tables, 14 services, 7 ApiImpl, 7 events/listeners**
