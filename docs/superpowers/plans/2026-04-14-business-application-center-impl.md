# business-application-center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the business-application-center module with 2 business domains (Loan + Support), 21 REST endpoints, 5 external APIs, deep workflow integration, and full TDD coverage.

**Architecture:** Modular monolith with Spring Boot. Two core domains (LoanApply, SupportRequest) sharing a BizStateMachine for state transitions. Deep integration with workflow-center via WorkflowApi for process lifecycle. SUPPORT/SUPPORT_DEPT dual-view permission model.

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, MySQL 8.0, Redis, Flowable 7.0.1 (via workflow-center API)

**Reference files:**
- Spec: `docs/superpowers/specs/2026-04-14-business-application-center-design.md`
- DDL: `docs/schema/ddl-bizapp.sql`
- Module docs: `docs/modules/business-application-center/` (01-09)
- Shared dev guide: `docs/common-dev-guide.md`
- Reference module: `customer-marketing-center/` (patterns, test infra, code style)

---

## CRITICAL NOTES FOR SUB-AGENTS

Read this entire section BEFORE starting any task. Violations will cause compilation or test failures.

### Entity Fields MUST Match DDL

The authoritative field source is `docs/schema/ddl-bizapp.sql`. Entity Java field names use camelCase auto-mapped from snake_case columns via `map-underscore-to-camel-case: true`. DO NOT invent fields.

**loan_apply columns (18 business fields):**
`id`, `apply_no`, `cust_id`, `source_touch_task_id`, `project_type`, `biz_type`, `guarantee_type`, `credit_amount`, `credit_exposure_amount`, `status`, `business_key`, `process_instance_id`, `owner_org_id`, `created_by`, `created_time`, `updated_by`, `updated_time`, `deleted`

**support_request columns (20 business fields):**
`id`, `request_no`, `submit_group_id`, `cust_id`, `source_touch_task_id`, `product_id`, `support_dept_id`, `other_demand`, `dispatch_emp_id`, `dispatch_time`, `assigned_emp_id`, `status`, `business_key`, `process_instance_id`, `owner_org_id`, `created_by`, `created_time`, `updated_by`, `updated_time`, `deleted`

### No MapStruct -- Manual DTO Conversion

Inline conversion in Service or Controller layer. No external mapping libraries.

### BizException Usage

```java
throw new BizException(BizAppErrorCode.XXX.getCode(), BizAppErrorCode.XXX.getMessage());
```

### Pagination Pattern

```java
int offset = (pageNo - 1) * pageSize;
List<T> records = mapper.selectPage(..., offset, pageSize);
long total = mapper.countPage(...);
return PageResult.of(pageNo, pageSize, total, records);
```

### ResponseWrapper Pattern

- `ResponseWrapper.success(data)` -- single object
- `ResponseWrapper.page(pageResult)` -- paginated result
- `ResponseWrapper.success()` -- void operations

### Event Pattern

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```
MUST catch all exceptions to prevent propagation to the publishing transaction.

### Test H2 Schema

Must create `loan_apply` and `support_request` tables in H2-compatible DDL for integration tests. H2 MySQL mode: `jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`

### Cross-Module APIs -- Mock Only in Tests

- `CurrentUserApi` -- `getCurrentEmpId()`, `getCurrentOrgCode()`
- `WorkflowApi` -- `startProcess(StartProcessCmd)`, `getProcessByBusinessKey(String)`, `getProcessByBizTypeAndBizId(String, String)`
- `CustomerQueryApi` -- `getCustomer(id)`, `isValidCustomer(custId)`, `isClaimedByOrg(custId, orgId)`
- `TouchTaskQueryApi` -- `getTaskById(taskId)` (returns TouchTask entity with `assigneeEmpId`)
- `ProductApi` -- `getProduct(id)`, `getProducts(ids)`, `listSupportAvailableProducts()`, `getProductResponsibleEmpIds(productId)`
- `AddressBookApi` -- `getEmployee(empId)`, `listEmployeesByOrg(orgCode)`
- `BizScopeApi`, `OrgApi`, `DictApi`, `FileApi`, `NotifyApi`, `AuditApi`

### Common Module Classes

| Class | Package | Purpose |
|---|---|---|
| `ResponseWrapper<T>` | `com.bank.branch.platform.common.web` | `success(data)` / `success()` / `page(result)` |
| `PageResult<T>` | `com.bank.branch.platform.common.web` | `PageResult.of(pageNo, pageSize, total, records)` |
| `BizException` | `com.bank.branch.platform.common.web.exception` | `new BizException(code, message)` |
| `@BizAuth` | `com.bank.branch.platform.common.security.annotation` | Method-level RBAC |
| `BizType` enum | `com.bank.branch.platform.common.security.enums` | LOAN, SUPPORT, SUPPORT_DEPT |
| `BizAction` enum | `com.bank.branch.platform.common.security.enums` | READ, LIST, WRITE, DELETE, EXPORT, TRANSFER |
| `DataScopeContext` | `com.bank.branch.platform.common.security.context` | ThreadLocal holder, field `.scope` |
| `StartProcessCmd` | `com.bank.branch.platform.workflow.api.dto` | Fields: bizType, bizId, businessKey, processDefinitionKey, startUser, startOrgId, title, variables |
| `WorkflowLaunchResp` | `com.bank.branch.platform.workflow.api.dto` | Fields: processInstanceId, businessKey, firstTaskId |
| `ProcessCompletedListener.ProcessCompletedEvent` | `com.bank.branch.platform.workflow.listener` | record(processInstanceId, businessKey) |

### Workflow Event Source

`ProcessCompletedListener.ProcessCompletedEvent` is a **record** (not a class) defined as inner type of `ProcessCompletedListener` in `com.bank.branch.platform.workflow.listener`. Access fields via `event.processInstanceId()` and `event.businessKey()`.

### BizType/BizAction Enums Already Exist

`LOAN`, `SUPPORT`, `SUPPORT_DEPT` are already defined in `BizType`. `READ`, `LIST`, `WRITE`, `DELETE`, `EXPORT`, `TRANSFER` are already in `BizAction`. DO NOT re-define them.

### UUID Generation

```java
UUID.randomUUID().toString().replace("-", "")
```

### High-Risk Operations

`@BizAuth` does NOT have a `highRisk` attribute. Use `@AuditLog(level=HIGH)` from `common-aop` separately.

---

## Phase 0: Module Scaffolding

### Task 0.1: Create Module Directory + POM + Register in Root/Bootstrap

**Files:**
- Create: `business-application-center/pom.xml`
- Modify: `pom.xml` (root) -- add `<module>business-application-center</module>` and `<dependency>` in `<dependencyManagement>`
- Modify: `bootstrap/pom.xml` -- add dependency on `business-application-center`

**Steps:**

- [ ] **Step 1:** Create `business-application-center/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.bank.branch.platform</groupId>
        <artifactId>branch-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>business-application-center</artifactId>
    <name>Business Application Center</name>
    <description>业务申请中心：资产投放申请 + 中场支持申请</description>

    <dependencies>
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Common 模块 -->
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

        <!-- auth-permission-center -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>auth-permission-center</artifactId>
        </dependency>

        <!-- workflow-center -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>workflow-center</artifactId>
        </dependency>

        <!-- system-governance-center -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>system-governance-center</artifactId>
        </dependency>

        <!-- customer-marketing-center (CustomerQueryApi, TouchTaskQueryApi, ProductApi) -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>customer-marketing-center</artifactId>
        </dependency>

        <!-- portal-content-center (AddressBookApi, ProductApi) -->
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>portal-content-center</artifactId>
        </dependency>

        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Redis + Session -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- MyBatis -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
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

- [ ] **Step 2:** Add module to root `pom.xml`

In `<modules>` section, add `<module>business-application-center</module>` after `customer-marketing-center`.

In `<dependencyManagement><dependencies>`, add:
```xml
<!-- business-application-center -->
<dependency>
    <groupId>com.bank.branch.platform</groupId>
    <artifactId>business-application-center</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 3:** Add dependency in `bootstrap/pom.xml`

In `<dependencies>` section (after customer-marketing-center):
```xml
<dependency>
    <groupId>com.bank.branch.platform</groupId>
    <artifactId>business-application-center</artifactId>
</dependency>
```

- [ ] **Step 4:** Create directory structure

```
business-application-center/
  src/main/java/com/bank/branch/platform/bizapp/
    api/
    api/dto/
    controller/
    config/
    dto/req/
    dto/resp/
    entity/
    enums/
    event/
    facade/
    listener/
    mapper/
    service/
  src/main/resources/mapper/bizapp/
  src/test/java/com/bank/branch/platform/bizapp/
    controller/
    service/
    listener/
    facade/
    support/
  src/test/resources/
```

- [ ] **Step 5:** Verify compilation

```bash
mvn clean compile -pl business-application-center -am -q
```

Expected: BUILD SUCCESS (empty module compiles)

---

### Task 0.2: Create Enums

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/enums/BizAppErrorCode.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/enums/LoanStatus.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/enums/SupportStatus.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/enums/SupportScenario.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/enums/SupportSourceType.java`

**Steps:**

- [ ] **Step 1:** Create `BizAppErrorCode.java`

