# System-Governance-Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the system-governance-center module providing 8 governance domains (Dict, Config, Calendar, Audit, Notify, File, Job, SQL Probe) for the bank branch platform.

**Architecture:** Maven module following auth-permission-center patterns — Entity → Mapper XML → Service → Facade (Api impl) → Controller. Redis Cache-Aside for Dict/Config/Calendar. MinIO for file storage. TDD with JUnit 5 + Mockito + AssertJ.

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, Redis 6.x, MinIO 8.5.7, Knife4j 4.4.0

**Reference files:**
- Spec: `docs/superpowers/specs/2026-04-03-system-governance-center-design.md`
- DDL: `docs/modules/system-governance-center/05-表结构DDL.md`
- API design: `docs/modules/system-governance-center/03-接口设计与报文.md`
- API contract: `docs/modules/system-governance-center/04-对外API契约.md`
- Common dev guide: `docs/common-dev-guide.md`
- Reference module: `auth-permission-center/` (follow same patterns)

**IMPORTANT - Before each task:**
1. Read the reference files listed above to understand exact field names, types, and constraints
2. Read `docs/modules/system-governance-center/05-表结构DDL.md` for exact column mappings
3. Read the corresponding section in `docs/modules/system-governance-center/03-接口设计与报文.md` for request/response DTOs
4. Read `docs/modules/system-governance-center/04-对外API契约.md` for API interface signatures
5. Follow auth-permission-center patterns exactly (entity style, mapper XML style, service style)

**IMPORTANT - Common module classes to use:**
- `com.bank.branch.platform.common.web.ResponseWrapper` — success(data), success(), page(pageResult), error(code, msg)
- `com.bank.branch.platform.common.web.PageResult` — of(pageNo, pageSize, total, records)
- `com.bank.branch.platform.common.web.exception.BizException` — BizException(code, message)
- `com.bank.branch.platform.common.web.exception.PermissionDeniedException`
- `com.bank.branch.platform.common.security.annotation.BizAuth` — @BizAuth(bizType, action)
- `com.bank.branch.platform.common.security.enums.BizType` — SYS_CONFIG etc.
- `com.bank.branch.platform.common.security.enums.BizAction` — READ, CONFIG, EXPORT, etc.
- `com.bank.branch.platform.common.aop.handler.AuditLogHandler` — interface to implement
- `com.bank.branch.platform.common.aop.event.AuditLogEvent` — event record for audit

---

## Phase 1: Dict + Config + Calendar

### Task 1: Module Scaffolding + Enums + Root POM

