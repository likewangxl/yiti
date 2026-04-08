# Governance 接口一致性修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 governance 模块 Controller 实现与 03-接口设计与报文.md 文档对齐，修复路径、参数、RequestBody 等差异

**Architecture:** 按批次执行修复，第一批为高优先级（审计日志路径、工作日历核心接口），第二批为文件管理，第三批为低优先级修复

**Tech Stack:** Spring Boot 3.2.3, MyBatis, MinIO

---

## 第一批：高优先级修复

### Task 1: 修复审计日志路径（添加 sys/ 前缀）

**Files:**
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AuditLogController.java:32`

- [ ] **Step 1: 修改 AuditLogController 路径**

```java
// 修改前
@RequestMapping("/api/admin/audit-logs")

// 修改后
@RequestMapping("/api/admin/sys/audit-logs")
```

- [ ] **Step 2: 运行单元测试验证**

Run: `cd system-governance-center && mvn test -Dtest=AuditLogControllerTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AuditLogController.java
git commit -m "fix(gov): 审计日志路径添加 sys/ 前缀"
```

---

### Task 2: 新增工作日历 B.1 公共按月查询接口

**Files:**
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/CalendarController.java`
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/CalendarDayRespDTO.java`

- [ ] **Step 1: 创建 CalendarDayRespDTO**

```java
package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 日历天响应DTO（B.1按月查询返回）
 */
@Data
public class CalendarDayRespDTO {
    /** 日期（yyyy-MM-dd 格式） */
    private String day;
    /** 是否工作日 */
    private Boolean isWorkday;
    /** 星期几（1=周一 ... 7=周日） */
    private Integer dayOfWeek;
    /** 备注 */
    private String remark;
}
```

- [ ] **Step 2: 在 CalendarService 中新增按月查询方法**

```java
/**
 * 获取指定年月的日历数据
 * @param year 年份
 * @param month 月份（1-12）
 * @return 日历天DTO列表
 */
public List<CalendarDayDTO> getDaysByMonth(int year, int month) {
    LocalDate start = LocalDate.of(year, month, 1);
    LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
    List<SysCalendarDay> days = calendarMapper.selectByDateRange(start, end);
    return days.stream().map(this::toDTO).collect(Collectors.toList());
}
```

- [ ] **Step 3: 在 CalendarController 中新增公共按月查询接口**

```java
/**
 * 获取指定年月的日历数据（公共接口，无鉴权）
 * @param year 年份
 * @param month 月份（1-12）
 * @return 日历天列表
 */
