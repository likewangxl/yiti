# 示例代码索引（Canonical Examples）

> **用途**：开发某类功能时，按本索引直接打开"最规范的现成实现"照着写，而不是从零发明或模仿碰巧看到的旧代码。
> **维护约定**：新增/替换某类规范实现时同步更新本文件；各级 AGENTS.md 只引用本索引，不复制内容。范例失效（文件被删/模式被废弃）时必须删行或改指向。
> 盘点基准：2026-07-19（全部路径已核实存在）。

## 后端

- 支行 KPI 只读范围交集：`performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiScoreCalcService.java` 的机构查询重载；新路径校验方案、日期和授权上下文，再把指定机构与用户可见范围求交，下推 Mapper，并通过 `scopeOrgCode` 明确响应身份。
- 支行指标完成及完整排名：`xanzc_frontend/src/views/screen/panorama/branchAchievementModel.js`、`branchPerformanceModel.js`；全量分页与旧后端范围确认守护：`branchPerformanceLoader.js`；页面实现：`BranchCoreMetrics.vue`、`BranchAchievementPanel.vue`、`BranchPerformancePanel.vue`。

- 代码化大屏模板声明与机构地图边界：`report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/CodeScreenPresentationValidator.java`；前端槽位/单位适配：`xanzc_frontend/src/views/screen/panorama/bindings.js`、`dataAdapter.js`。约束与使用步骤见 `docs/modules/report-analytics-center/11-代码化经营大屏.md`。

### 文件上传 + 对象存储（华为云 OBS）
| 关注点 | 位置 | 说明 |
|---|---|---|
| 上传/下载 Controller | `system-governance-center/.../governance/controller/FileController.java` | 唯一通用文件管理入口：上传、下载（预签名/流式）、关联查询、删除 |
| FileApi 契约 | `system-governance-center/.../governance/api/FileApi.java` | 跨模块文件能力唯一入口；`bindFile(bizType, bizId, fileObjectId, fileRole)` 注意 bizId 是 String |
| bindFile 消费方范例 | `red-engine-center/.../redengine/service/ReSubmitService.java`（createSubmit 内） | 业务创建后逐附件 `fileApi.bindFile("RE_SUBMIT", ...)` 的典范写法 |
| 存储客户端实现 | `system-governance-center/.../governance/storage/ObsStorageClient.java` | putObject / getBytes / deleteByKey / generatePresignedUrl；懒连接 |
| 单测 | 同模块 `storage/ObsStorageClientTest.java`、`FileServiceTest.java` | bindFile 用例参考 |

⚠️ 实际对象存储是**华为云 OBS**（`esdk-obs-java-bundle`，配置键 `obs.*`）。`FileApi.java` / `FileController.java` 的 Javadoc 中残留的 "MinIO" 字样是历史注释，勿据此判断实现。

### MyBatis-Plus 标准 CRUD（新增功能红线写法）
| 关注点 | 位置 | 说明 |
|---|---|---|
| 实体三件套 | `red-engine-center/.../redengine/entity/ReSubmit.java` | `@TableName` + `@TableId(IdType.AUTO)` + `@TableLogic` 齐全的标准范例 |
| 纯 BaseMapper | `red-engine-center/.../redengine/mapper/ReSubmitMapper.java` | `extends BaseMapper<T>`，零 XML（red-engine 全模块 8 个 Mapper 均无 XML，反向对照"简单 CRUD 不写 XML"） |
| LambdaQueryWrapper + 分页 | `red-engine-center/.../redengine/service/ReSubmitService.java`（getMySubmits） | `selectPage(new Page<>(...), wrapper)` 标准分页 |

### 自定义 SQL 的 XML（BaseMapper 覆盖不到才写）
- 接口：`performance-engine-center/.../performance/eval/mapper/EvalUserTagMapper.java` — 仅声明 batchInsert / JOIN 查询等 4 个自定义方法
- XML：`performance-engine-center/src/main/resources/mapper/performance/EvalUserTagMapper.xml` — `<foreach>` 批量插入 + `INNER JOIN` 聚合投影，两类"必须落 XML"的典型场景