**Files:**
- Create: `system-governance-center/pom.xml`
- Modify: `pom.xml` (root) — add module + dependencyManagement
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/enums/GovErrorCode.java`

**Context:** Follow `auth-permission-center/pom.xml` pattern exactly. The root pom.xml needs `system-governance-center` added to `<modules>` and `<dependencyManagement>`.

- [ ] **Step 1:** Read `auth-permission-center/pom.xml` and root `pom.xml` to understand dependency patterns
- [ ] **Step 2:** Create `system-governance-center/pom.xml` with dependencies: all 5 common modules, auth-permission-center, spring-boot-starter-web, spring-boot-starter-data-redis, mybatis-spring-boot-starter, knife4j, spring-boot-starter-test, lombok (provided), minio 8.5.7
- [ ] **Step 3:** Modify root `pom.xml` — add `<module>system-governance-center</module>` and add it to `<dependencyManagement>`
- [ ] **Step 4:** Create `GovErrorCode` enum with all error codes from spec (GOV-40001 through GOV-50003). Follow `auth-permission-center/src/main/java/.../enums/AuthErrorCode.java` pattern exactly.
- [ ] **Step 5:** Verify compilation: `mvn compile -pl system-governance-center -am -q`
- [ ] **Step 6:** Commit: `feat(governance): Task 1 - 模块脚手架 + GovErrorCode 枚举`

---

### Task 2: Dict Domain — Entity + Mapper + Service (TDD)

**Files:**
- Create: `system-governance-center/src/main/java/.../governance/entity/SysDict.java`
- Create: `system-governance-center/src/main/java/.../governance/mapper/DictMapper.java`
- Create: `system-governance-center/src/main/resources/mapper/governance/DictMapper.xml`
- Create: `system-governance-center/src/main/java/.../governance/service/DictService.java`
- Create: `system-governance-center/src/test/java/.../governance/service/DictServiceTest.java`

**Context:**
- Read `docs/modules/system-governance-center/05-表结构DDL.md` for sys_dict table columns
- Follow `auth-permission-center` entity/mapper/service patterns exactly
- Entity fields: id, dictType, dictCode, dictLabel, dictValue, sortOrder, status, remark, createdBy, createdTime, updatedBy, updatedTime
- Mapper methods: selectByDictType, selectByDictTypeAndDictCode, insert, updateById, selectByPage, countByPage, existsByDictTypeAndDictCode
- DictService: getDictItems(dictType), getDictLabel(dictType, dictCode), isValidDictValue(dictType, dictCode), batchGetDictItems(Set), createDict(...), updateDict(id, ...), deleteDict(id), toggleStatus(id), listByPage(...)
- Redis cache: key=`gov:dict:{dictType}`, TTL=10min, evict on write

- [ ] **Step 1:** Write DictServiceTest (RED): test getDictItems returns items from cache when hit, loads from DB on miss; test createDict validates uniqueness (GOV-40901); test deleteDict sets status to DISABLED; test getDictLabel returns label or null
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement Entity (SysDict.java — @Data, follow PtResource pattern)
- [ ] **Step 4:** Implement Mapper interface (DictMapper.java — @Mapper, follow ResourceMapper pattern)
- [ ] **Step 5:** Implement Mapper XML (DictMapper.xml — follow ResourceMapper.xml pattern with BASE_COLUMNS sql fragment)
- [ ] **Step 6:** Implement DictService (@Slf4j @Service @RequiredArgsConstructor, inject RedisTemplate + DictMapper)
- [ ] **Step 7:** Run tests to verify they pass: `mvn test -pl system-governance-center -Dtest=DictServiceTest`
- [ ] **Step 8:** Commit: `feat(governance): Task 2 - Dict Entity + Mapper + Service (TDD)`

---

### Task 3: Dict Domain — API + DTO + Facade (TDD)

**Files:**
- Create: `system-governance-center/src/main/java/.../governance/api/DictApi.java`
- Create: `system-governance-center/src/main/java/.../governance/api/dto/DictItemDTO.java`
- Create: `system-governance-center/src/main/java/.../governance/api/dto/DictCreateReqDTO.java`
- Create: `system-governance-center/src/main/java/.../governance/api/dto/DictUpdateReqDTO.java`
- Create: `system-governance-center/src/main/java/.../governance/api/dto/DictRespDTO.java`
- Create: `system-governance-center/src/main/java/.../governance/facade/DictFacade.java`
- Create: `system-governance-center/src/test/java/.../governance/facade/DictFacadeTest.java`

**Context:**
- Read `docs/modules/system-governance-center/04-对外API契约.md` for exact DictApi interface signatures
- Read `docs/modules/system-governance-center/03-接口设计与报文.md` for DTO fields
- DictApi: getDictItems(dictType) → List<DictItemDTO>, getDictLabel(dictType, dictCode) → String, isValidDictValue(dictType, dictCode) → boolean, batchGetDictItems(Set<String>) → Map<String, List<DictItemDTO>>
- DictFacade implements DictApi, delegates to DictService
- Key rule: getDictItems for non-existent dictType returns empty list (NOT exception)

- [ ] **Step 1:** Write DictFacadeTest (RED): test getDictItems returns DTOs from service; test getDictItems returns empty list for unknown dictType (not exception); test batchGetDictItems merges results
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement DictApi interface + DTOs (DictItemDTO, DictCreateReqDTO, DictUpdateReqDTO, DictRespDTO)
- [ ] **Step 4:** Implement DictFacade (@Service implements DictApi, delegates to DictService, entity→DTO conversion)
- [ ] **Step 5:** Run tests: `mvn test -pl system-governance-center -Dtest=DictFacadeTest`
- [ ] **Step 6:** Commit: `feat(governance): Task 3 - Dict API + DTO + Facade (TDD)`

---

### Task 4: Config Domain — Full Stack (TDD)

**Files:**
- Create: entity/SysConfigKv.java, mapper/ConfigMapper.java, resources/mapper/governance/ConfigMapper.xml
- Create: service/ConfigService.java, api/ConfigApi.java, api/dto/ConfigDTO.java, api/dto/ConfigUpdateReqDTO.java
- Create: facade/ConfigFacade.java
- Create: test: service/ConfigServiceTest.java

**Context:**
- Read DDL for sys_config_kv: id, config_key(unique), config_value(longtext), value_type(STRING/JSON/NUMBER/BOOL), status, remark, created_by/time, updated_by/time
- ConfigApi: getConfigValue(key) → String, getConfigValue(key, defaultValue) → String, getConfigValue(key, Class<T>) → T
- Type conversion: NUMBER→Long, BOOL→Boolean, JSON→Jackson deserialize, STRING→as-is
- Redis cache: key=`gov:config:{configKey}`, TTL=10min
- ConfigService: getConfigValue, listConfigs(page), updateConfig(key, value) with type validation

- [ ] **Step 1:** Write ConfigServiceTest (RED): test getConfigValue returns from cache; test getConfigValue loads from DB on miss; test type conversion (NUMBER→Long, BOOL→Boolean); test updateConfig evicts cache; test getConfigValue with default returns default when key missing
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement Entity + Mapper + Mapper XML + Service + API + DTOs + Facade
- [ ] **Step 4:** Run all tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 4 - Config 全栈实现 (TDD)`