```java
package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务申请中心错误码枚举。
 * 格式：BIZ-{HTTP状态码后两位}{序号}
 */
@Getter
@AllArgsConstructor
public enum BizAppErrorCode {

    // 403 权限错误
    CUSTOMER_NOT_VALID("BIZ-40301", "客户非有效公司客户"),
    NOT_TOUCH_TASK_ASSIGNEE("BIZ-40302", "非触达任务执行人"),
    CUSTOMER_NOT_CLAIMED_BY_ORG("BIZ-40303", "客户未被本机构认领"),
    NOT_SUPPORT_DEPT_MEMBER("BIZ-40304", "非承接部门人员/非当前承接人"),
    NOT_APPLY_CREATOR("BIZ-40305", "非申请创建人无权操作"),

    // 404 资源不存在
    APPLY_NOT_FOUND("BIZ-40401", "申请不存在"),

    // 409 冲突/业务规则
    PRODUCT_NOT_SUPPORT_AVAILABLE("BIZ-40901", "产品不支持中场支持"),
    SCENARIO_B_MISSING_DEPT("BIZ-40902", "场景B缺少supportDeptId"),
    EMPTY_PRODUCT_AND_DEMAND("BIZ-40903", "productIds和otherDemand都为空"),
    INVALID_DICT_VALUE("BIZ-40904", "字典值不合法"),
    EXPOSURE_EXCEEDS_CREDIT("BIZ-40905", "敞口金额大于授信金额"),
    DUPLICATE_SUPPORT_REQUEST("BIZ-40906", "同客户同产品重复申请"),
    PARALLEL_APPLY_OVER_LIMIT("BIZ-40907", "并行申请超限"),

    // 422 校验错误
    NODE_FORM_REQUIRED_MISSING("BIZ-42201", "节点表单必填字段缺失"),
    NODE_FORM_CONDITION_FAIL("BIZ-42202", "节点表单条件必填校验失败"),
    EXPORT_ROW_LIMIT_EXCEEDED("BIZ-42207", "导出行数超上限"),
    INVALID_STATUS_TRANSITION("BIZ-42301", "非法状态迁移"),
    NOT_DRAFT_STATUS("BIZ-42303", "申请非草稿状态不可编辑"),

    // 500 内部错误
    WORKFLOW_CALL_ERROR("BIZ-50001", "工作流调用异常"),
    EXPORT_GENERATE_ERROR("BIZ-50002", "导出文件生成失败");

    private final String code;
    private final String message;
}
```

- [ ] **Step 2:** Create `LoanStatus.java`

```java
package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资产投放申请状态枚举。
 * 对应 loan_apply.status 字段值。
 */
@Getter
@AllArgsConstructor
public enum LoanStatus {

    DRAFT("DRAFT", "草稿"),
    IN_APPROVAL("IN_APPROVAL", "审批中"),
    COMPLETED("COMPLETED", "已完成"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已撤回");

    private final String code;
    private final String description;
}
```

- [ ] **Step 3:** Create `SupportStatus.java`

```java
package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中场支持申请状态枚举。
 * 对应 support_request.status 字段值。
 */
@Getter
@AllArgsConstructor
public enum SupportStatus {

    DRAFT("DRAFT", "草稿"),
    IN_APPROVAL("IN_APPROVAL", "审批中"),
    IN_PROGRESS("IN_PROGRESS", "办理中"),
    COMPLETED("COMPLETED", "已完成"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已撤回");

    private final String code;
    private final String description;
}
```

- [ ] **Step 4:** Create `SupportScenario.java`

```java
package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中场支持场景枚举。
 * A=产品直达（有明确产品，无其他需求），B=部门承接（含模糊需求）。
 */
@Getter
@AllArgsConstructor
public enum SupportScenario {

    A("A", "产品直达", "support_simple_v1"),
    B("B", "部门承接", "support_complex_v1");

    private final String code;
    private final String description;
    private final String processDefinitionKey;
}
```

- [ ] **Step 5:** Create `SupportSourceType.java`

```java
package com.bank.branch.platform.bizapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 中场支持来源类型枚举。
 */
@Getter
@AllArgsConstructor
public enum SupportSourceType {

    MANUAL("MANUAL", "手动创建"),
    TOUCH_TASK("TOUCH_TASK", "触达任务转入");

    private final String code;
    private final String description;
}
```

- [ ] **Step 6:** Verify compilation

```bash
mvn clean compile -pl business-application-center -am -q
```

---

### Task 0.3: Create Entities

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/entity/LoanApply.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/entity/SupportRequest.java`

**Steps:**

- [ ] **Step 1:** Create `LoanApply.java`

```java
package com.bank.branch.platform.bizapp.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产投放申请实体，对应 loan_apply 表。
 * <p>
 * 状态机：DRAFT -> IN_APPROVAL -> COMPLETED/REJECTED/CANCELLED。
 * 业务键格式：LOAN:{id}。
 * </p>
 */
@Data
public class LoanApply {

    /** 申请ID（UUID，32位去连字符），对应 id */
    private String id;

    /** 申请编号（LA+yyyyMMdd+6位序号），对应 apply_no */
    private String applyNo;

    /** 客户ID，逻辑外键->cust_master.id，对应 cust_id */
    private String custId;

    /** 来源触达任务ID，逻辑外键->touch_task.id，对应 source_touch_task_id */
    private String sourceTouchTaskId;

    /** 项目类型（字典PROJECT_TYPE），对应 project_type */
    private String projectType;

    /** 业务类型（字典BIZ_TYPE），对应 biz_type */
    private String bizType;

    /** 担保方式（字典GUARANTEE_TYPE），对应 guarantee_type */
    private String guaranteeType;

    /** 授信金额（元，保留4位小数），对应 credit_amount */
    private BigDecimal creditAmount;

    /** 敞口金额（元，保留4位小数），对应 credit_exposure_amount */
    private BigDecimal creditExposureAmount;

    /** 状态：DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED，对应 status */
    private String status;

    /** 流程业务键，固定格式LOAN:{id}，对应 business_key */
    private String businessKey;

    /** 流程实例ID，对应 process_instance_id */
    private String processInstanceId;

    /** 归属机构（ORG_CODE），对应 owner_org_id */
    private String ownerOrgId;

    /** 创建人工号，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人工号，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 逻辑删除：0=未删，1=已删，对应 deleted */
    private Integer deleted;
}
```

- [ ] **Step 2:** Create `SupportRequest.java`

```java
package com.bank.branch.platform.bizapp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中场支持申请实体，对应 support_request 表。
 * <p>
 * 支持多产品拆单（同组共享 submit_group_id），双视图 SUPPORT/SUPPORT_DEPT。
 * 场景A（产品直达）：assigned_emp_id=产品负责人，dispatch_emp_id=NULL。
 * 场景B（部门承接）：先秘书派单，再 assigned_emp_id 办理。
 * 状态机：DRAFT -> IN_APPROVAL -> IN_PROGRESS(仅B) -> COMPLETED/REJECTED/CANCELLED。
 * 业务键格式：SUPPORT:{id}。
 * </p>
 */
@Data
public class SupportRequest {

    /** 申请ID（UUID，32位去连字符），对应 id */
    private String id;

    /** 申请编号（SR+yyyyMMdd+6位序号），对应 request_no */
    private String requestNo;

    /** 同批提交分组ID（多产品拆单时共享），对应 submit_group_id */
    private String submitGroupId;

    /** 客户ID，逻辑外键->cust_master.id，对应 cust_id */
    private String custId;

    /** 来源触达任务ID，对应 source_touch_task_id */
    private String sourceTouchTaskId;

    /** 产品ID，逻辑外键->product_info.id，对应 product_id */
    private String productId;

    /** 承接部门ORG_CODE，对应 support_dept_id */
    private String supportDeptId;

    /** 其他需求/补充说明，对应 other_demand */
    private String otherDemand;

    /** 派单人工号（部门秘书，仅场景B），对应 dispatch_emp_id */
    private String dispatchEmpId;

    /** 派单时间，对应 dispatch_time */
    private LocalDateTime dispatchTime;

    /** 承接办理人工号，对应 assigned_emp_id */
    private String assignedEmpId;

    /** 状态：DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED，对应 status */
    private String status;

    /** 流程业务键，固定格式SUPPORT:{id}，对应 business_key */
    private String businessKey;

    /** 流程实例ID，对应 process_instance_id */
    private String processInstanceId;

    /** 归属机构（发起侧ORG_CODE），对应 owner_org_id */
    private String ownerOrgId;

    /** 创建人工号（发起人），对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人工号，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 逻辑删除，对应 deleted */
    private Integer deleted;
}
```

- [ ] **Step 3:** Verify compilation

```bash
mvn clean compile -pl business-application-center -am -q
```

---

### Task 0.4: Create Mapper Interfaces + XML Files

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/mapper/LoanApplyMapper.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/mapper/SupportRequestMapper.java`
- Create: `business-application-center/src/main/resources/mapper/bizapp/LoanApplyMapper.xml`
- Create: `business-application-center/src/main/resources/mapper/bizapp/SupportRequestMapper.xml`

**Steps:**

- [ ] **Step 1:** Create `LoanApplyMapper.java`

