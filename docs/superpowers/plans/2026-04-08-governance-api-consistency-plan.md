# Governance 模块接口一致性修复实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 A、B 模块中不符合文档定义的接口从 `@RequestParam` 改为 `@RequestBody`，保持与 `03-接口设计与报文.md` 文档一致。

**Architecture:** Controller 层接收 JSON 请求体（DTO），操作工号从 `DataScopeContext.current().getEmpId()` 获取，不从前端传入。

**Tech Stack:** Spring Boot 3.2.3, Java 17, MyBatis

---

## 任务概览

| 任务 | 接口 | 状态 |
|------|------|------|
| Task 1 | A.6 启用/禁用字典项 | 待实现 |
| Task 2 | B.3 年初初始化 | 待实现 |
| Task 3 | B.4 批量导入节假日 | 不需修改（文件上传） |

> H.1 (SQL探查) 已在会话中初步实现，本计划不包含。

---

## Task 1: A.6 启用/禁用字典项改为 @RequestBody

**Files:**
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/DictStatusReqDTO.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/DictController.java:137-146`

- [ ] **Step 1: 创建 DictStatusReqDTO**

```java
package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 字典状态更新请求DTO（A.6）
 */
@Data
public class DictStatusReqDTO {

    /** 目标状态（ACTIVE/DISABLED） */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ACTIVE|DISABLED", message = "状态值必须是 ACTIVE 或 DISABLED")
    private String status;
}
```

- [ ] **Step 2: 修改 DictController.updateDictStatus 方法**

将 `DictController.java` 第 137-146 行：

```java
@PutMapping("/api/admin/sys/dicts/{id}/status")
@Operation(summary = "启用/禁用字典项")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<SysDict> updateDictStatus(
        @PathVariable(value = "id") String id,
        @RequestParam(value = "status") String status) {
    log.info("[DictController.updateDictStatus] id={}, status={}", id, status);
    SysDict dict = dictService.updateStatus(id, status);
    return ResponseWrapper.success(dict);
}
```

改为：

```java
@PutMapping("/api/admin/sys/dicts/{id}/status")
@Operation(summary = "启用/禁用字典项")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<SysDict> updateDictStatus(
        @PathVariable(value = "id") String id,
        @Valid @RequestBody DictStatusReqDTO req) {
    log.info("[DictController.updateDictStatus] id={}, status={}", id, req.getStatus());
    SysDict dict = dictService.updateStatus(id, req.getStatus());
    return ResponseWrapper.success(dict);
}
```

需要添加 import：
```java
import com.bank.branch.platform.governance.api.dto.DictStatusReqDTO;
```

- [ ] **Step 3: 运行编译验证**

```bash
cd system-governance-center && mvn compile -q
```

预期：编译成功，无错误

- [ ] **Step 4: 提交代码**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/DictStatusReqDTO.java
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/DictController.java
git commit -m "fix(governance): A.6 启用/禁用字典项改为 @RequestBody"
```

---

## Task 2: B.3 年初初始化改为 @RequestBody

**Files:**
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/CalendarInitReqDTO.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/CalendarController.java:83-91`

- [ ] **Step 1: 创建 CalendarInitReqDTO**

```java
package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 日历初始化请求DTO（B.3）
 */
@Data
public class CalendarInitReqDTO {

    /** 年份 */
    @NotNull(message = "年份不能为空")
    @Min(value = 2026, message = "年份不能早于2026")
    @Max(value = 2100, message = "年份不能超过2100")
    private Integer year;
}
```

- [ ] **Step 2: 修改 CalendarController.initYear 方法**

将 `CalendarController.java` 第 83-91 行：

```java
@PostMapping("/init")
@Operation(summary = "初始化年份日历数据")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<Void> initYear(
        @RequestParam(value = "year") int year) {
    log.info("[CalendarController.initYear] year={}", year);
    calendarService.initYear(year);
    return ResponseWrapper.success();
}
```

改为：

```java
@PostMapping("/init")
@Operation(summary = "初始化年份日历数据")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<Void> initYear(@Valid @RequestBody CalendarInitReqDTO req) {
    log.info("[CalendarController.initYear] year={}", req.getYear());
    calendarService.initYear(req.getYear());
    return ResponseWrapper.success();
}
```

需要修改 import（移除 `@RequestParam`，添加 `CalendarInitReqDTO`）：
```java
import com.bank.branch.platform.governance.api.dto.CalendarInitReqDTO;
```

- [ ] **Step 3: 运行编译验证**

```bash
cd system-governance-center && mvn compile -q
```

预期：编译成功，无错误

- [ ] **Step 4: 提交代码**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/CalendarInitReqDTO.java
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/CalendarController.java
git commit -m "fix(governance): B.3 年初初始化改为 @RequestBody"
```

---

## Task 3: B.4 批量导入节假日 - 无需修改

B.4 接口 `POST /api/admin/sys/calendar/import` 使用 `@RequestParam` 接收 `MultipartFile` 文件上传，这是 Spring MVC 处理文件上传的标准方式，与文档定义一致（文档中也说明 "Content-Type: multipart/form-data"），无需修改。

---

## 验收确认

完成所有任务后，确认：

1. ✅ A.6 接口使用 `@RequestBody DictStatusReqDTO` 接收请求
2. ✅ B.3 接口使用 `@RequestBody CalendarInitReqDTO` 接收请求
3. ✅ B.4 接口保持 `MultipartFile` 文件上传方式
4. ✅ 编译通过
5. ✅ 代码已提交