### 标准 Controller（@BizAuth + @AuditLog + @Valid + PageResult 四要素）
- 首选：`customer-marketing-center/.../customer/controller/TouchTaskController.java` — 类级 `@Validated`；`listPage` 分页读；`cancel` 高危写（`@BizAuth(WRITE)` + `@AuditLog(reasonRequired = true)` + `@Valid @RequestBody`）
- 配套 DTO：同模块 `dto/req/TouchCancelReqDTO.java` — reason 的 `@NotBlank`（reasonRequired 的实际强制手段就是 DTO 校验）
- 备选：`CustomerController.java`（transfer 高危）、`LeadController.java`（读/分页更全）

### PT_RESOURCE 资源注册 + 角色绑定种子 SQL
- 一站式范例：`docs/superpowers/sql/2026-07-18-redengine-seed.sql` — PT_RESOURCE（含 `MENU_RANK_NO` 防"同 METHOD 通配符抢先匹配字面 URL"的踩坑注释）→ PT_ROLE → PT_ROLE_RESOURCE → PT_ROLE_BIZ_SCOPE 全链
- 轻量单表范例：`docs/superpowers/sql/2026-04-14-customer-touch-pt-resource.sql`

### DATA_SCOPE 数据范围查询
- 规范模板：`docs/common-dev-guide.md` §5（7 种 DataScopeType → SQL 谓词模板）
- 真实落地：`performance-engine-center/.../performance/service/PerfRunTaskService.java`（resolveScopeFilter：调 `BizScopeApi.resolveScope`，ALL→null，否则拼谓词）+ `mapper/performance/PerfRunTaskMapper.xml`（头部安全声明：`${dataScopeFilter}` 是唯一允许的 `${}` 注入点）

⚠️ `common-security` 的 `ObjectMetaRegistry` 是规划态框架件，当前无业务模块调用；文档示例里的 `DataPermissionChecker` 类在仓库中不存在——两者都不要当"现成实现"引用。

### 跨模块调用（*Api / *QueryApi）
- 接口定义：`customer-marketing-center/.../customer/api/CustomerQueryApi.java` — 全返回 DTO，Javadoc 明确禁止跨模块直连 Mapper
- 消费方：`report-analytics-center/.../report/service/impl/CustPoolSummaryServiceImpl.java` — 只读模块纯消费上游 *Api 的典范

### Flowable 工作流接入（仅 workflow-center 可直连 Flowable）
- 发起流程：`workflow-center/.../workflow/service/ProcessStartService.java` — 校验流程定义 → businessKey 唯一 → startProcessInstanceByKey → 写 BIZ_PROCESS_MAP → 发 ProcessStartedEvent
- 任务办理：`workflow-center/.../workflow/service/TaskOperationService.java` — 签收/审批/驳回 + TaskApprovedEvent / ProcessCompletedEvent
- 业务侧发起方：`customer-marketing-center/.../customer/service/LeadService.java`（submitForApproval：SELECT FOR UPDATE 防并发 → 组装 StartProcessCmd → `workflowApi.startProcess`）

### Quartz 任务（sys_job_conf 动态注册）
- 裸业务方法：`performance-engine-center/.../performance/job/SysControlCleanupJob.java`（`@Component`，无调度注解）
- Quartz 包装类：同模块 `job/quartz/SysControlCleanupQuartzJob.java`（**不加** `@Component`，仅委托裸方法）
- 注册方式：往 `sys_job_conf` 表插一行（`quartz_job_class` 填包装类全限定名），由 governance `JobService.syncJobsOnStartup()` 启动时动态注册；种子示例 `docs/schema/migrations/2026-04-25-quartz-integration.sql`
- ⚠️ `MetricSchedulerService.register()` 已被运维要求关停为空实现，**不要**当范例