@GetMapping("/api/sys/calendar")
@Operation(summary = "按月查询日历")
public ResponseWrapper<List<CalendarDayRespDTO>> getCalendarByMonth(
        @RequestParam(value = "year") int year,
        @RequestParam(value = "month") int month) {
    log.debug("[CalendarController.getCalendarByMonth] year={}, month={}", year, month);
    List<CalendarDayDTO> days = calendarService.getDaysByMonth(year, month);
    // 转换为响应DTO
    List<CalendarDayRespDTO> result = days.stream().map(d -> {
        CalendarDayRespDTO r = new CalendarDayRespDTO();
        r.setDay(d.getDay().toString());
        r.setIsWorkday(d.getIsWorkday() == 1);
        r.setDayOfWeek(d.getDay().getDayOfWeek().getValue());
        r.setRemark(d.getRemark());
        return r;
    }).collect(Collectors.toList());
    return ResponseWrapper.success(result);
}
```

- [ ] **Step 4: 编写单元测试**

```java
@Test
void getCalendarByMonth_shouldReturnMonthData() throws Exception {
    when(calendarService.getDaysByMonth(anyInt(), anyInt()))
        .thenReturn(List.of(makeCalendarDayDTO(LocalDate.of(2026, 4, 1), 1, null)));
    
    mockMvc.perform(get("/api/sys/calendar")
            .param("year", "2026")
            .param("month", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isArray());
}
```

- [ ] **Step 5: 运行测试验证**

Run: `cd system-governance-center && mvn test -Dtest=CalendarControllerTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/CalendarController.java
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/CalendarDayRespDTO.java
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/service/CalendarService.java
git commit -m "feat(gov): 新增工作日历按月公共查询接口 B.1"
```

---

### Task 3: 修改工作日历 B.2（PUT /api/admin/sys/calendar/{date}）

**Files:**
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/CalendarController.java`
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/CalendarDayUpdateReqDTO.java`

- [ ] **Step 1: 创建 CalendarDayUpdateReqDTO**

```java
package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 日历天更新请求DTO（B.2切换工作/休息状态）
 */
@Data
public class CalendarDayUpdateReqDTO {
    @NotNull(message = "isWorkday 不能为空")
    private Boolean isWorkday;
    private String remark;
}
```

- [ ] **Step 2: 在 CalendarService 中新增设置方法（而非翻转）**

```java
/**
 * 设置指定日期的工作日/休息日状态
 * @param date 日期
 * @param isWorkday 是否工作日
 * @param remark 备注
 */
@Transactional
public void setWorkday(LocalDate date, boolean isWorkday, String remark) {
    // 过去日期不允许修改
    if (date.isBefore(LocalDate.now())) {
        throw new BizException(GovErrorCode.PAST_DATE_NOT_MODIFIABLE.getCode(),
                GovErrorCode.PAST_DATE_NOT_MODIFIABLE.getMessage());
    }
    SysCalendarDay existing = calendarMapper.selectByDay(date);
    if (existing == null) {
        existing = new SysCalendarDay();
        existing.setDay(date);
        existing.setIsWorkday(isWorkday ? 1 : 0);
        existing.setRemark(remark);
        existing.setCreatedTime(LocalDateTime.now());
        existing.setUpdatedTime(LocalDateTime.now());
        calendarMapper.insert(existing);
    } else {
        existing.setIsWorkday(isWorkday ? 1 : 0);
        existing.setRemark(remark);
        existing.setUpdatedTime(LocalDateTime.now());
        calendarMapper.updateById(existing);
    }
    // 清除该年份缓存
    redisTemplate.delete(CACHE_PREFIX + date.getYear());
}
```

- [ ] **Step 3: 修改 CalendarController 接口**

```java
/**
 * 设置指定日期的工作日/休息日状态
 * @param date 日期（yyyy-MM-dd 格式路径参数）
 * @return 成功响应
 */
@PutMapping("/{date}")
@Operation(summary = "设置工作日/休息日状态")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
public ResponseWrapper<Void> setWorkday(
        @PathVariable(value = "date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @Valid @RequestBody CalendarDayUpdateReqDTO req) {
    log.info("[CalendarController.setWorkday] date={}, isWorkday={}", date, req.getIsWorkday());
    calendarService.setWorkday(date, req.getIsWorkday(), req.getRemark());
    return ResponseWrapper.success();
}
```

- [ ] **Step 4: 修改单元测试**

```java
@Test
void setWorkday_shouldReturn200() throws Exception {
    doNothing().when(calendarService).setWorkday(any(LocalDate.class), anyBoolean(), any());
    
    mockMvc.perform(put("/api/admin/sys/calendar/2026-05-01")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"isWorkday\": true, \"remark\": \"调休\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));
}
```

- [ ] **Step 5: 运行测试验证**

Run: `cd system-governance-center && mvn test -Dtest=CalendarControllerTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/CalendarController.java
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/CalendarDayUpdateReqDTO.java
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/service/CalendarService.java
git commit -m "fix(gov): 工作日历B.2改为设置状态接口"
```

---

### Task 4: 新增工作日历 B.4 批量导入节假日接口

**Files:**
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/CalendarImportReqDTO.java`
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/CalendarImportRespDTO.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/CalendarController.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/CalendarService.java`

- [ ] **Step 1: 创建导入请求/响应DTO**

```java
// CalendarImportReqDTO.java
package com.bank.branch.platform.governance.api.dto;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.NotNull;

@Data
public class CalendarImportReqDTO {
    @NotNull(message = "文件不能为空")
    private MultipartFile file;
}

// CalendarImportRespDTO.java
package com.bank.branch.platform.governance.api.dto;
import lombok.Data;

@Data
public class CalendarImportRespDTO {
    private Integer totalRows;
    private Integer successRows;
    private Integer skippedRows;
}
```

- [ ] **Step 2: 在 CalendarService 中新增 Excel 解析导入方法**

```java
/**
 * 批量导入节假日（Excel格式）
 * @param file Excel文件
 * @return 导入结果
 */
public CalendarImportRespDTO importFromExcel(MultipartFile file) {
    // 使用 Apache POI 解析 Excel
    // 格式：日期(yyyy-MM-dd), 是否工作日(1/0), 备注
    // 只处理未来日期
    List<CalendarDayDTO> toImport = new ArrayList<>();
    int skipped = 0;
    LocalDate today = LocalDate.now();
    
    try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
        Sheet sheet = workbook.getSheetAt(0);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // 跳过表头
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            Cell dateCell = row.getCell(0);
            Cell workdayCell = row.getCell(1);
            Cell remarkCell = row.getCell(2);
            
            LocalDate date = dateCell.getLocalDateTimeCellValue().toLocalDate();
            if (date.isBefore(today)) {
                skipped++;
                continue;
            }
            
            CalendarDayDTO dto = new CalendarDayDTO();
            dto.setDay(date);
            dto.setIsWorkday(Integer.valueOf(workdayCell.getNumericCellValue()).intValue());
            dto.setRemark(remarkCell != null ? remarkCell.getStringCellValue() : null);
            toImport.add(dto);
        }
    } catch (Exception e) {
        throw new BizException(GovErrorCode.FILE_FORMAT_INVALID.getCode(), "Excel解析失败: " + e.getMessage());
    }
    
    batchImport(toImport);
    
    CalendarImportRespDTO resp = new CalendarImportRespDTO();
    resp.setTotalRows(toImport.size() + skipped);
    resp.setSuccessRows(toImport.size());
    resp.setSkippedRows(skipped);
    return resp;
}
```

- [ ] **Step 3: 在 CalendarController 中新增导入接口**

```java
/**
 * 批量导入节假日（Excel格式）
 * @param file Excel文件
 * @return 导入结果
 */
@PostMapping("/import")
@Operation(summary = "批量导入节假日")
@BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.IMPORT)
public ResponseWrapper<CalendarImportRespDTO> importCalendar(
            @RequestParam(value = "file") MultipartFile file) {
    log.info("[CalendarController.importCalendar] fileName={}", file.getOriginalFilename());
    CalendarImportRespDTO result = calendarService.importFromExcel(file);
    return ResponseWrapper.success(result);
}
```

- [ ] **Step 4: 编写单元测试**

- [ ] **Step 5: 运行测试验证**

- [ ] **Step 6: Commit**

---

## 第二批：文件管理修复

### Task 5: 修改文件管理 G.2（302重定向下载）

### Task 6: 修改文件管理 G.3（Query参数查询）

### Task 7: 新增文件管理 G.4（删除文件）

---

## 第三批：低优先级修复

### Task 8: JobController trigger reason 改为 RequestBody

### Task 9: SqlProbeController execute 改为 RequestBody

### Task 10: NotificationController 新增详情接口 E.5

---

## 实施顺序总结

1. **Task 1**: 审计日志路径修复（影响范围小，改动明确）
2. **Task 2**: 工作日历 B.1 公共查询接口
3. **Task 3**: 工作日历 B.2 修改
4. **Task 4**: 工作日历 B.4 批量导入
5. **Task 5-7**: 文件管理（G.2/G.3/G.4）
6. **Task 8-10**: 低优先级修复

---