```java
package com.bank.branch.platform.bizapp.mapper;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产投放申请 Mapper 接口，操作 loan_apply 表。
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 */
@Mapper
public interface LoanApplyMapper {

    /** 按 id 查询（含逻辑删除过滤） */
    LoanApply selectById(@Param("id") String id);

    /** 按 id 查询并加 FOR UPDATE 行锁 */
    LoanApply selectForUpdate(@Param("id") String id);

    /** 按 business_key 查询 */
    LoanApply selectByBusinessKey(@Param("businessKey") String businessKey);

    /** 分页查询 */
    List<LoanApply> selectPage(@Param("keyword") String keyword,
                               @Param("status") String status,
                               @Param("ownerOrgId") String ownerOrgId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 分页总数 */
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("ownerOrgId") String ownerOrgId);

    /** 插入 */
    int insert(LoanApply entity);

    /** 动态更新（仅更新非 null 字段） */
    int updateById(LoanApply entity);

    /** 更新状态 */
    int updateStatusById(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    /** 按客户ID查询历史 */
    List<LoanApply> selectByCustId(@Param("custId") String custId);

    /** 批量按ID查询 */
    List<LoanApply> selectByIds(@Param("ids") List<String> ids);

    /** 按机构+时间范围统计已完成数量 */
    long countCompletedByOrg(@Param("orgId") String orgId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /** 按创建人+时间范围汇总授信金额 */
    BigDecimal sumCreditAmountByEmp(@Param("empId") String empId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);
}
```

- [ ] **Step 2:** Create `SupportRequestMapper.java`

```java
package com.bank.branch.platform.bizapp.mapper;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 中场支持申请 Mapper 接口，操作 support_request 表。
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 */
@Mapper
public interface SupportRequestMapper {

    /** 按 id 查询 */
    SupportRequest selectById(@Param("id") String id);

    /** 按 id 查询并加 FOR UPDATE 行锁 */
    SupportRequest selectForUpdate(@Param("id") String id);

    /** 按 business_key 查询 */
    SupportRequest selectByBusinessKey(@Param("businessKey") String businessKey);

    /** 发起侧分页查询（SUPPORT 视图） */
    List<SupportRequest> selectPageForSupport(@Param("keyword") String keyword,
                                              @Param("status") String status,
                                              @Param("ownerOrgId") String ownerOrgId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /** 发起侧分页总数 */
    long countPageForSupport(@Param("keyword") String keyword,
                             @Param("status") String status,
                             @Param("ownerOrgId") String ownerOrgId);

    /** 承接侧分页查询（SUPPORT_DEPT 视图） */
    List<SupportRequest> selectPageForDept(@Param("supportDeptId") String supportDeptId,
                                           @Param("status") String status,
                                           @Param("assignedEmpId") String assignedEmpId,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 承接侧分页总数 */
    long countPageForDept(@Param("supportDeptId") String supportDeptId,
                          @Param("status") String status,
                          @Param("assignedEmpId") String assignedEmpId);

    /** 插入 */
    int insert(SupportRequest entity);

    /** 动态更新 */
    int updateById(SupportRequest entity);

    /** 更新状态 */
    int updateStatusById(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    /** 按客户ID查询历史 */
    List<SupportRequest> selectByCustId(@Param("custId") String custId);

    /** 按 submit_group_id 查询同组 */
    List<SupportRequest> selectBySubmitGroupId(@Param("submitGroupId") String submitGroupId);

    /** 批量按ID查询 */
    List<SupportRequest> selectByIds(@Param("ids") List<String> ids);

    /** 统计同客户同产品正在进行的申请数 */
    long countActiveByCustomerAndProduct(@Param("custId") String custId,
                                         @Param("productId") String productId);

    /** 按创建人+时间范围统计已完成数量 */
    long countCompletedByCreator(@Param("empId") String empId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /** 按承接人+时间范围统计已完成数量 */
    long countCompletedByAssignee(@Param("empId") String empId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /** 统计客户正在运行的支持申请数 */
    long countRunningByCustomer(@Param("custId") String custId);
}
```

- [ ] **Step 3:** Create `LoanApplyMapper.xml`

Follow the exact pattern from `CustLeadMapper.xml`: use `<sql id="BASE_COLUMNS">` fragment, dynamic `<if>` conditions, separate count queries. Column names MUST match DDL. Reference: `customer-marketing-center/src/main/resources/mapper/customer/CustLeadMapper.xml`.

Key SQL patterns:
- `selectForUpdate`: `SELECT ... FROM loan_apply WHERE id = #{id} AND deleted = 0 FOR UPDATE`
- `selectPage`: dynamic WHERE with `<if>` for keyword (search `apply_no`), status, ownerOrgId
- `updateById`: dynamic SET with `<if test="field != null">` for each nullable field
- `countCompletedByOrg`: `WHERE status = 'COMPLETED' AND owner_org_id = #{orgId} AND deleted = 0 AND updated_time BETWEEN #{start} AND #{end}`

- [ ] **Step 4:** Create `SupportRequestMapper.xml`

Same pattern. Note dual-view queries:
- `selectPageForSupport`: filters by `owner_org_id` (initiator view)
- `selectPageForDept`: filters by `support_dept_id` and optionally `assigned_emp_id` (receiver view)
- `countActiveByCustomerAndProduct`: `WHERE cust_id = #{custId} AND product_id = #{productId} AND status IN ('IN_APPROVAL', 'IN_PROGRESS') AND deleted = 0`

- [ ] **Step 5:** Verify compilation

```bash
mvn clean compile -pl business-application-center -am -q
```

---

### Task 0.5: Create Test Infrastructure

**Files:**
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/support/AbstractControllerIntegrationTest.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/support/WithMockEmpContext.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/support/MockEmpContextExtension.java`
- Create: `business-application-center/src/test/resources/application-test.yml`
- Create: `business-application-center/src/test/resources/schema.sql`

**Steps:**

- [ ] **Step 1:** Create `application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  session:
    store-type: none
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