### EasyExcel 导出（含行数上限保护）
- 首选（异步任务 + 预检行数 + 硬上限 + OBS 上传一体）：`performance-engine-center/.../performance/service/export/impl/KpiExportStrategy.java`（countForExport 预检，超限抛 PERF-42207）
- 异步导出任务框架（状态机 PENDING→RUNNING→SUCCESS/FAILED + 策略路由）：`report-analytics-center/.../report/export/impl/RptExportServiceImpl.java`
- ⚠️ `red-engine-center/.../ReExportService.java` 的同步导出是迁移期例外（其 AGENTS.md 已标注偏离"超 5000 行必须异步"的平台 MUST 线），新功能勿模仿

### 事件发布 / 监听（Spring Event）
- 事件 + 分布式锁联动：`performance-engine-center/.../performance/listener/KpiCascadeListener.java` — `@Async` + `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` + LockManager 防重
- 跨模块事件回调：`customer-marketing-center/.../customer/listener/WorkflowCallbackListener.java` — 消费 workflow 的 ProcessCompletedEvent 后**同步**委托处理（V1.11 起弃用嵌套事件监听，避免事务提交时序 bug）

### 分布式锁 LockManager / PT_LOCK
- 接口：`common/common-web/.../common/web/lock/LockManager.java`（tryLock/unlock，CAS 防误删他人锁）；实现 `JdbcLockManager`
- 标准 try/finally 用法：`performance-engine-center/.../performance/service/DataTaskService.java` — 抢锁失败等待重查、持锁双检查、finally 释放失败降级靠 TTL

## 测试

| 类型 | 范例 | 说明 |
|---|---|---|
| Mapper 真库测试基类 | `red-engine-center/src/test/.../support/RedEngineMapperTestBase.java` | `@SpringBootTest(独立TestApp)` + `@ActiveProfiles("test")` + `@Transactional` 回滚；同构自 performance 的 `PerformanceMapperTestBase` |
| Mapper IT 用例 | `red-engine-center/src/test/.../mapper/RePartyOrgMapperIT.java` | 插入→查询→`@TableLogic` 软删→查空 三步闭环 |
| Service Mockito 单测 | `red-engine-center/src/test/.../service/ReSubmitServiceTest.java` | 关键技巧：`@BeforeAll` 里 `TableInfoHelper.initTableInfo` 预热实体元数据，使 LambdaQueryWrapper 在纯单测可用 |
| 并发测试基类 | `performance-engine-center/src/test/.../support/PerformanceConcurrentTestBase.java` | 不带 `@Transactional`，配合 TestDbCleaner 前缀隔离 |
| bootstrap 常规 IT | `bootstrap/src/test/.../it/CustomerMarketingCenterIT.java` | `@SpringBootTest` + `@ActiveProfiles("test")` + `@Sql` 夹具 |
| RBAC 鉴权链路 IT | `bootstrap/src/test/.../it/RedEngineSmokeIT.java` | `@ActiveProfiles("redengine-smoke")` 绕开 test profile 关闭 RBAC 的问题；真实 login + switch-role 验证 401/200/403 |
| 前端 vitest 组件测试 | `xanzc_frontend/src/views/perf/__tests__/Metrics.spec.js` | happy-dom + `vi.mock` API 打桩 + Element Plus 组件 stub 三件套 |
| 前端 store 单测 | `xanzc_frontend/src/stores/__tests__/menu.spec.js` | `setActivePinia` + `vi.mock` |

⚠️ 仓库内**无 Playwright/E2E 套件**（无依赖、无 e2e 目录）；历史文档提到的"Playwright 全链路"是一次性人工验证记录，不是可复用资产。

## 前端（xanzc_frontend）