---

### Task 5: Calendar Domain — Full Stack (TDD)

**Files:**
- Create: entity/SysCalendarDay.java, mapper/CalendarMapper.java, resources/mapper/governance/CalendarMapper.xml
- Create: service/CalendarService.java, api/CalendarApi.java, api/dto/CalendarDayDTO.java
- Create: facade/CalendarFacade.java
- Create: test: service/CalendarServiceTest.java

**Context:**
- Read DDL for sys_calendar_day: day(PK, DATE), is_workday(tinyint), remark, created_by, created_time, updated_by, updated_time
- CalendarApi: isWorkingDay(LocalDate) → boolean, countWorkingDays(from, to) → int, getWorkingDays(year) → List<CalendarDayDTO>, addWorkingDays(from, days) → LocalDate
- CalendarService: above queries + toggleWorkday(date) — past dates rejected (GOV-40301), initYear(year) — idempotent, batchImport(List) — future dates only
- Redis cache: key=`gov:calendar:{year}`, TTL=24h
- Business logic: addWorkingDays must skip non-working days; countWorkingDays counts is_workday=1 between from and to (inclusive)

- [ ] **Step 1:** Write CalendarServiceTest (RED): test isWorkingDay returns true for workday; test countWorkingDays counts correctly; test addWorkingDays skips weekends; test toggleWorkday rejects past dates (GOV-40301); test initYear is idempotent
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement Entity + Mapper + Mapper XML + Service + API + DTO + Facade
- [ ] **Step 4:** Run all tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 5 - Calendar 全栈实现 (TDD)`

---

## Phase 2: Audit + Notify

### Task 6: Audit Domain — Entity + Mapper + Service (TDD)

**Files:**
- Create: entity/AuditLog.java, mapper/AuditLogMapper.java, resources/mapper/governance/AuditLogMapper.xml
- Create: service/AuditLogService.java, api/AuditApi.java
- Create: api/cmd/AuditLogCmd.java, api/dto/AuditLogDTO.java, api/dto/AuditLogQueryReqDTO.java
- Create: facade/AuditFacade.java
- Create: test: service/AuditLogServiceTest.java

**Context:**
- Read DDL for audit_log: id, trace_id, emp_id, emp_name, biz_type, biz_action, resource_url, request_method, request_params(text, desensitized), response_status, error_msg, ip_address, user_agent, execution_time(int ms), reason, created_time
- AuditApi: log(AuditLogCmd), queryLogs(AuditLogQueryReqDTO, pageNo, pageSize) → PageResult<AuditLogDTO>
- CRITICAL: log() uses @Transactional(propagation = REQUIRES_NEW) for independent transaction
- AuditLogCmd fields: traceId, empId, empName, bizType, bizAction, resourceUrl, requestMethod, requestParams, responseStatus, ipAddress, userAgent, executionTime, reason
- queryLogs supports: empId, bizType, bizAction, startDate, endDate, keyword filters
- Sensitive data masking: use SensitiveDataMasker from common-security on requestParams

- [ ] **Step 1:** Write AuditLogServiceTest (RED): test log() inserts record; test queryLogs with filters; test queryLogs pagination
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement Entity + Mapper + Mapper XML + Service (with REQUIRES_NEW) + API + Cmd + DTOs + Facade
- [ ] **Step 4:** Run tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 6 - Audit 全栈实现 (TDD)`