mybatis:
  mapper-locations: classpath*:mapper/**/*Mapper.xml
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.bank.branch.platform.bizapp: DEBUG
```

- [ ] **Step 2:** Create `schema.sql` (H2-compatible DDL)

```sql
-- loan_apply (H2 MySQL compatibility mode)
CREATE TABLE IF NOT EXISTS loan_apply (
  id                     VARCHAR(32)    NOT NULL,
  apply_no               VARCHAR(100)   DEFAULT NULL,
  cust_id                VARCHAR(32)    NOT NULL,
  source_touch_task_id   VARCHAR(32)    DEFAULT NULL,
  project_type           VARCHAR(32)    DEFAULT NULL,
  biz_type               VARCHAR(32)    DEFAULT NULL,
  guarantee_type         VARCHAR(32)    DEFAULT NULL,
  credit_amount          DECIMAL(20,4)  DEFAULT NULL,
  credit_exposure_amount DECIMAL(20,4)  DEFAULT NULL,
  status                 VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
  business_key           VARCHAR(100)   DEFAULT NULL,
  process_instance_id    VARCHAR(64)    DEFAULT NULL,
  owner_org_id           VARCHAR(50)    NOT NULL,
  created_by             VARCHAR(32)    NOT NULL,
  created_time           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
  updated_by             VARCHAR(32)    DEFAULT NULL,
  updated_time           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
  deleted                INT            NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- support_request (H2 MySQL compatibility mode)
CREATE TABLE IF NOT EXISTS support_request (
  id                     VARCHAR(32)    NOT NULL,
  request_no             VARCHAR(100)   DEFAULT NULL,
  submit_group_id        VARCHAR(64)    DEFAULT NULL,
  cust_id                VARCHAR(32)    NOT NULL,
  source_touch_task_id   VARCHAR(32)    DEFAULT NULL,
  product_id             VARCHAR(64)    DEFAULT NULL,
  support_dept_id        VARCHAR(50)    DEFAULT NULL,
  other_demand           CLOB           DEFAULT NULL,
  dispatch_emp_id        VARCHAR(32)    DEFAULT NULL,
  dispatch_time          TIMESTAMP      DEFAULT NULL,
  assigned_emp_id        VARCHAR(32)    DEFAULT NULL,
  status                 VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
  business_key           VARCHAR(100)   DEFAULT NULL,
  process_instance_id    VARCHAR(64)    DEFAULT NULL,
  owner_org_id           VARCHAR(50)    NOT NULL,
  created_by             VARCHAR(32)    NOT NULL,
  created_time           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
  updated_by             VARCHAR(32)    DEFAULT NULL,
  updated_time           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
  deleted                INT            NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
```

- [ ] **Step 3:** Create `AbstractControllerIntegrationTest.java`

Mirror `customer-marketing-center/src/test/java/.../support/AbstractControllerIntegrationTest.java` exactly, but:
- Change package to `com.bank.branch.platform.bizapp.support`
- Change `scanBasePackages` to `"com.bank.branch.platform.bizapp"`
- Add `@MockBean` for all cross-module APIs: `CurrentUserApi`, `BizScopeApi`, `OrgApi`, `DictApi`, `FileApi`, `NotifyApi`, `AuditApi`, `WorkflowApi`, `CustomerQueryApi`, `TouchTaskQueryApi`, `ProductApi`, `AddressBookApi`, `RedisTemplate`

```java
package com.bank.branch.platform.bizapp.support;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
// import ProductApi and AddressBookApi from portal-content-center
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@SpringBootTest(classes = AbstractControllerIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractControllerIntegrationTest {

    @SpringBootApplication(
            scanBasePackages = "com.bank.branch.platform.bizapp",
            exclude = {
                    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
                    org.flowable.spring.boot.ProcessEngineAutoConfiguration.class,
                    org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.app.AppEngineAutoConfiguration.class,
                    org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.idm.IdmEngineAutoConfiguration.class,
                    org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.cmmn.CmmnEngineAutoConfiguration.class,
                    org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.dmn.DmnEngineAutoConfiguration.class,
                    org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration.class,
                    org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration.class,
                    org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration.class,
                    org.flowable.spring.boot.FlowableSecurityAutoConfiguration.class,
                    org.flowable.spring.boot.EndpointAutoConfiguration.class,
                    org.flowable.spring.boot.RestApiAutoConfiguration.class
            }
    )
    static class TestApp {}

    // auth-permission-center
    @MockBean protected CurrentUserApi currentUserApi;
    @MockBean protected BizScopeApi bizScopeApi;
    @MockBean protected OrgApi orgApi;
    // system-governance-center
    @MockBean protected DictApi dictApi;
    @MockBean protected FileApi fileApi;
    @MockBean protected NotifyApi notifyApi;
    @MockBean protected AuditApi auditApi;
    // workflow-center
    @MockBean protected WorkflowApi workflowApi;
    // customer-marketing-center
    @MockBean protected CustomerQueryApi customerQueryApi;
    @MockBean protected TouchTaskQueryApi touchTaskQueryApi;
    // portal-content-center (verify actual import paths)
    // @MockBean protected ProductApi productApi;
    // @MockBean protected AddressBookApi addressBookApi;
    // Redis
    @MockBean(name = "redisTemplate")
    protected RedisTemplate<String, Object> redisTemplate;
}
```

**NOTE:** Sub-agent MUST verify the actual import paths for `ProductApi` and `AddressBookApi` by reading `portal-content-center` source before completing this file.

- [ ] **Step 4:** Create `WithMockEmpContext.java`

Mirror customer-marketing-center pattern exactly, package `com.bank.branch.platform.bizapp.support`.

```java
package com.bank.branch.platform.bizapp.support;

import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockEmpContextExtension.class)
public @interface WithMockEmpContext {
    String empId() default "E10001";
    String orgCode() default "ORG_SZ_001";
    String[] roleCodes() default {"R_RM"};
    String dataScope() default "ORG_SUBTREE";
    String[] orgSubtree() default {"ORG_SZ_001"};
    boolean systemAdmin() default false;
}
```

- [ ] **Step 5:** Create `MockEmpContextExtension.java`

Mirror customer-marketing-center pattern, change default BizType to `BizType.LOAN` (will be overridden per-test). Package `com.bank.branch.platform.bizapp.support`.

- [ ] **Step 6:** Verify test infrastructure compiles

```bash
mvn clean compile -pl business-application-center -am -q
mvn test-compile -pl business-application-center -am -q
```

---

## Phase 1: Loan Domain

### Task 1.1: BizStateMachine (Shared State Transition Logic)

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/BizStateMachine.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/BizStateMachineTest.java`

**Specification:**

`BizStateMachine` is a `@Service` that validates and executes state transitions for both Loan and Support domains. It is stateless -- no mapper dependencies.

**Methods:**

```java
/**
 * 校验 Loan 状态迁移是否合法。
 * @param currentStatus 当前状态
 * @param targetStatus 目标状态
 * @throws BizException BIZ-42301 if transition is invalid
 */
public void validateLoanTransition(String currentStatus, String targetStatus);

/**
 * 校验 Support 状态迁移是否合法。
 * @param currentStatus 当前状态
 * @param targetStatus 目标状态
 * @throws BizException BIZ-42301 if transition is invalid
 */
public void validateSupportTransition(String currentStatus, String targetStatus);
```

**Loan transitions (allowed):**
- DRAFT -> IN_APPROVAL (submit)
- IN_APPROVAL -> COMPLETED (workflow approved)
- IN_APPROVAL -> REJECTED (workflow rejected)
- IN_APPROVAL -> CANCELLED (initiator cancel)

**Support transitions (allowed):**
- DRAFT -> IN_APPROVAL (submit)
- IN_APPROVAL -> IN_PROGRESS (dispatch, scenario B only)
- IN_APPROVAL -> COMPLETED (complete, scenario A)
- IN_PROGRESS -> COMPLETED (complete, scenario B)
- IN_PROGRESS -> REJECTED (complete failed, scenario B)
- IN_APPROVAL -> CANCELLED (cancel)
- IN_PROGRESS -> CANCELLED (cancel)

**Implementation approach:** Use `Map<String, Set<String>>` to define allowed transitions. Throw `BizException(BizAppErrorCode.INVALID_STATUS_TRANSITION)` if transition not in map.

**Test cases (write FIRST):**
1. `validateLoanTransition_DRAFT_to_IN_APPROVAL_shouldPass`
2. `validateLoanTransition_IN_APPROVAL_to_COMPLETED_shouldPass`
3. `validateLoanTransition_IN_APPROVAL_to_REJECTED_shouldPass`
4. `validateLoanTransition_IN_APPROVAL_to_CANCELLED_shouldPass`
5. `validateLoanTransition_DRAFT_to_COMPLETED_shouldThrow`
6. `validateLoanTransition_COMPLETED_to_any_shouldThrow`
7. `validateSupportTransition_IN_APPROVAL_to_IN_PROGRESS_shouldPass`
8. `validateSupportTransition_IN_APPROVAL_to_COMPLETED_shouldPass`
9. `validateSupportTransition_IN_PROGRESS_to_COMPLETED_shouldPass`
10. `validateSupportTransition_IN_PROGRESS_to_REJECTED_shouldPass`
11. `validateSupportTransition_DRAFT_to_COMPLETED_shouldThrow`

**TDD flow:**
- [ ] Write all test cases (RED -- they fail because BizStateMachine doesn't exist)
- [ ] Implement BizStateMachine (GREEN -- all tests pass)
- [ ] Refactor if needed
- [ ] Run: `mvn test -pl business-application-center -Dtest=BizStateMachineTest`

---

### Task 1.2: LoanService

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/LoanService.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/BizNoGenerator.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/LoanServiceTest.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/BizNoGeneratorTest.java`

**Specification:**

`BizNoGenerator` -- `@Service` generating unique business numbers:
- `generateLoanNo()` -> `"LA" + yyyyMMdd + 6-digit random`
- `generateSupportNo()` -> `"SR" + yyyyMMdd + 6-digit random`
- Uses `DateTimeFormatter.ofPattern("yyyyMMdd")` + 6-digit zero-padded random

`LoanService` -- `@Slf4j @Service @RequiredArgsConstructor`

**Dependencies (constructor-injected):**
- `LoanApplyMapper loanMapper`
- `BizStateMachine bizStateMachine`
- `BizNoGenerator bizNoGenerator`
- `WorkflowApi workflowApi`
- `CustomerQueryApi customerQueryApi`
- `TouchTaskQueryApi touchTaskQueryApi`
- `ApplicationEventPublisher eventPublisher`

**Methods and business logic pseudocode:**

```java
/** 创建草稿 */
@Transactional
public LoanApply createDraft(String custId, String sourceTouchTaskId,
        String projectType, String bizType, String guaranteeType,
        BigDecimal creditAmount, BigDecimal creditExposureAmount,
        String operatorEmpId, String orgCode) {
    // 1. Validate customer: customerQueryApi.isValidCustomer(custId) -> BIZ-40301
    // 2. Validate customer claimed: customerQueryApi.isClaimedByOrg(custId, orgCode) -> BIZ-40303
    // 3. If sourceTouchTaskId != null: validate touch task assignee == operatorEmpId -> BIZ-40302
    // 4. Validate exposure <= credit if both non-null -> BIZ-40905
    // 5. Generate UUID id, applyNo via bizNoGenerator
    // 6. Build entity, set status=DRAFT, deleted=0
    // 7. loanMapper.insert(entity)
    // 8. Return entity
}

/** 更新草稿 */
@Transactional
public LoanApply updateDraft(String id, ..fields.., String operatorEmpId) {
    // 1. selectById -> BIZ-40401 if null
    // 2. Validate status == DRAFT -> BIZ-42303
    // 3. Validate operatorEmpId == createdBy -> BIZ-40305
    // 4. Validate exposure <= credit if both non-null -> BIZ-40905
    // 5. Build partial update entity, updateById
    // 6. Return merged entity
}

/** 删除草稿（逻辑删除） */
@Transactional
public void deleteDraft(String id, String operatorEmpId) {
    // 1. selectById -> BIZ-40401
    // 2. Validate status == DRAFT -> BIZ-42303
    // 3. Validate operatorEmpId == createdBy -> BIZ-40305
    // 4. Set deleted=1, updateById
}

/** 提交审批 */
@Transactional
public void submitForApproval(String id, String operatorEmpId, String orgCode) {
    // 1. SELECT FOR UPDATE -> BIZ-40401
    // 2. bizStateMachine.validateLoanTransition(DRAFT, IN_APPROVAL) -> BIZ-42301
    // 3. Validate operatorEmpId == createdBy -> BIZ-40305
    // 4. Build businessKey = "LOAN:" + id
    // 5. Build StartProcessCmd (bizType="LOAN", processDefinitionKey="loan_corp_review_v1")
    // 6. workflowApi.startProcess(cmd) -> catch exception, wrap as BIZ-50001
    // 7. Update status=IN_APPROVAL, processInstanceId, businessKey
    // 8. eventPublisher.publishEvent(new LoanSubmittedEvent(...))
}

/** 撤回申请 */
@Transactional
public void cancelApply(String id, String operatorEmpId) {
    // 1. selectById -> BIZ-40401
    // 2. bizStateMachine.validateLoanTransition(current, CANCELLED)
    // 3. Validate operatorEmpId == createdBy -> BIZ-40305
    // 4. updateStatusById(id, CANCELLED, operatorEmpId)
}

/** 按ID查询 */
public LoanApply getById(String id) {
    // selectById -> BIZ-40401 if null
}

/** 分页查询 */
public PageResult<LoanApply> listPage(String keyword, String status,
        String ownerOrgId, int pageNo, int pageSize) {
    // offset = (pageNo - 1) * pageSize
    // records = loanMapper.selectPage(...)
    // total = loanMapper.countPage(...)
    // return PageResult.of(pageNo, pageSize, total, records)
}
```

**Test cases (write FIRST, all use `@ExtendWith(MockitoExtension.class)`):**

1. `createDraft_validInput_shouldInsertAndReturn`
2. `createDraft_invalidCustomer_shouldThrowBIZ40301`
3. `createDraft_customerNotClaimedByOrg_shouldThrowBIZ40303`
4. `createDraft_wrongTouchTaskAssignee_shouldThrowBIZ40302`
5. `createDraft_exposureExceedsCredit_shouldThrowBIZ40905`
6. `updateDraft_validDraft_shouldUpdate`
7. `updateDraft_notFound_shouldThrowBIZ40401`
8. `updateDraft_notDraftStatus_shouldThrowBIZ42303`
9. `updateDraft_notCreator_shouldThrowBIZ40305`
10. `deleteDraft_validDraft_shouldSoftDelete`
11. `deleteDraft_notDraftStatus_shouldThrowBIZ42303`
12. `submitForApproval_validDraft_shouldStartWorkflow`
13. `submitForApproval_workflowError_shouldThrowBIZ50001`
14. `cancelApply_validInApproval_shouldCancel`
15. `getById_exists_shouldReturn`
16. `getById_notExists_shouldThrowBIZ40401`
17. `listPage_shouldReturnPageResult`

**TDD flow:**
- [ ] Write BizNoGeneratorTest (2 tests: generateLoanNo format, generateSupportNo format)
- [ ] Implement BizNoGenerator
- [ ] Write all LoanServiceTest cases (RED)
- [ ] Implement LoanService (GREEN)
- [ ] Refactor
- [ ] Run: `mvn test -pl business-application-center -Dtest="LoanServiceTest,BizNoGeneratorTest"`

---

### Task 1.3: LoanController + Request/Response DTOs

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/dto/req/CreateLoanReq.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/dto/req/UpdateLoanReq.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/dto/resp/LoanDetailResp.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/controller/LoanController.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/controller/LoanControllerTest.java`

**Specification:**

`LoanController` -- `@Slf4j @RestController @RequiredArgsConstructor @RequestMapping("/api/loans") @Validated`

**9 endpoints:**

| Method | Path | Auth | Handler |
|--------|------|------|---------|
| GET | `/api/loans` | `@BizAuth(bizType=LOAN, action=LIST)` | `listPage(@RequestParam...)` |
| GET | `/api/loans/{id}` | `@BizAuth(bizType=LOAN, action=READ)` | `getById(@PathVariable)` |
| POST | `/api/loans` | `@BizAuth(bizType=LOAN, action=WRITE)` | `create(@RequestBody @Valid CreateLoanReq)` |
| PUT | `/api/loans/{id}` | `@BizAuth(bizType=LOAN, action=WRITE)` | `update(@PathVariable, @RequestBody @Valid UpdateLoanReq)` |
| POST | `/api/loans/{id}/submit` | `@BizAuth(bizType=LOAN, action=WRITE)` | `submit(@PathVariable)` |
| DELETE | `/api/loans/{id}` | `@BizAuth(bizType=LOAN, action=WRITE)` | `delete(@PathVariable)` |
| POST | `/api/loans/{id}/cancel` | `@BizAuth(bizType=LOAN, action=WRITE)` | `cancel(@PathVariable)` |
| GET | `/api/loans/export` | `@BizAuth(bizType=LOAN, action=EXPORT)` | `export()` -- returns 501 |
| GET | `/api/loans/{id}/node-form/{nodeKey}` | `@BizAuth(bizType=LOAN, action=READ)` | `getNodeForm(@PathVariable id, @PathVariable nodeKey)` |

**DTO structures:**

`CreateLoanReq`: custId(required), sourceTouchTaskId, projectType, bizType, guaranteeType, creditAmount, creditExposureAmount

`UpdateLoanReq`: projectType, bizType, guaranteeType, creditAmount, creditExposureAmount (all optional)

`LoanDetailResp`: all LoanApply fields (manual conversion from entity)

**Test cases (extend AbstractControllerIntegrationTest, @MockBean LoanService):**
1. `listPage_shouldReturn200`
2. `getById_shouldReturn200`
3. `create_shouldReturn200`
4. `update_shouldReturn200`
5. `submit_shouldReturn200`
6. `delete_shouldReturn200`
7. `cancel_shouldReturn200`
8. `export_shouldReturn501`
9. `getNodeForm_shouldReturn200`

**TDD flow:**
- [ ] Write DTOs
- [ ] Write LoanControllerTest (RED -- controller doesn't exist)
- [ ] Implement LoanController (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=LoanControllerTest`

---

### Task 1.4: LoanFormValidator

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/LoanFormValidator.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/LoanFormValidatorTest.java`

**Specification:**

V1 only hardcodes validation for `loan_corp_review` node. Generic Validator pattern for future extension.

```java
@Slf4j
@Service
public class LoanFormValidator {
    /**
     * 校验节点表单。
     * @param nodeKey 节点标识（如 "loan_corp_review"）
     * @param formData 表单数据 Map
     * @throws BizException BIZ-42201 if required fields missing, BIZ-42202 if condition validation fails
     */
    public void validate(String nodeKey, Map<String, Object> formData);
}
```

For `loan_corp_review` node:
- Required fields: `creditAmount`, `guaranteeType`
- Conditional: if `guaranteeType == "MORTGAGE"`, `collateralDesc` is required

**Test cases:**
1. `validate_loanCorpReview_allFieldsPresent_shouldPass`
2. `validate_loanCorpReview_missingCreditAmount_shouldThrowBIZ42201`
3. `validate_loanCorpReview_mortgageMissingCollateral_shouldThrowBIZ42202`
4. `validate_unknownNodeKey_shouldPassSilently`

**TDD flow:**
- [ ] Write tests (RED)
- [ ] Implement (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=LoanFormValidatorTest`

---

### Task 1.5: Loan Workflow Listeners

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/listener/LoanWorkflowListener.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/listener/LoanWorkflowListenerTest.java`

**Specification:**

Listens to `ProcessCompletedListener.ProcessCompletedEvent` from workflow-center. Filters by `businessKey` prefix `"LOAN:"`.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanWorkflowListener {

    private final LoanApplyMapper loanMapper;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onProcessCompleted(ProcessCompletedListener.ProcessCompletedEvent event) {
        // 1. Filter: if !businessKey.startsWith("LOAN:") return
        // 2. Extract loanId from businessKey
        // 3. selectById(loanId) -> if null, log warn and return
        // 4. Determine outcome:
        //    - V1 simplification: all completed = APPROVED -> status = COMPLETED
        //    - Publish LoanApprovedEvent
        // 5. Catch ALL exceptions, log error, do NOT propagate
    }
}
```

**Test cases:**
1. `onProcessCompleted_loanBusinessKey_shouldUpdateStatusToCompleted`
2. `onProcessCompleted_nonLoanBusinessKey_shouldIgnore`
3. `onProcessCompleted_loanNotFound_shouldLogWarnAndSkip`
4. `onProcessCompleted_shouldPublishLoanApprovedEvent`

**TDD flow:**
- [ ] Write tests (RED)
- [ ] Implement (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=LoanWorkflowListenerTest`

---

### Task 1.6: Loan Domain Events

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/event/LoanSubmittedEvent.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/event/LoanApprovedEvent.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/event/LoanRejectedEvent.java`

**Specification:**

All events use `@Data @AllArgsConstructor @NoArgsConstructor` pattern (following customer-marketing-center events).

```java
// LoanSubmittedEvent
@Data @AllArgsConstructor @NoArgsConstructor
public class LoanSubmittedEvent {
    private String loanId;
    private String applyNo;
    private String custId;
    private String ownerOrgId;
    private String operatorEmpId;
}

// LoanApprovedEvent
@Data @AllArgsConstructor @NoArgsConstructor
public class LoanApprovedEvent {
    private String loanId;
    private String applyNo;
    private String custId;
    private String ownerOrgId;
    private BigDecimal creditAmount;
}

// LoanRejectedEvent
@Data @AllArgsConstructor @NoArgsConstructor
public class LoanRejectedEvent {
    private String loanId;
    private String applyNo;
    private String custId;
    private String ownerOrgId;
}
```

**Steps:**
- [ ] Create all three event classes
- [ ] Verify compilation: `mvn clean compile -pl business-application-center -am -q`

---

## Phase 2: Support Domain

### Task 2.1: SupportScenarioRouter

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportScenarioRouter.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/SupportScenarioRouterTest.java`

**Specification:**

Stateless `@Service` that determines scenario A or B based on input.

```java
@Slf4j
@Service
public class SupportScenarioRouter {

    /**
     * 根据输入参数决定场景。
     * @param productIds 产品ID列表（可为 null 或空）
     * @param otherDemand 其他需求（可为 null 或空）
     * @param supportDeptId 承接部门ID（场景B必填）
     * @return SupportScenario.A 或 SupportScenario.B
     * @throws BizException BIZ-40903 if both empty, BIZ-40902 if B but no deptId
     */
    public SupportScenario route(List<String> productIds, String otherDemand, String supportDeptId);
}
```

**Routing logic:**
- If productIds is non-empty AND otherDemand is blank -> Scenario A
- If productIds is empty OR otherDemand is non-blank -> Scenario B, require supportDeptId non-blank
- If both productIds empty AND otherDemand empty -> throw BIZ-40903

**Test cases:**
1. `route_withProducts_noOtherDemand_shouldReturnA`
2. `route_noProducts_withOtherDemand_shouldReturnB`
3. `route_withProducts_withOtherDemand_shouldReturnB`
4. `route_noProducts_noOtherDemand_shouldThrowBIZ40903`
5. `route_scenarioB_noDeptId_shouldThrowBIZ40902`
6. `route_scenarioB_withDeptId_shouldReturnB`

**TDD flow:**
- [ ] Write tests (RED)
- [ ] Implement (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=SupportScenarioRouterTest`

---

### Task 2.2: SupportProductSplitService

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportProductSplitService.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/SupportProductSplitServiceTest.java`

**Specification:**

Handles multi-product split for Scenario A. For each productId, creates an independent SupportRequest.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportProductSplitService {

    private final SupportRequestMapper supportMapper;
    private final BizNoGenerator bizNoGenerator;
    private final ProductApi productApi;

    /**
     * 为每个产品创建独立的 SupportRequest 记录。
     * @param productIds 产品ID列表
     * @param custId 客户ID
     * @param sourceTouchTaskId 来源触达任务ID（可选）
     * @param operatorEmpId 操作人
     * @param orgCode 归属机构
     * @return 创建的 SupportRequest 列表
     * @throws BizException BIZ-40901 if product not support-available
     * @throws BizException BIZ-40906 if duplicate active request for same cust+product (>= 2)
     */
    public List<SupportRequest> splitByProducts(List<String> productIds, String custId,
            String sourceTouchTaskId, String operatorEmpId, String orgCode);
}
```

**Logic per product:**
1. Generate shared `submitGroupId` (UUID) for the batch
2. For each productId:
   a. Validate product exists and is support-available via ProductApi -> BIZ-40901
   b. Check `countActiveByCustomerAndProduct(custId, productId) >= 2` -> BIZ-40906
   c. Get assignedEmpId from `ProductApi.getProductResponsibleEmpIds(productId)` (first one)
   d. Generate UUID id, requestNo via bizNoGenerator
   e. Build entity: status=DRAFT, submitGroupId, productId, assignedEmpId
   f. Insert

**Test cases:**
1. `splitByProducts_singleProduct_shouldCreateOneRecord`
2. `splitByProducts_multipleProducts_shouldShareSubmitGroupId`
3. `splitByProducts_productNotAvailable_shouldThrowBIZ40901`
4. `splitByProducts_duplicateActiveRequest_shouldThrowBIZ40906`
5. `splitByProducts_shouldAssignProductResponsibleEmp`

**TDD flow:**
- [ ] Write tests (RED)
- [ ] Implement (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=SupportProductSplitServiceTest`

---

### Task 2.3: SupportService (Initiator View)

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportService.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/SupportServiceTest.java`

**Specification:**

`SupportService` -- `@Slf4j @Service @RequiredArgsConstructor`

**Dependencies:**
- `SupportRequestMapper supportMapper`
- `SupportScenarioRouter scenarioRouter`
- `SupportProductSplitService splitService`
- `BizStateMachine bizStateMachine`
- `BizNoGenerator bizNoGenerator`
- `WorkflowApi workflowApi`
- `CustomerQueryApi customerQueryApi`
- `ApplicationEventPublisher eventPublisher`

**Methods:**

```java
/**
 * 创建中场支持申请（含自动拆单逻辑）。
 * Scenario A: 调用 splitService.splitByProducts() 创建多条 DRAFT 记录
 * Scenario B: 创建单条 DRAFT 记录，supportDeptId 必填
 * @return 创建的记录列表（A 可能多条，B 固定一条）
 */
@Transactional
public List<SupportRequest> create(List<String> productIds, String custId,
        String sourceTouchTaskId, String otherDemand, String supportDeptId,
        String operatorEmpId, String orgCode);

/**
 * 提交草稿（对 create 返回的每条记录逐一提交流程）。
 * SELECT FOR UPDATE, validate DRAFT -> IN_APPROVAL, start workflow.
 */
@Transactional
public void submit(String id, String operatorEmpId, String orgCode);

/** 撤回申请 */
@Transactional
public void cancel(String id, String operatorEmpId);

/** 删除草稿 */
@Transactional
public void deleteDraft(String id, String operatorEmpId);

/** 按ID查询 */
public SupportRequest getById(String id);

/** 发起侧分页查询 */
public PageResult<SupportRequest> listPage(String keyword, String status,
        String ownerOrgId, int pageNo, int pageSize);
```

**Test cases (17+):**
1. `create_scenarioA_singleProduct_shouldDelegateSplit`
2. `create_scenarioA_multiProduct_shouldReturnMultipleRecords`
3. `create_scenarioB_withOtherDemand_shouldCreateSingleRecord`
4. `create_invalidCustomer_shouldThrowBIZ40301`
5. `submit_validDraft_shouldStartWorkflow`
6. `submit_notDraft_shouldThrow`
7. `submit_notCreator_shouldThrowBIZ40305`
8. `submit_scenarioA_shouldUseSimpleProcess`
9. `submit_scenarioB_shouldUseComplexProcess`
10. `cancel_inApproval_shouldCancel`
11. `cancel_inProgress_shouldCancel`
12. `deleteDraft_valid_shouldSoftDelete`
13. `deleteDraft_notDraft_shouldThrow`
14. `getById_exists_shouldReturn`
15. `getById_notFound_shouldThrow`
16. `listPage_shouldReturnPageResult`

**TDD flow:**
- [ ] Write tests (RED)
- [ ] Implement (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=SupportServiceTest`

---

### Task 2.4: SupportDeptService (Receiver View)

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportDeptService.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/SupportDeptServiceTest.java`

**Specification:**

`SupportDeptService` -- handles operations from the receiving department perspective (SUPPORT_DEPT BizType).

**Dependencies:**
- `SupportRequestMapper supportMapper`
- `BizStateMachine bizStateMachine`
- `AddressBookApi addressBookApi`
- `ApplicationEventPublisher eventPublisher`

**Methods:**

```java
/**
 * 秘书派单（仅场景B）。
 * IN_APPROVAL -> IN_PROGRESS, set dispatch_emp_id, dispatch_time, assigned_emp_id
 */
@Transactional
public void dispatch(String id, String assignedEmpId, String dispatcherEmpId);

/**
 * 转交承接人。
 * 已分配的任务转交给其他同部门人员。
 * @AuditLog(level=HIGH) -- high-risk operation
 */
@Transactional
public void transfer(String id, String newAssignedEmpId, String operatorEmpId);

/**
 * 办理完成。
 * IN_APPROVAL -> COMPLETED (A) or IN_PROGRESS -> COMPLETED/REJECTED (B)
 */
@Transactional
public void complete(String id, boolean success, String operatorEmpId);

/** 承接侧分页查询 */
public PageResult<SupportRequest> listPageForDept(String supportDeptId,
        String status, String assignedEmpId, int pageNo, int pageSize);
```

**Test cases:**
1. `dispatch_inApproval_shouldTransitionToInProgress`
2. `dispatch_notInApproval_shouldThrow`
3. `dispatch_shouldSetDispatchFields`
4. `transfer_validInProgress_shouldUpdateAssignee`
5. `transfer_notAssignedPerson_shouldThrowBIZ40304`
6. `complete_scenarioA_success_shouldComplete`
7. `complete_scenarioB_inProgress_success_shouldComplete`
8. `complete_scenarioB_inProgress_failed_shouldReject`
9. `complete_invalidStatus_shouldThrow`
10. `complete_shouldPublishSupportCompletedEvent`
11. `listPageForDept_shouldReturnPageResult`

**TDD flow:**
- [ ] Write tests (RED)
- [ ] Implement (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=SupportDeptServiceTest`

---

### Task 2.5: SupportController + SupportDeptController + DTOs

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/dto/req/CreateSupportReq.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/dto/req/DispatchReq.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/dto/req/TransferReq.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/dto/req/CompleteReq.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/dto/resp/SupportDetailResp.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/controller/SupportController.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/controller/SupportDeptController.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/controller/SupportControllerTest.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/controller/SupportDeptControllerTest.java`

**SupportController** -- `@RequestMapping("/api/support-requests")` -- 8 endpoints:

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/support-requests` | SUPPORT/LIST |
| GET | `/api/support-requests/{id}` | SUPPORT/READ |
| POST | `/api/support-requests` | SUPPORT/WRITE |
| POST | `/api/support-requests/{id}/submit` | SUPPORT/WRITE |
| DELETE | `/api/support-requests/{id}` | SUPPORT/WRITE |
| POST | `/api/support-requests/{id}/cancel` | SUPPORT/WRITE |
| GET | `/api/support-requests/export` | SUPPORT/EXPORT |
| GET | `/api/support-requests/available-products` | SUPPORT/READ |

**SupportDeptController** -- `@RequestMapping("/api/support-dept/requests")` -- 4 endpoints:

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/support-dept/requests` | SUPPORT_DEPT/LIST |
| POST | `/api/support-dept/requests/{id}/dispatch` | SUPPORT_DEPT/WRITE |
| POST | `/api/support-dept/requests/{id}/transfer` | SUPPORT_DEPT/TRANSFER |
| POST | `/api/support-dept/requests/{id}/complete` | SUPPORT_DEPT/WRITE |

**DTO structures:**

`CreateSupportReq`: custId(required), productIds(List<String>), sourceTouchTaskId, otherDemand, supportDeptId

`DispatchReq`: assignedEmpId(required)

`TransferReq`: newAssignedEmpId(required)

`CompleteReq`: success(boolean, required)

**Test cases for SupportControllerTest (8 tests, one per endpoint):**
1-8: Standard 200 response tests for each endpoint, export returns 501

**Test cases for SupportDeptControllerTest (4 tests):**
1-4: Standard 200 response tests for dispatch, transfer, complete, listPage

**TDD flow:**
- [ ] Create all DTOs
- [ ] Write SupportControllerTest (RED)
- [ ] Implement SupportController (GREEN)
- [ ] Write SupportDeptControllerTest (RED)
- [ ] Implement SupportDeptController (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest="SupportControllerTest,SupportDeptControllerTest"`

---

### Task 2.6: Support Workflow Listeners

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/listener/SupportWorkflowListener.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/listener/SupportWorkflowListenerTest.java`

**Specification:**

Same pattern as LoanWorkflowListener, filters by `"SUPPORT:"` prefix.

On process completed:
- Scenario A (no dispatchEmpId): IN_APPROVAL -> COMPLETED
- Scenario B: depends on workflow outcome (V1 simplification: completed = success)

Must catch ALL exceptions. Uses `@EventListener` (not `@TransactionalEventListener` since it listens to workflow events).

**Test cases:**
1. `onProcessCompleted_supportBusinessKey_shouldUpdateStatus`
2. `onProcessCompleted_nonSupportBusinessKey_shouldIgnore`
3. `onProcessCompleted_requestNotFound_shouldLogAndSkip`
4. `onProcessCompleted_shouldPublishSupportCompletedEvent`
5. `onProcessCompleted_exceptionInHandler_shouldNotPropagate`

**TDD flow:**
- [ ] Write tests (RED)
- [ ] Implement (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=SupportWorkflowListenerTest`

---

### Task 2.7: Support Domain Events

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/event/SupportSubmittedEvent.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/event/SupportDispatchedEvent.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/event/SupportCompletedEvent.java`

**Specification:**

```java
// SupportSubmittedEvent
@Data @AllArgsConstructor @NoArgsConstructor
public class SupportSubmittedEvent {
    private String requestId;
    private String requestNo;
    private String custId;
    private String productId;
    private String ownerOrgId;
    private String operatorEmpId;
}

// SupportDispatchedEvent
@Data @AllArgsConstructor @NoArgsConstructor
public class SupportDispatchedEvent {
    private String requestId;
    private String requestNo;
    private String assignedEmpId;
    private String dispatcherEmpId;
    private String supportDeptId;
}

// SupportCompletedEvent
@Data @AllArgsConstructor @NoArgsConstructor
public class SupportCompletedEvent {
    private String requestId;
    private String requestNo;
    private String custId;
    private String productId;
    private String assignedEmpId;
    private boolean success;
}
```

**Steps:**
- [ ] Create all three event classes
- [ ] Verify compilation: `mvn clean compile -pl business-application-center -am -q`

---

## Phase 3: External APIs + Integration

### Task 3.1: API Interfaces + DTOs

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/LoanApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/LoanQueryApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/SupportApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/SupportQueryApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/BizApplyQueryApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/LoanApplyDTO.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/SupportRequestDTO.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/LoanQueryConditionDTO.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/SupportQueryConditionDTO.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/RunningAppCountDTO.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/dto/BizApplyStatDTO.java`

**Specification:**

Interfaces MUST match the signatures in spec section 7 exactly:

```java
// LoanApi
public interface LoanApi {
    Optional<LoanApplyDTO> getLoanApply(String applyId);
    Optional<LoanApplyDTO> getLoanApplyByBusinessKey(String businessKey);
    List<LoanApplyDTO> getCustomerLoanHistory(String custId);
    List<LoanApplyDTO> getLoanApplyBatch(List<String> applyIds);
}

// LoanQueryApi
public interface LoanQueryApi {
    PageResult<LoanApplyDTO> pageQuery(LoanQueryConditionDTO condition);
    long countCompletedByOrg(String orgId, LocalDateTime start, LocalDateTime end);
    BigDecimal sumCreditAmountByEmp(String empId, LocalDateTime start, LocalDateTime end);
}

// SupportApi
public interface SupportApi {
    Optional<SupportRequestDTO> getSupportRequest(String requestId);
    Optional<SupportRequestDTO> getSupportRequestByBusinessKey(String businessKey);
    List<SupportRequestDTO> getCustomerSupportHistory(String custId);
    List<SupportRequestDTO> getBySubmitGroup(String submitGroupId);
    List<SupportRequestDTO> getSupportRequestBatch(List<String> requestIds);
}

// SupportQueryApi
public interface SupportQueryApi {
    PageResult<SupportRequestDTO> pageQuery(SupportQueryConditionDTO condition);
    long countCompletedByCreator(String empId, LocalDateTime start, LocalDateTime end);
    long countCompletedByAssignee(String empId, LocalDateTime start, LocalDateTime end);
}

// BizApplyQueryApi
public interface BizApplyQueryApi {
    RunningAppCountDTO countRunningApplications(String custId);
    boolean hasRunningLoan(String custId);
    boolean hasRunningSupport(String custId);
    BizApplyStatDTO getEmpStatistics(String empId);
    BizApplyStatDTO getEmpStatisticsByPeriod(String empId, LocalDateTime start, LocalDateTime end);
}
```

**DTO structures:**

`LoanApplyDTO`: mirrors all LoanApply entity fields

`SupportRequestDTO`: mirrors all SupportRequest entity fields

`LoanQueryConditionDTO`: keyword, status, ownerOrgId, pageNo, pageSize

`SupportQueryConditionDTO`: keyword, status, ownerOrgId, supportDeptId, pageNo, pageSize

`RunningAppCountDTO`: runningLoanCount(long), runningSupportCount(long)

`BizApplyStatDTO`: totalLoans(long), completedLoans(long), totalSupports(long), completedSupports(long), totalCreditAmount(BigDecimal)

**Steps:**
- [ ] Create all DTOs
- [ ] Create all 5 API interfaces
- [ ] Verify compilation: `mvn clean compile -pl business-application-center -am -q`

---

### Task 3.2: Facade Implementations

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/facade/LoanApiImpl.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/facade/LoanQueryApiImpl.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/facade/SupportApiImpl.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/facade/SupportQueryApiImpl.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/facade/BizApplyQueryApiImpl.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/facade/LoanApiImplTest.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/facade/SupportApiImplTest.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/facade/BizApplyQueryApiImplTest.java`

**Specification:**

Each facade is `@Slf4j @Service @RequiredArgsConstructor` and implements its corresponding API interface. Injects mapper directly (facade is in same module).

Key implementation details:
- Entity-to-DTO conversion: manual inline, create private helper `toDTO(entity)` method
- `LoanQueryApiImpl.pageQuery()`: delegate to mapper with condition DTO fields
- `BizApplyQueryApiImpl`: aggregates counts from both LoanApplyMapper and SupportRequestMapper

**Test cases for LoanApiImplTest:**
1. `getLoanApply_exists_shouldReturnPresent`
2. `getLoanApply_notExists_shouldReturnEmpty`
3. `getCustomerLoanHistory_shouldReturnList`
4. `getLoanApplyBatch_shouldReturnList`

**Test cases for SupportApiImplTest:**
1. `getSupportRequest_exists_shouldReturnPresent`
2. `getBySubmitGroup_shouldReturnGroupedList`

**Test cases for BizApplyQueryApiImplTest:**
1. `countRunningApplications_shouldAggregateBothDomains`
2. `hasRunningLoan_withActive_shouldReturnTrue`
3. `hasRunningLoan_noActive_shouldReturnFalse`
4. `getEmpStatistics_shouldAggregate`

**TDD flow:**
- [ ] Write all facade tests (RED)
- [ ] Implement all 5 facades (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest="LoanApiImplTest,SupportApiImplTest,BizApplyQueryApiImplTest"`

---

### Task 3.3: BizApplySearchService

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/BizApplySearchService.java`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/BizApplySearchServiceTest.java`

**Specification:**

Cross-domain query aggregation service. Used by `BizApplyQueryApiImpl` for complex aggregations.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class BizApplySearchService {

    private final LoanApplyMapper loanMapper;
    private final SupportRequestMapper supportMapper;

    /** 统计员工创建的所有申请 */
    public BizApplyStatDTO getEmpStatistics(String empId);

    /** 统计员工在指定时间段内创建的申请 */
    public BizApplyStatDTO getEmpStatisticsByPeriod(String empId,
            LocalDateTime start, LocalDateTime end);
}
```

**Test cases:**
1. `getEmpStatistics_shouldAggregateFromBothDomains`
2. `getEmpStatisticsByPeriod_shouldFilterByDateRange`

**TDD flow:**
- [ ] Write tests (RED)
- [ ] Implement (GREEN)
- [ ] Run: `mvn test -pl business-application-center -Dtest=BizApplySearchServiceTest`

---

### Task 3.4: BizAppCacheConfig + BizNoGenerator Enhancements

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/config/BizAppCacheConfig.java`

**Specification:**

```java
package com.bank.branch.platform.bizapp.config;

/**
 * 业务申请中心缓存配置。
 * Cache-Aside 模式，所有 Key 前缀 bizapp:，默认 TTL 5 分钟 + 10% 随机抖动防雪崩。
 */
public class BizAppCacheConfig {
    public static final String KEY_PREFIX = "bizapp:";
    public static final long DEFAULT_TTL_SECONDS = 300;
    public static final double JITTER_RATIO = 0.1;
}
```

**Steps:**
- [ ] Create BizAppCacheConfig
- [ ] Verify compilation: `mvn clean compile -pl business-application-center -am -q`

---

## Phase 4: Final Integration

### Task 4.1: PT_RESOURCE SQL Seed Files

**Files:**
- Create: `docs/superpowers/sql/2026-04-14-bizapp-pt-resource.sql`

**Specification:**

Register all 21 endpoints in `PT_RESOURCE` table. RESOURCE_ID max 20 chars, naming convention `B_*` (for biz).

Format per row:
```sql
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_NAME, URL_PATTERN, METHOD, BIZ_TYPE, BIZ_ACTION, REMARK)
VALUES ('B_LOAN_LIST', '资产投放列表', '/api/loans', 'GET', 'LOAN', 'LIST', '资产投放分页列表');
```

**Endpoint-to-RESOURCE_ID mapping:**

| Endpoint | RESOURCE_ID | BIZ_TYPE | BIZ_ACTION |
|----------|------------|----------|------------|
| GET /api/loans | B_LOAN_LIST | LOAN | LIST |
| GET /api/loans/* | B_LOAN_READ | LOAN | READ |
| POST /api/loans | B_LOAN_CREATE | LOAN | WRITE |
| PUT /api/loans/* | B_LOAN_UPDATE | LOAN | WRITE |
| POST /api/loans/*/submit | B_LOAN_SUBMIT | LOAN | WRITE |
| DELETE /api/loans/* | B_LOAN_DELETE | LOAN | WRITE |
| POST /api/loans/*/cancel | B_LOAN_CANCEL | LOAN | WRITE |
| GET /api/loans/export | B_LOAN_EXPORT | LOAN | EXPORT |
| GET /api/loans/*/node-form/* | B_LOAN_FORM | LOAN | READ |
| GET /api/support-requests | B_SUP_LIST | SUPPORT | LIST |
| GET /api/support-requests/* | B_SUP_READ | SUPPORT | READ |
| POST /api/support-requests | B_SUP_CREATE | SUPPORT | WRITE |
| POST /api/support-requests/*/submit | B_SUP_SUBMIT | SUPPORT | WRITE |
| DELETE /api/support-requests/* | B_SUP_DELETE | SUPPORT | WRITE |
| POST /api/support-requests/*/cancel | B_SUP_CANCEL | SUPPORT | WRITE |
| GET /api/support-requests/export | B_SUP_EXPORT | SUPPORT | EXPORT |
| GET /api/support-requests/available-products | B_SUP_PROD | SUPPORT | READ |
| GET /api/support-dept/requests | B_SUPD_LIST | SUPPORT_DEPT | LIST |
| POST /api/support-dept/requests/*/dispatch | B_SUPD_DISP | SUPPORT_DEPT | WRITE |
| POST /api/support-dept/requests/*/transfer | B_SUPD_XFER | SUPPORT_DEPT | TRANSFER |
| POST /api/support-dept/requests/*/complete | B_SUPD_DONE | SUPPORT_DEPT | WRITE |

**Steps:**
- [ ] Create SQL file with 21 INSERT statements
- [ ] Include role-resource bindings for R_ADMIN and R_RM roles

---

### Task 4.2: Module CLAUDE.md

**Files:**
- Create: `business-application-center/CLAUDE.md`

**Steps:**
- [ ] Write module CLAUDE.md following the exact structure of `customer-marketing-center/CLAUDE.md`
- [ ] Include: module overview, package structure, REST endpoints table, API interfaces table, entity table, events table, error codes table, cross-module dependencies table, cache config, test infrastructure

---

### Task 4.3: Full Compilation + Test Run

**Steps:**

- [ ] **Step 1:** Full compilation check

```bash
mvn clean compile -pl bootstrap -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2:** Run all module tests

```bash
mvn test -pl business-application-center
```

Expected: All tests pass (target: 80+ test cases across all phases)

- [ ] **Step 3:** Fix any compilation or test failures

---

### Task 4.4: Git Commit

**Steps:**

- [ ] Stage all new/modified files
- [ ] Commit with message:

```
feat(bizapp): implement business-application-center module - 2 domains, 21 endpoints, 5 APIs

- Loan domain: 9 REST endpoints, full CRUD + workflow integration
- Support domain: 12 REST endpoints, dual-view (SUPPORT/SUPPORT_DEPT)
- Multi-product split (Scenario A) + department routing (Scenario B)
- BizStateMachine for shared state transition validation
- 6 domain events + workflow callback listeners
- 5 external API interfaces with facade implementations
- Full TDD coverage: ~80+ test cases
```

---

## Dependency Graph Between Tasks

```
Phase 0 (serial):
  0.1 -> 0.2 -> 0.3 -> 0.4 -> 0.5

Phase 1 (after 0.5, can parallel with Phase 2):
  1.1 (BizStateMachine)
    -> 1.2 (LoanService, depends on 1.1)
      -> 1.3 (LoanController, depends on 1.2)
  1.4 (LoanFormValidator, independent after 0.5)
  1.5 (LoanWorkflowListener, depends on 1.2 for mapper)
  1.6 (LoanEvents, independent after 0.5)

Phase 2 (after 0.5 + 1.1, can parallel with Phase 1.2+):
  2.1 (SupportScenarioRouter, depends on 0.5)
  2.2 (SupportProductSplitService, depends on 0.4)
    -> 2.3 (SupportService, depends on 2.1 + 2.2 + 1.1)
      -> 2.5 (Controllers, depends on 2.3 + 2.4)
  2.4 (SupportDeptService, depends on 1.1)
  2.6 (SupportWorkflowListener, depends on 0.4)
  2.7 (SupportEvents, independent after 0.5)

Phase 3 (after Phase 1 + Phase 2):
  3.1 (API Interfaces, independent after 0.3)
  3.2 (Facades, depends on 3.1 + mappers)
  3.3 (BizApplySearchService, depends on mappers)
  3.4 (CacheConfig, independent)

Phase 4 (after all above):
  4.1 -> 4.2 -> 4.3 -> 4.4
```

**Recommended parallel dispatch:**
- Sub-agent A: Phase 0 (serial) -> Task 1.1 -> Task 1.6 -> Task 2.7
- Sub-agent B (after 0.5): Tasks 1.2 -> 1.3 -> 1.4 -> 1.5
- Sub-agent C (after 0.5 + 1.1): Tasks 2.1 -> 2.2 -> 2.3 -> 2.5 -> 2.6
- Sub-agent D (after 1.1): Task 2.4
- Sub-agent E (after Phase 1+2): Tasks 3.1 -> 3.2 -> 3.3 -> 3.4
- Main agent: Phase 4

---

## Expected Final File Count

| Category | Count |
|----------|-------|
| Entity classes | 2 |
| Enum classes | 5 |
| Mapper interfaces | 2 |
| Mapper XML files | 2 |
| Service classes | 8 (LoanService, SupportService, SupportDeptService, BizStateMachine, SupportScenarioRouter, SupportProductSplitService, LoanFormValidator, BizNoGenerator) |
| Facade classes | 5 |
| Controller classes | 3 (Loan, Support, SupportDept) |
| API interfaces | 5 |
| API DTOs | 6 |
| Request DTOs | 4 |
| Response DTOs | 2 |
| Event classes | 6 |
| Listener classes | 2 |
| Config classes | 1 |
| Search service | 1 |
| Test support classes | 3 (AbstractControllerIntegrationTest, WithMockEmpContext, MockEmpContextExtension) |
| Service test classes | ~10 |
| Controller test classes | 3 |
| Listener test classes | 2 |
| Facade test classes | 3 |
| Config files | 2 (application-test.yml, schema.sql) |
| SQL seed file | 1 |
| Module CLAUDE.md | 1 |
| **Total files** | **~71** |
| **Expected test cases** | **~90+** |