- 市级支行经营全景排名布局和逐页轮播：`src/views/screen/panorama/CityPanorama.vue`、`CityBranchRanking.vue`；排名填满右栏，每5秒翻页，当前指标末页停留后才切下一指标。筛选与刷新保持有效页码，暂停、悬停、焦点、页面隐藏与卸载控制定时器；测试见相邻 `__tests__/CityBranchRanking.spec.js`。

| 类型 | 范例 | 说明 |
|---|---|---|
| HTTP 封装 | `src/api/http.js` | `call(method,url,config,fallback)`：写操作真错必 throw、仅 GET 允许 mock 兜底；统一解包 ResponseWrapper/PageResult；401 跳登录 |
| API 模块 | `src/api/redengine.js` | 全部走 `call()`；含 blob 导出与 multipart 上传两种特殊写法 |
| 列表+分页+弹窗视图 | `src/views/system/Users.vue` | el-table + el-pagination + 多 dialog + reactive pager/filters + reload() 标准模式 |
| 路由 + 登录守卫 | `src/router/index.js` | routes 注册 + `router.beforeEach` |
| 菜单按资源过滤 | `src/views/redengine/layout/canSee.js`（配套单测 `__tests__/RedEngineMenuFilter.spec.js`） | 菜单项带 `res` 资源 URL，精确 + `/**`、`/*` 前缀通配匹配；resourceUrls 未就绪时降级全显示 |
| Pinia store | `src/stores/user.js`（setup 风格）；备选 `src/stores/menu.js`（幂等 load + 索引缓存） | |

⚠️ `canSee` 前端菜单权限过滤目前只在红色引擎子系统使用；主平台 `DefaultLayout`/`AppSidebar` 不做按资源过滤，权限依赖后端 403。新功能要做前端菜单过滤时参照 `canSee.js`，而非主平台现状。

### 分行大屏不可变批次

批次手工入口、治理触发和测试都复用同一个 Service；自动模式把空日期交给 Service 选择最近
完整金融业务日，不能在 Quartz 层回退成固定日期：

```java
BranchDashboardBatchDTO result = batchService.runBatch(
        requestedDate,                 // null = 自动选择最近完整日
        "MANUAL",                      // Quartz 使用 "AUTO"
        operatorEmpId);                // AUTO 可为 null，Service 使用系统 startedBy
if (!"COMPLETE".equals(result.getStatus())) {
    throw new IllegalStateException("batch failed: " + result.getStatus());
}
```

跨模块查询只依赖公开 QueryApi，并把当前授权机构集合传入；不要读取 `PERF_RUN_TASK`、entity 或
mapper：

```java
Optional<BranchDashboardBatchDTO> snapshot = batchQueryApi.latest(
        groupCode, currentAuthorizedOrgCodes);
snapshot.ifPresent(dto -> {
    String batchId = dto.getBatchId();
    if ("PARTIAL".equals(dto.getStatus())) {
        // 展示 quality.missing；不要把被过滤机构聚合回页面
    }
});
```

成功写入由批次存储边界一次提交五个专用机构槽位、不可变 JSON 和 SUCCESS 任务；计算、输入
完整性或序列化失败由独立事务记录 FAILED。Quartz 包装类使用 `@DisallowConcurrentExecution`
且不标注 `@Component`，由治理 `JobApi`/`SYS_JOB_CONF` 注册，禁止恢复旧的
`MetricSchedulerService.register()` 路径。

### 大屏信息卡显式比较来源

- `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/presentation/ScreenDisplayComparisonDTO.java` 与 `CodeScreenPresentationValidator`：类型化 `display.comparisons`、开关语义、当前画布与不可变快照的受控历史区块引用。
- `xanzc_frontend/src/views/screen/presentation/model/explicitComparisons.js`：以数据日计算昨日/上月末/上年末，唯一行及当前值一致性校验，金额单位换算、完成率百分点；缺值不得补零。
- `xanzc_frontend/src/views/screen/panorama/usePanoramaData.js`：复用屏级授权/batchId查询历史，去重、权限与迟到响应处理，`comparisonResults`独立于增长曲线数据。