---

### Task 7: AuditLogHandler Implementation

**Files:**
- Create: `system-governance-center/src/main/java/.../governance/handler/GovAuditLogHandler.java`
- Create: test: `system-governance-center/src/test/java/.../governance/handler/GovAuditLogHandlerTest.java`

**Context:**
- Read `common/common-aop/src/main/java/.../aop/handler/AuditLogHandler.java` for interface signature
- Read `common/common-aop/src/main/java/.../aop/event/AuditLogEvent.java` for event fields
- GovAuditLogHandler implements AuditLogHandler, converts AuditLogEvent → AuditLogCmd, calls AuditLogService.log()
- This bean replaces NoopAuditLogHandler as the active implementation (use @Primary or @ConditionalOnBean)

- [ ] **Step 1:** Write GovAuditLogHandlerTest (RED): test handle() converts event fields correctly and calls service
- [ ] **Step 2:** Run test to verify it fails
- [ ] **Step 3:** Implement GovAuditLogHandler (@Component @Primary, implements AuditLogHandler)
- [ ] **Step 4:** Run tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 7 - AuditLogHandler 实现（替换 NoopAuditLogHandler）`

---

### Task 8: Notify Domain — Full Stack (TDD)

**Files:**
- Create: entity/UserNotification.java, mapper/NotificationMapper.java, resources/mapper/governance/NotificationMapper.xml
- Create: service/NotificationService.java, api/NotifyApi.java
- Create: api/cmd/NotificationCmd.java, api/dto/NotificationDTO.java
- Create: facade/NotifyFacade.java
- Create: enums/NotifyType.java (SYSTEM, WORKFLOW, BUSINESS)
- Create: test: service/NotificationServiceTest.java

**Context:**
- Read DDL for user_notification: id, emp_id, title, content(text), notify_type, biz_type, biz_id, link_url, is_read(0/1), read_time, created_time
- NotifyApi: sendNotification(NotificationCmd), batchSendNotifications(List), countUnread(empId) → int, queryNotifications(empId, isRead, pageNo, pageSize) → PageResult
- NotificationCmd fields: empId, title, content, notifyType, bizType, bizId, linkUrl
- Service: sendNotification inserts record; markAsRead(id) sets is_read=1 + read_time; markAllAsRead(empId); countUnread(empId)
- Facade: sendNotification supports @TransactionalEventListener(phase=AFTER_COMMIT) pattern for async

- [ ] **Step 1:** Write NotificationServiceTest (RED): test sendNotification inserts; test countUnread; test markAsRead; test queryNotifications with isRead filter
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement Entity + Mapper + XML + Service + API + Cmd + DTOs + Facade + NotifyType enum
- [ ] **Step 4:** Run all tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 8 - Notify 全栈实现 (TDD)`

---

## Phase 3: File + Job

### Task 9: File Domain — Full Stack (TDD)

**Files:**
- Create: entity/FileObject.java, entity/BizFileRel.java
- Create: mapper/FileObjectMapper.java, mapper/BizFileRelMapper.java
- Create: resources/mapper/governance/FileObjectMapper.xml, resources/mapper/governance/BizFileRelMapper.xml
- Create: service/FileService.java, api/FileApi.java
- Create: api/dto/FileObjectDTO.java
- Create: facade/FileFacade.java
- Create: config/MinioConfig.java
- Create: test: service/FileServiceTest.java

**Context:**
- Read DDL for file_object and biz_file_rel tables
- FileApi: upload(MultipartFile, uploadedBy) → FileObjectDTO, getDownloadUrl(fileId) → String, bindFile(bizType, bizId, fileObjectId, fileRole), listBizFiles(bizType, bizId) → List<FileObjectDTO>, deleteFile(fileId)
- Upload flow: validate format/size → MD5 → check duplicate (same MD5 reuses existing) → MinIO upload → DB insert
- Storage path: `/{bizType}/{yyyy}/{MM}/{dd}/{uuid}.{ext}`
- Download: MinIO presigned URL (1hr expiry)
- MinioConfig: reads endpoint/accessKey/secretKey/bucket from application.yml, creates MinioClient bean
- For tests: mock MinioClient (don't require real MinIO for unit tests)

- [ ] **Step 1:** Write FileServiceTest (RED): test upload validates format; test upload calculates MD5 and deduplicates; test getDownloadUrl generates presigned URL; test bindFile is idempotent; test listBizFiles returns files; test deleteFile removes record
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement entities + mappers + XML + MinioConfig + FileService + API + DTO + Facade
- [ ] **Step 4:** Run tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 9 - File 全栈实现 + MinIO 配置 (TDD)`

---

### Task 10: Job Domain — Full Stack (TDD)

**Files:**
- Create: entity/SysJobConf.java, entity/SysJobRunLog.java
- Create: mapper/JobConfMapper.java, mapper/JobRunLogMapper.java
- Create: resources/mapper/governance/JobConfMapper.xml, resources/mapper/governance/JobRunLogMapper.xml
- Create: service/JobService.java, api/JobApi.java
- Create: api/dto/SysJobConfDTO.java, api/dto/SysJobRunLogDTO.java
- Create: facade/JobFacade.java
- Create: enums/JobStatus.java (ACTIVE, PAUSED), enums/JobRunStatus.java (RUNNING, SUCCESS, FAILED)
- Create: test: service/JobServiceTest.java

**Context:**
- Read DDL for sys_job_conf and sys_job_run_log
- JobApi: getJobConf(jobKey) → SysJobConfDTO, startJobRun(jobId, triggerType, operatorEmpId) → String(runLogId), completeJobRun(runLogId), failJobRun(runLogId, errorMsg)
- Concurrent prevention: startJobRun checks if any RUNNING log exists for this jobId → GOV-40903
- startJobRun creates RUNNING log, returns runLogId; completeJobRun/failJobRun update status + endTime
- JobService: listJobs(page), listRunLogs(jobId, page), pauseJob(jobId), resumeJob(jobId)
- V1: business modules use @Scheduled + JobApi; architecture ready for V2 unified scheduler

- [ ] **Step 1:** Write JobServiceTest (RED): test startJobRun creates RUNNING log; test startJobRun rejects when RUNNING exists (GOV-40903); test completeJobRun updates status; test failJobRun records error; test pauseJob/resumeJob toggles status
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement entities + mappers + XML + Service + API + DTOs + Facade + enums
- [ ] **Step 4:** Run all tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 10 - Job 全栈实现 (TDD)`

---

## Phase 4: SQL Probe + Controllers + Config

### Task 11: SQL Probe Service (TDD)

**Files:**
- Create: service/SqlProbeService.java
- Create: test: service/SqlProbeServiceTest.java

**Context:**
- SqlProbeService.executeSql(String sql, String reason) → List<Map<String, Object>>
- Security chain: SELECT-only (GOV-42201), force LIMIT (default 1000), timeout 30s, concurrency limit 3 (Semaphore)
- Uses separate readonly DataSource (inject via @Qualifier)
- Each execution writes audit log via AuditApi (bizAction=EXECUTE_SQL)
- History: query from audit_log where bizAction=EXECUTE_SQL

- [ ] **Step 1:** Write SqlProbeServiceTest (RED): test rejects non-SELECT (GOV-42201); test forces LIMIT on SELECT without LIMIT; test concurrency limit (GOV-42202); test successful execution returns results; test writes audit log
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement SqlProbeService (use Semaphore for concurrency, Statement.setQueryTimeout for timeout)
- [ ] **Step 4:** Run tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 11 - SQL Probe Service (TDD)`

---

### Task 12: All 8 Controllers (TDD)

**Files:**
- Create: controller/DictController.java, controller/ConfigController.java, controller/CalendarController.java
- Create: controller/AuditLogController.java, controller/NotificationController.java
- Create: controller/FileController.java, controller/JobController.java, controller/SqlProbeController.java
- Create: test files for each controller

**Context:**
- Read `docs/modules/system-governance-center/03-接口设计与报文.md` for exact endpoint paths, params, request/response DTOs
- All controllers: @Slf4j @RestController @RequiredArgsConstructor @RequestMapping
- Use ResponseWrapper.success(data) for responses, ResponseWrapper.page(pageResult) for pagination
- Use @BizAuth(bizType=BizType.SYS_CONFIG, action=...) on each endpoint
- Use @Valid on request bodies
- Use MockMvcBuilders.standaloneSetup(controller) for tests (no Spring context)
- Test at least 2 endpoints per controller

Controller → endpoint mapping:
- DictController: GET /api/sys/dicts (public query) + /api/admin/sys/dicts (CRUD)
- ConfigController: GET/PUT /api/admin/sys/configs
- CalendarController: GET/PUT/POST /api/admin/sys/calendar
- AuditLogController: GET /api/admin/audit-logs
- NotificationController: GET /api/notifications (user) + read/readAll
- FileController: POST /api/files/upload, GET /api/files/{fileId}/download, etc.
- JobController: GET/POST /api/admin/sys/jobs
- SqlProbeController: POST /api/admin/sql-probe/execute, GET /api/admin/sql-probe/history

- [ ] **Step 1:** Write controller tests (RED) — at least 2 tests per controller using MockMvcBuilders.standaloneSetup
- [ ] **Step 2:** Run tests to verify they fail
- [ ] **Step 3:** Implement all 8 controllers
- [ ] **Step 4:** Run all tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 12 - 全部 8 个 Controller (TDD)`

---

### Task 13: Config Classes + AutoConfiguration

**Files:**
- Create: config/GovCacheConfig.java (RedisTemplate serialization)
- Create: config/MinioConfig.java (if not done in Task 9)
- Create: resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

**Context:**
- GovCacheConfig: same pattern as auth-permission-center's CacheConfig (StringRedisSerializer for key, GenericJackson2JsonRedisSerializer for value)
- AutoConfiguration.imports: register GovCacheConfig, MinioConfig
- No independent Filter/Interceptor needed (reuse auth module's)

- [ ] **Step 1:** Implement GovCacheConfig (follow auth CacheConfig pattern)
- [ ] **Step 2:** Create AutoConfiguration.imports file
- [ ] **Step 3:** Verify full compilation: `mvn compile -pl system-governance-center -am`
- [ ] **Step 4:** Run all tests: `mvn test -pl system-governance-center`
- [ ] **Step 5:** Commit: `feat(governance): Task 13 - Config + AutoConfiguration`

---

### Task 14: Final Verification

- [ ] **Step 1:** Run full module tests: `mvn test -pl system-governance-center` — all tests must pass
- [ ] **Step 2:** Run full project compilation: `mvn compile -am` — verify no cross-module compilation errors
- [ ] **Step 3:** Verify test count and 0 failures
- [ ] **Step 4:** Final commit if any cleanup needed
